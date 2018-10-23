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

import it.unibz.inf.ontop.dbschema.*;
import it.unibz.inf.ontop.injection.OntopSQLOWLAPIConfiguration;
import it.unibz.inf.ontop.injection.OntopSystemConfiguration;
import it.unibz.inf.ontop.rdf4j.repository.OntopRepository;
import org.apache.commons.rdf.rdf4j.RDF4J;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.repository.Repository;
import org.mediawiki.sparql.mwontop.Configuration;
import org.mediawiki.sparql.mwontop.utils.InternalFilesManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * @author Thomas Pellissier Tanon
 */
public final class RepositoryFactory {
    @NonNull
    private static final Logger LOGGER = LoggerFactory.getLogger( RepositoryFactory.class );
    @NonNull
    private static final RepositoryFactory INSTANCE = new RepositoryFactory();


    private Repository repository;

    public static RepositoryFactory getInstance() {
        return INSTANCE;
    }

    private RepositoryFactory() {
        //simple instance creation protection
    }

    public void initializeRepository() throws Exception {
        repository = buildVirtualRepository( connectionInformationForSiteId() );
    }

    public Repository getRepository() {
        return repository;
    }

    @NonNull
    private MySQLConnectionInformation connectionInformationForSiteId() {
        Configuration configuration = Configuration.instance();
        return new MySQLConnectionInformation(
                configuration.getDatabaseHost(),
                "meta_p",
                configuration.getDatabaseUser(),
                configuration.getDatabasePassword()
        );
    }

    @NonNull
    private Repository buildVirtualRepository( @NonNull MySQLConnectionInformation connectionInformation ) throws Exception {
        List<SiteConfig> sitesConfig = loadSitesConfig( connectionInformation );

        Model rdfMapping = new LinkedHashModel();
        String mappingTemplate = InternalFilesManager.getFileAsString( "/mapping.ttl" );
        for ( SiteConfig c : sitesConfig ) {
            rdfMapping.addAll( loadRDFMappingModelForSite( c, mappingTemplate ) );
        }

        Properties prop = new Properties();
        prop.put( "ontop.completeProvidedMetadata", "false" );
        prop.put( "it.unibz.inf.ontop.answering.reformulation.unfolding.QueryUnfolder", "org.mediawiki.sparql.mwontop.utils.SiteSpecificUnfolder" );

        OntopSystemConfiguration configuration = OntopSQLOWLAPIConfiguration.defaultBuilder()
                .dbMetadata( loadDBMetadata( connectionInformation, sitesConfig ) )
                .enableIRISafeEncoding( false )
                .jdbcDriver( "com.mysql.jdbc.Driver" )
                .jdbcUrl( "jdbc:mysql://" + connectionInformation.getHost() + "/" +
                        connectionInformation.getDatabaseName() + "?characterEncoding=UTF-8&sessionVariables=sql_mode='ANSI'" )
                .jdbcName( connectionInformation.getDatabaseName() )
                .jdbcUser( connectionInformation.getUser() )
                .jdbcPassword( connectionInformation.getPassword() )
                .properties( prop )
                .r2rmlMappingGraph( ( new RDF4J() ).asGraph( rdfMapping ) )
                .build();

        OntopRepository repository = OntopRepository.defaultRepository( configuration );
        repository.initialize();
        return repository;
    }

    private Model loadRDFMappingModelForSite( @NonNull SiteConfig siteConfig, @NonNull String mappingTemplate ) throws IOException {
        return InternalFilesManager.parseTurtle(
                mappingTemplate
                        .replace( "{lang}", siteConfig.getLanguageCode() )
                        .replace( "{db}", siteConfig.getDatabaseName() + "_p" )
                        .replace( "{site_id}", siteConfig.getDatabaseName() )
                        .replace( "{base_url}", siteConfig.getBaseURL() )
        );
    }

