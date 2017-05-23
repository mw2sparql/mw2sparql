/*
 * Copyright (c) 2017 MW2SPARQL developers.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mediawiki.sparql.mwontop.sql;

import com.google.common.collect.Sets;
import it.unibz.inf.ontop.owlrefplatform.core.QuestConstants;
import it.unibz.inf.ontop.owlrefplatform.core.QuestPreferences;
import it.unibz.inf.ontop.sesame.SesameVirtualRepo;
import it.unibz.inf.ontop.sql.*;
import org.apache.commons.codec.CharEncoding;
import org.apache.commons.io.IOUtils;
import org.mediawiki.sparql.mwontop.Configuration;
import org.mediawiki.sparql.mwontop.api.SiteInfo;
import org.mediawiki.sparql.mwontop.utils.InternalFilesManager;
import org.openrdf.model.Model;
import org.openrdf.model.impl.LinkedHashModel;
import org.openrdf.model.vocabulary.OWL;
import org.openrdf.model.vocabulary.RDF;
import org.openrdf.model.vocabulary.RDFS;
import org.openrdf.model.vocabulary.XMLSchema;
import org.openrdf.repository.Repository;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.sql.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Thomas Pellissier Tanon
 */
public class RepositoryFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryFactory.class);
    private static final RepositoryFactory INSTANCE = new RepositoryFactory();
    private static final Map<String, String> PREFIXES = new HashMap<>();
    private static final Set<String> TABLES_USED = Sets.newHashSet(
            "category", "categorylinks", "change_tag", "externallinks", "image", "imagelinks", "interwiki", "iwlinks", "langlinks", "logging", "oldimage", "page", "pagelinks", "page_props", "page_restrictions", "protected_titles", "recentchanges", "redirect", "revision", "sites", "site_identifiers", "site_stats", "tag_summary", "templatelinks", "user", "user_groups", "user_newtalk", "user_properties", "tag_summary", "valid_tag", "watchlist"
    );

    static {
        PREFIXES.put(RDF.PREFIX, RDF.NAMESPACE);
        PREFIXES.put(RDFS.PREFIX, RDFS.NAMESPACE);
        PREFIXES.put(OWL.PREFIX, OWL.NAMESPACE);
        PREFIXES.put(XMLSchema.PREFIX, XMLSchema.NAMESPACE);
        PREFIXES.put("mw", "http://tools.wmflabs.org/mw2sparql/ontology#");
    }

    private Repository repository;

    public static RepositoryFactory getInstance() {
        return INSTANCE;
    }

    public void initializeRepository() throws Exception {
        repository = buildVirtualRepository(connectionInformationForSiteId());
    }

    public Repository getRepository() {
        return repository;
    }

    private MySQLConnectionInformation connectionInformationForSiteId() {
        Configuration configuration = Configuration.getInstance();
        return new MySQLConnectionInformation(
                configuration.getDatabaseHost(),
                "enwiki_p",
                configuration.getDatabaseUser(),
                configuration.getDatabasePassword()
        );
    }

    private Repository buildVirtualRepository(MySQLConnectionInformation connectionInformation) throws Exception {
        Map<String, SiteConfig> sitesConfig = loadSitesConfig();
        setupNamespaceDatabase(sitesConfig);

        QuestPreferences preferences = new QuestPreferences();
        preferences.setCurrentValueOf(QuestPreferences.ABOX_MODE, QuestConstants.VIRTUAL);
        preferences.setCurrentValueOf(QuestPreferences.OBTAIN_FULL_METADATA, QuestConstants.FALSE);
        preferences.setCurrentValueOf(QuestPreferences.DBNAME, connectionInformation.getDatabaseName());
        preferences.setCurrentValueOf(QuestPreferences.JDBC_DRIVER, "com.mysql.jdbc.Driver");
        preferences.setCurrentValueOf(
                QuestPreferences.JDBC_URL,
                "jdbc:mysql://" + connectionInformation.getHost() + "/" + connectionInformation.getDatabaseName()
                        + "?sessionVariables=sql_mode='ANSI'"
        );
        preferences.setCurrentValueOf(QuestPreferences.DBUSER, connectionInformation.getUser());
        preferences.setCurrentValueOf(QuestPreferences.DBPASSWORD, connectionInformation.getPassword());
        String usualDBName = connectionInformation.getUser() + "__extra";


        Model rdfMapping = new LinkedHashModel();
        for (String siteId : Configuration.getInstance().getAllowedSites()) {
            rdfMapping.addAll(loadRDFMappingModelForSite(sitesConfig.get(siteId), usualDBName));
        }

        SesameVirtualRepo repository = new SesameVirtualRepo(
                connectionInformation.getDatabaseName(),
                loadOWLOntology(),
                rdfMapping,
                loadDBMetadata(connectionInformation),
                preferences
        );
        repository.setNamespaces(PREFIXES);
        repository.setImplicitDBConstraints(loadDBConstraints(usualDBName));
        repository.initialize();
        return repository;
    }

    private OWLOntology loadOWLOntology() throws Exception {
        try (InputStream inputStream = this.getClass().getResourceAsStream("/ontology.ttl")) {
            return OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(inputStream);
        }
    }

    private Model loadRDFMappingModelForSite(SiteConfig siteConfig, String usualDBName) throws Exception {
        return InternalFilesManager.parseTurtle(
                InternalFilesManager.getFileAsString("/mapping.ttl")
                        .replace("{lang}", siteConfig.getLanguageCode())
                        .replace("{db}", siteConfig.getDatabaseName() + "_p")
                        .replace("{site_id}", siteConfig.getDatabaseName())
                        .replace("{usual_db}", usualDBName)
                        .replace("{base_url}", siteConfig.getBaseURL())
        );
    }

    private ImplicitDBConstraintsReader loadDBConstraints(String usualDBName) throws IOException {
        //ImplicitDBConstraintsReader takes a file as input so we have to do a copy
        File file = File.createTempFile("db_constraints", ".tmp");
        file.deleteOnExit();
        try (
                InputStream inputStream = getClass().getResourceAsStream("/db_constraints.txt");
                OutputStream outputStream = new FileOutputStream(file)
        ) {
            String fileContent = IOUtils.toString(inputStream);
            for (String siteId : Configuration.getInstance().getAllowedSites()) {
                outputStream.write(fileContent
                        .replace("{db}", siteId + "_p")
                        .replace("{usual_db}", usualDBName)
                        .getBytes(CharEncoding.UTF_8)
                );
            }
        }
        return new ImplicitDBConstraintsReader(file);
    }

    private Map<String, SiteConfig> loadSitesConfig() throws Exception {
        LOGGER.debug("Retriving sites configuration");
        MySQLConnectionInformation connectionInformation = connectionInformationForSiteId();
        try (Connection connection = connectionInformation.createConnection()) {
            try (ResultSet resultSet = connection.createStatement().executeQuery("SELECT dbname, lang, name, url FROM meta_p.wiki;")) {
                Map<String, SiteConfig> siteConfig = new HashMap<>();
                while (resultSet.next()) {
                    siteConfig.put(resultSet.getString("dbname"), new SiteConfig(
                            resultSet.getString("dbname"),
                            resultSet.getString("lang"),
                            resultSet.getString("name"),
                            resultSet.getString("url")
                    ));
                }
                LOGGER.debug(siteConfig.size() + " sites retrived");
                return siteConfig;
            }
        }
    }

    private DBMetadata loadDBMetadata(MySQLConnectionInformation connectionInformation) throws SQLException {
        DBMetadata dbMetadata;
        try (Connection connection = connectionInformation.withDatabase("meta_p").createConnection()) {
            dbMetadata = DBMetadataExtractor.createMetadata(connection);
        }
        for (String siteId : Configuration.getInstance().getAllowedSites()) {
            addTablesToMetadata(dbMetadata, connectionInformation.withDatabase(connectionInformation.getUser() + "__extra"), Sets.newHashSet(
                    siteId + "_ns_name2id", siteId + "_ns_id2name"
            ));
            addTablesToMetadata(dbMetadata, connectionInformation.withDatabase(siteId + "_p"), TABLES_USED);
        }
        return dbMetadata;
    }

    private void addTablesToMetadata(DBMetadata dbMetadata, MySQLConnectionInformation connectionInformation, Collection<String> tables) throws SQLException {
        QuotedIDFactory qidFactory = dbMetadata.getQuotedIDFactory();
        try (Connection connection = connectionInformation.createConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            for (String table : tables) {
                DatabaseRelationDefinition currentRelation = null;
                try (ResultSet resultSet = metaData.getColumns(null, connectionInformation.getDatabaseName(), table, null)) {
                    while (resultSet.next()) {
                        RelationID relationId = RelationID.createRelationIdFromDatabaseRecord(
                                qidFactory,
                                connectionInformation.getDatabaseName(),
                                resultSet.getString("TABLE_NAME")
                        );
                        QuotedID attributeId = QuotedID.createIdFromDatabaseRecord(
                                qidFactory,
                                resultSet.getString("COLUMN_NAME")
                        );
                        if (currentRelation == null || !currentRelation.getID().equals(relationId)) {
                            currentRelation = dbMetadata.createDatabaseRelation(relationId);
                        }
                        currentRelation.addAttribute(
                                attributeId,
                                resultSet.getInt("DATA_TYPE"),
                                resultSet.getString("TYPE_NAME"),
                                resultSet.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls
                        );
                    }
                }
            }
        }
    }

    private void setupNamespaceDatabase(Map<String, SiteConfig> sitesConfig) throws Exception {
        MySQLConnectionInformation connectionInformation = connectionInformationForSiteId();
        LOGGER.info("Starting namespace database creation");
        try (Connection connection = connectionInformation.createConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE IF NOT EXISTS " + connectionInformation.getUser() + "__extra");
            }
            for (String siteId : Configuration.getInstance().getAllowedSites()) {
                SiteInfo siteInfo = SiteInfo.loadSiteInfo(sitesConfig.get(siteId).getBaseURL());
                LOGGER.info("Filling namespace database for " + siteId);
                String tablesPrefix = connectionInformation.getUser() + "__extra." + siteInfo.getWikiId() + "_";
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE TABLE IF NOT EXISTS " + tablesPrefix + "ns_id2name (" +
                            "ns_id INTEGER NOT NULL, " +
                            "ns_name VARCHAR(100) NOT NULL, " +
                            "PRIMARY KEY(ns_id)" +
                            ")");
                    statement.execute("CREATE TABLE IF NOT EXISTS " + tablesPrefix + "ns_name2id (" +
                            "ns_name VARCHAR(100) NOT NULL, " +
                            "ns_id INTEGER NOT NULL, " +
                            "PRIMARY KEY(ns_name)" +
                            ")");
                }
                try (PreparedStatement preparedStatement = connection.prepareStatement(
                        "REPLACE INTO " + tablesPrefix + "ns_id2name VALUES (?, ?)"
                )) {
                    for (Map.Entry<Integer, String> namespace : siteInfo.getNamespaceNames().entrySet()) {
                        preparedStatement.setInt(1, namespace.getKey());
                        if (namespace.getValue().equals("")) {
                            preparedStatement.setString(2, "");
                        } else {
                            preparedStatement.setString(2, namespace.getValue());
                        }
                        preparedStatement.execute();
                    }
                }
                try (PreparedStatement preparedStatement = connection.prepareStatement(
                        "REPLACE INTO " + tablesPrefix + "ns_name2id VALUES (?, ?)"
                )) {
                    for (Map.Entry<String, Integer> namespace : siteInfo.getAllNamespaceNames().entrySet()) {
                        if (namespace.getKey().equals("")) {
                            preparedStatement.setString(1, "");
                        } else {
                            preparedStatement.setString(1, namespace.getKey());
                        }
                        preparedStatement.setInt(2, namespace.getValue());
                        preparedStatement.execute();
                    }
                }
            }
        }
        LOGGER.info("End of namespace database creation");
    }

    private static class MySQLConnectionInformation {
        private String host;
        private String dbName;
        private String user;
        private String password;

        MySQLConnectionInformation(String host, String dbName, String user, String password) {
            this.host = host;
            this.dbName = dbName;
            this.user = user;
            this.password = password;
        }

        String getHost() {
            return host;
        }

        String getDatabaseName() {
            return dbName;
        }

        String getUser() {
            return user;
        }

        String getPassword() {
            return password;
        }

        Connection createConnection() throws SQLException {
            return DriverManager.getConnection("jdbc:mysql://" + getHost() + "/" + getDatabaseName(), getUser(), getPassword());
        }

        MySQLConnectionInformation withDatabase(String dbName) {
            return new MySQLConnectionInformation(host, dbName, user, password);
        }
    }

    private static class SiteConfig {
        private String dbName;
        private String lang;
        private String name;
        private String url;

        SiteConfig(String dbName, String lang, String name, String url) {
            this.dbName = dbName;
            this.lang = lang;
            this.name = name;
            this.url = url;
        }

        String getDatabaseName() {
            return dbName;
        }

        String getLanguageCode() {
            return lang;
        }

        String getSiteName() {
            return name;
        }

        String getBaseURL() {
            return url;
        }
    }
}
