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


import org.mediawiki.sparql.mwontop.utils.InternalFilesManager;
import org.openrdf.rio.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

/**
 * @author Thomas Pellissier Tanon
 */
@Path("/ontology")
public class OntologyAction {

    private static final Logger LOGGER = LoggerFactory.getLogger(SPARQLActions.class);

    @GET
    public Response get(@Context Request request) {
        SesameContentNegotiation.FormatService<RDFWriterFactory> format =
                SesameContentNegotiation.getServiceForFormat(RDFWriterRegistry.getInstance(), request);
        return Response.ok(
                (StreamingOutput) outputStream -> {
                    try {
                        Rio.write(InternalFilesManager.parseTurtleFile("/ontology.ttl"), format.getService().getWriter(outputStream));
                    } catch (RDFParseException | RDFHandlerException e) {
                        LOGGER.warn(e.getMessage(), e);
                        throw new InternalServerErrorException(e);
                    }
                },
                SesameContentNegotiation.variantForFormat(format.getFormat())
        ).build();
    }
}