    @NonNull
    private List<SiteConfig> loadSitesConfig( @NonNull MySQLConnectionInformation connectionInformation ) throws SQLException {
        LOGGER.debug( "Retriving sites configuration" );
        try ( Connection connection = connectionInformation.createConnection();
              PreparedStatement statement = prepareSiteConfigStatement( connection );
              ResultSet resultSet = statement.executeQuery() ) {
            List<SiteConfig> siteConfig = new ArrayList<>();
            while ( resultSet.next() ) {
                siteConfig.add( new SiteConfig(
                        resultSet.getString( "dbname" ),
                        resultSet.getString( "lang" ),
                        resultSet.getString( "url" )
                ) );
            }
            LOGGER.debug( siteConfig.size() + " sites retrived" );
            return siteConfig;
        }
    }

    @NonNull
    private PreparedStatement prepareSiteConfigStatement( @NonNull Connection connection ) throws SQLException {

        Configuration conf = Configuration.instance();
        List<String> filteredFamilies = conf.getFilteredWikiFamilies();
        List<String> filteredDBNames = conf.getFilteredWikiDBNames();

        String familiesIndexes = String.join( ",", Collections.nCopies( filteredFamilies.size(), "?" ) );
        String dbNamesIndexes = String.join( ",", Collections.nCopies( filteredDBNames.size(), "?" ) );

        String sql = "SELECT * FROM wiki WHERE NOT family IN (" + familiesIndexes + ") OR dbname IN ( " + dbNamesIndexes + ")";
        PreparedStatement ps = connection.prepareStatement( sql );
        int parameterIndex = 1;
        for ( String filteredFamily : filteredFamilies ) {
            ps.setString( parameterIndex++, filteredFamily );
        }
        for ( String filteredDBName : filteredDBNames ) {
            ps.setString( parameterIndex++, filteredDBName );
        }
        return ps;
    }

