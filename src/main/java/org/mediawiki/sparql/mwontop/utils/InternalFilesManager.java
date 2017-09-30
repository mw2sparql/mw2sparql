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

package org.mediawiki.sparql.mwontop.utils;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.CharEncoding;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.rio.*;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Thomas Pellissier Tanon
 */
public class InternalFilesManager {

    public static String getFileAsString(String fileName) throws IOException {
        try(InputStream inputStream = InternalFilesManager.class.getResourceAsStream(fileName)) {
            return IOUtils.toString(inputStream);
        }
    }

    public static Model parseTurtleFile(String fileName) throws IOException, RDFParseException, RDFHandlerException {
        try(InputStream inputStream = InternalFilesManager.class.getResourceAsStream(fileName)) {
            return parseTurtleFile(inputStream);
        }
    }

    private static Model parseTurtleFile(InputStream inputStream) throws IOException, RDFParseException, RDFHandlerException {
        Model model = new LinkedHashModel();
        StatementCollector collector = new StatementCollector(model);

        RDFParser rdfParser = Rio.createParser(RDFFormat.TURTLE);
        rdfParser.setRDFHandler(collector);
        rdfParser.parse(inputStream, "");

        return model;
    }

    public static Model parseTurtle(String content) throws IOException, RDFParseException, RDFHandlerException {
        try (InputStream inputStream = new ByteArrayInputStream(content.getBytes(CharEncoding.UTF_8))) {
            return parseTurtleFile(inputStream);
        }
    }
}
