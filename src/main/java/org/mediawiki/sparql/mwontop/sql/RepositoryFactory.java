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
import it.unibz.inf.ontop.dbschema.*;
import it.unibz.inf.ontop.injection.OntopSQLOWLAPIConfiguration;
import it.unibz.inf.ontop.injection.OntopSystemConfiguration;
import it.unibz.inf.ontop.rdf4j.repository.OntopRepository;
import org.apache.commons.rdf.rdf4j.RDF4J;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.repository.Repository;
import org.mediawiki.sparql.mwontop.Configuration;
import org.mediawiki.sparql.mwontop.utils.InternalFilesManager;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Thomas Pellissier Tanon
 */
public class RepositoryFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryFactory.class);
    private static final RepositoryFactory INSTANCE = new RepositoryFactory();
    private static final Set<String> TABLES_USED = Sets.newHashSet(
            "page", "revision", "redirect", "pagelinks", "templatelinks", "categorylinks"
    );

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
                "meta_p",
                configuration.getDatabaseUser(),
                configuration.getDatabasePassword()
        );
    }

    private Repository buildVirtualRepository(MySQLConnectionInformation connectionInformation) throws Exception {
        Map<String, SiteConfig> sitesConfig = loadSitesConfig();

        Model rdfMapping = new LinkedHashModel();
        for (String siteId : Configuration.getInstance().getAllowedSites()) {
            rdfMapping.addAll(loadRDFMappingModelForSite(sitesConfig.get(siteId)));
        }

        OntopSystemConfiguration configuration = OntopSQLOWLAPIConfiguration.defaultBuilder()
                .dbMetadata(loadDBMetadata(connectionInformation))
                .enableIRISafeEncoding(false)
                .jdbcDriver("com.mysql.jdbc.Driver")
                .jdbcUrl("jdbc:mysql://" + connectionInformation.getHost() + "/" +
                        connectionInformation.getDatabaseName() + "?characterEncoding=UTF-8&sessionVariables=sql_mode='ANSI'")
                .jdbcName(connectionInformation.getDatabaseName())
                .jdbcUser(connectionInformation.getUser())
                .jdbcPassword(connectionInformation.getPassword())
                .ontology(loadOWLOntology())
                .r2rmlMappingGraph((new RDF4J()).asGraph(rdfMapping))
                .build();

        OntopRepository repository = OntopRepository.defaultRepository(configuration);
        repository.initialize();
        return repository;
    }

    private OWLOntology loadOWLOntology() throws Exception {
        try (InputStream inputStream = this.getClass().getResourceAsStream("/ontology.ttl")) {
            return OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(inputStream);
        }
    }

    private Model loadRDFMappingModelForSite(SiteConfig siteConfig) throws Exception {
        return InternalFilesManager.parseTurtle(
                InternalFilesManager.getFileAsString("/mapping.ttl")
                        .replace("{lang}", siteConfig.getLanguageCode())
                        .replace("{db}", siteConfig.getDatabaseName() + "_p")
                        .replace("{site_id}", siteConfig.getDatabaseName())
                        .replace("{base_url}", siteConfig.getBaseURL())
        );
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
                            resultSet.getString("url")
                    ));
                }
                LOGGER.debug(siteConfig.size() + " sites retrived");
                return siteConfig;
            }
        }
    }

    private DBMetadata loadDBMetadata(MySQLConnectionInformation connectionInformation) throws SQLException {
        RDBMetadata dbMetadata;
        try (Connection connection = connectionInformation.withDatabase("meta_p").createConnection()) {
            dbMetadata = RDBMetadataExtractionTools.createMetadata(connection);
        }
        for (String siteId : Configuration.getInstance().getAllowedSites()) {
            addTablesToMetadata(dbMetadata, connectionInformation.withDatabase(siteId + "_p"));
        }
        return dbMetadata;
    }

    private void addTablesToMetadata(RDBMetadata dbMetadata, MySQLConnectionInformation connectionInformation) throws SQLException {
        QuotedIDFactory qidFactory = dbMetadata.getQuotedIDFactory();
        try (Connection connection = connectionInformation.createConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            for (String table : RepositoryFactory.TABLES_USED) {
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
        private String url;

        SiteConfig(String dbName, String lang, String url) {
            this.dbName = dbName;
            this.lang = lang;
            this.url = url;
        }

        String getDatabaseName() {
            return dbName;
        }

        String getLanguageCode() {
            return lang;
        }

        String getBaseURL() {
            return url;
        }
    }
}