    @NonNull
    private DBMetadata loadDBMetadata( @NonNull MySQLConnectionInformation connectionInformation, @NonNull List<SiteConfig> sitesConfig ) throws SQLException {
        RDBMetadata dbMetadata;
        try ( Connection connection = connectionInformation.withDatabase().createConnection() ) {
            dbMetadata = RDBMetadataExtractionTools.createMetadata( connection );
        }
        QuotedIDFactory qidFactory = dbMetadata.getQuotedIDFactory();
        QuotedID rd_namespace = QuotedID.createIdFromDatabaseRecord( qidFactory, "rd_namespace" );
        QuotedID rd_title = QuotedID.createIdFromDatabaseRecord( qidFactory, "rd_title" );
        QuotedID rd_from = QuotedID.createIdFromDatabaseRecord( qidFactory, "rd_from" );
        QuotedID tl_namespace = QuotedID.createIdFromDatabaseRecord( qidFactory, "tl_namespace" );
        QuotedID tl_title = QuotedID.createIdFromDatabaseRecord( qidFactory, "tl_title" );
        QuotedID tl_from = QuotedID.createIdFromDatabaseRecord( qidFactory, "tl_from" );
        QuotedID page_id = QuotedID.createIdFromDatabaseRecord( qidFactory, "page_id" );
        QuotedID page_namespace = QuotedID.createIdFromDatabaseRecord( qidFactory, "page_namespace" );
        QuotedID page_title = QuotedID.createIdFromDatabaseRecord( qidFactory, "page_title" );
        QuotedID pl_namespace = QuotedID.createIdFromDatabaseRecord( qidFactory, "pl_namespace" );
        QuotedID pl_from = QuotedID.createIdFromDatabaseRecord( qidFactory, "pl_from" );
        QuotedID pl_title = QuotedID.createIdFromDatabaseRecord( qidFactory, "pl_title" );
        QuotedID cl_to = QuotedID.createIdFromDatabaseRecord( qidFactory, "cl_to" );
        QuotedID cl_from = QuotedID.createIdFromDatabaseRecord( qidFactory, "cl_from" );

        for ( SiteConfig siteConfig : sitesConfig ) {
            String dbName = siteConfig.dbName;
            DatabaseRelationDefinition page = dbMetadata.createDatabaseRelation( RelationID.createRelationIdFromDatabaseRecord( qidFactory, dbName + "_p", "page" ) );
            page.addAttribute( page_id, 4, "INT UNSIGNED", false );
            page.addAttribute( page_namespace, 4, "INT", false );
            page.addAttribute( page_title, -3, "VARBINARY", false );
            page.addUniqueConstraint( UniqueConstraint.primaryKeyOf( page.getAttribute( page_id ) ) );
            page.addUniqueConstraint( UniqueConstraint.builder( page )
                    .add( page.getAttribute( page_namespace ) )
                    .add( page.getAttribute( page_title ) )
                    .build( "unique", false ) );

            DatabaseRelationDefinition templatelinks = dbMetadata.createDatabaseRelation( RelationID.createRelationIdFromDatabaseRecord( qidFactory, dbName + "_p",
                    "templatelinks" ) );
            templatelinks.addAttribute( tl_namespace, 4, "INT", false );
            templatelinks.addAttribute( tl_title, -3, "VARBINARY", false );
            templatelinks.addAttribute( tl_from, -3, "VARBINARY", false );
            templatelinks.addForeignKeyConstraint( ForeignKeyConstraint.of( dbName + "-fk1", page.getAttribute( page_id ), templatelinks.getAttribute( tl_from ) ) );

            DatabaseRelationDefinition categorylinks = dbMetadata.createDatabaseRelation( RelationID.createRelationIdFromDatabaseRecord( qidFactory, dbName + "_p",
                    "categorylinks" ) );
            categorylinks.addAttribute( cl_to, -3, "VARBINARY", false );
            categorylinks.addAttribute( cl_from, 4, "INT UNSIGNED", false );
            categorylinks.addForeignKeyConstraint( ForeignKeyConstraint.of( dbName + "-fk2", page.getAttribute( page_id ), categorylinks.getAttribute( cl_from ) ) );

            DatabaseRelationDefinition pagelinks = dbMetadata.createDatabaseRelation( RelationID.createRelationIdFromDatabaseRecord( qidFactory, dbName + "_p", "pagelinks" ) );
            pagelinks.addAttribute( pl_namespace, 4, "INT", false );
            pagelinks.addAttribute( pl_from, 4, "INT UNSIGNED", false );
            pagelinks.addAttribute( pl_title, -3, "VARBINARY", false );
            pagelinks.addForeignKeyConstraint( ForeignKeyConstraint.of( dbName + "-fk3", page.getAttribute( page_id ), pagelinks.getAttribute( pl_from ) ) );

            DatabaseRelationDefinition redirect = dbMetadata.createDatabaseRelation( RelationID.createRelationIdFromDatabaseRecord( qidFactory, dbName + "_p", "redirect" ) );
            redirect.addAttribute( rd_namespace, 4, "INT", false );
            redirect.addAttribute( rd_title, -3, "VARBINARY", false );
            redirect.addAttribute( rd_from, 4, "INT UNSIGNED", false );
            redirect.addForeignKeyConstraint( ForeignKeyConstraint.of( dbName + "-fk4", page.getAttribute( page_id ), redirect.getAttribute( rd_from ) ) );
        }

        return dbMetadata;
    }

    private static class MySQLConnectionInformation {
        private String host;
        private String dbName;
        private String user;
        private String password;

        MySQLConnectionInformation( String host, String dbName, String user, String password ) {
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
            return DriverManager.getConnection( "jdbc:mysql://" + getHost() + "/" + getDatabaseName(), getUser(), getPassword() );
        }

        MySQLConnectionInformation withDatabase() {
            return new MySQLConnectionInformation( host, "enwiki_p", user, password );
        }
    }

    private static class SiteConfig {
        private String dbName;
        private String lang;
        private String url;

        SiteConfig( String dbName, String lang, String url ) {
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
