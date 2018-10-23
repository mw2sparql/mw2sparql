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

package org.mediawiki.sparql.mwontop;

import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.UriBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Thomas Pellissier Tanon
 */
public class Configuration {
    @NonNull
    private static final Logger LOGGER = LoggerFactory.getLogger( Configuration.class );

    @NonNull
    private static final Configuration INSTANCE = loadConfiguration();

    public static final String APP_DB_FILTERED_WIKI_FAMILIES_KEY = "app.db.filtered.wiki.families";
    public static final String APP_DB_FILTERED_WIKI_DB_NAMES_KEY = "app.db.filtered.wiki.db.names";
    public static final String APP_DB_HOST_KEY = "app.db.host";
    public static final String APP_DB_USER_KEY = "app.db.user";
    public static final String APP_DB_PASSWORD_KEY = "app.db.password";
    public static final String APP_HTTP_BASE_URI_KEY = "app.http.baseURI";

    /**
     * Default wiki families to skip while processing site configs.
     *
     * @see org.mediawiki.sparql.mwontop.sql.RepositoryFactory#initializeRepository
     */
    public static final String APP_DB_FILTERED_WIKI_FAMILIES_DEFAULT = "wikimania, special, wikidata";
    /**
     * Default wiki db names to skip while processing site config.
     *
     * @see org.mediawiki.sparql.mwontop.sql.RepositoryFactory#initializeRepository
     */
    public static final String APP_DB_FILTERED_WIKI_DB_NAMES_DEFAULT = "commonswiki, specieswiki";

    @NonNull
    private Properties properties;

    private Configuration( @NonNull Properties properties ) {
        this.properties = properties;
    }

    @NonNull
    public static Configuration instance() {
        return INSTANCE;
    }

    @NonNull
    private static Properties loadDefaultProperties() {
        Properties properties = new Properties( System.getProperties() );

        //init default properties
        properties.setProperty( APP_DB_FILTERED_WIKI_FAMILIES_KEY, APP_DB_FILTERED_WIKI_FAMILIES_DEFAULT );
        properties.setProperty( APP_DB_FILTERED_WIKI_DB_NAMES_KEY, APP_DB_FILTERED_WIKI_DB_NAMES_DEFAULT );

        try ( InputStream input = Configuration.class.getClassLoader().getResourceAsStream( "application.properties" ) ) {
            properties.load( input );
        } catch ( IOException e ) {
            LOGGER.error( e.getMessage() );
        }
        return properties;
    }

    @NonNull
    private static Configuration loadConfiguration() {
        Properties properties = loadDefaultProperties();
        try {
            URL location = Configuration.class.getProtectionDomain().getCodeSource().getLocation();
            File basePath = new File( location.toURI() ).getParentFile();
            try ( InputStream inputStream = new FileInputStream( new File( basePath, "config.properties" ) ) ) {
                properties.load( inputStream );
                return new Configuration( properties );
            }
        } catch ( IOException | URISyntaxException e ) {
            LOGGER.error( e.getMessage() + ".\nDefault properties will be used!" );
            //creating default properties with environment variables
            return new Configuration( properties );
        }
    }

    @NonNull
    public URI getBaseURI() {
        String baseUri = getProperty( APP_HTTP_BASE_URI_KEY );
        if ( baseUri == null ) {
            throw new IllegalStateException( "'" + APP_HTTP_BASE_URI_KEY + "' property should be defined." );
        }
        UriBuilder uriBuilder = UriBuilder.fromUri( baseUri );
        String port = System.getenv( "PORT" );
        if ( port != null ) {
            uriBuilder.port( Integer.parseInt( port ) );
        }
        return uriBuilder.build();
    }

    @Nullable
    public String getDatabaseHost() {
        return getProperty( APP_DB_HOST_KEY );
    }

    @Nullable
    public String getDatabaseUser() {
        return getProperty( APP_DB_USER_KEY );
    }

    @Nullable
    public String getDatabasePassword() {
        return getProperty( APP_DB_PASSWORD_KEY );
    }

    @NonNull
    public List<String> getFilteredWikiFamilies() {
        return getPropertyAsList( APP_DB_FILTERED_WIKI_FAMILIES_KEY );
    }

    @NonNull
    public List<String> getFilteredWikiDBNames() {
        return getPropertyAsList( APP_DB_FILTERED_WIKI_DB_NAMES_KEY );
    }

    @Nullable
    public String getProperty( @NonNull String key ) {
        return properties.getProperty( key );
    }

    /**
     * Receive a property and split it by coma sign.
     * Result is wrapped by List. Values are trimmed and empty ones are skipped.
     *
     * @return unmodifiable list.
     */
    @NonNull
    public List<String> getPropertyAsList( @NonNull String key ) {
        String property = getProperty( key );

        if ( property == null ) {
            return Collections.emptyList();
        }

        String[] splitResult = property.split( "," );
        return Stream.of( splitResult ).filter( StringUtils::isNotBlank ).map( String::trim ).collect( Collectors.toList() );
    }
}
