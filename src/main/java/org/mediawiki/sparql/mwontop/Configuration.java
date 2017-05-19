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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

/**
 * @author Thomas Pellissier Tanon
 */
public class Configuration {

    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);
    private static final Configuration INSTANCE = loadConfiguration();

    public static Configuration getInstance() {
        return INSTANCE;
    }

    private static Configuration loadConfiguration() {
        try {
            File basePath = new File(Configuration.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
            try(InputStream inputStream = new FileInputStream(new File(basePath,"config.properties"))) {
                Properties properties = new Properties();
                properties.load(inputStream);
                return new Configuration(properties);
            }
        } catch (IOException | URISyntaxException e) {
            LOGGER.error(e.getMessage(), e);
            System.exit(1);
            return null;
        }
    }

    private Properties properties;

    private Configuration(Properties properties) {
        this.properties = properties;
    }

    public URI getBaseURI() {
        try {
            return new URI(properties.getProperty("app.http.baseURI"));
        } catch (URISyntaxException e) {
            LOGGER.error("You should define an app.http.baseURI in the config.properties file", e);
            System.exit(1);
            return null;
        }
    }

    public String getDatabaseHostPattern() {
        return properties.getProperty("app.db.hostPattern");
    }

    public String getDatabaseUser() {
        return properties.getProperty("app.db.user");
    }

    public String getDatabasePassword() {
        return properties.getProperty("app.db.password");
    }
}
