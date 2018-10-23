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

package org.mediawiki.sparql.mwontop.http;

import com.sun.net.httpserver.HttpServer;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.message.DeflateEncoder;
import org.glassfish.jersey.message.GZipEncoder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.filter.EncodingFilter;
import org.mediawiki.sparql.mwontop.Configuration;
import org.mediawiki.sparql.mwontop.sql.RepositoryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * @author Thomas Pellissier Tanon
 */
public class Main extends ResourceConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private Main() {
        packages("org.mediawiki.sparql.mwontop.http");

        register(CORSFilter.class);
        EncodingFilter.enableFor(this, GZipEncoder.class);
        EncodingFilter.enableFor(this, DeflateEncoder.class);
    }

    public static void main(String[] args) {
        try {
            RepositoryFactory.getInstance().initializeRepository();
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
        HttpServer server = startServer();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
    }

    private static HttpServer startServer() {
        URI baseURI = Configuration.instance().getBaseURI();
        LOGGER.info("Starting server at: " + baseURI.toString());
        return JdkHttpServerFactory.createHttpServer(baseURI, new Main());
    }
}
