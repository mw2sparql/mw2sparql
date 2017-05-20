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


import org.mediawiki.sparql.mwontop.sql.RepositoryFactory;
import org.openrdf.query.*;
import org.openrdf.query.resultio.BooleanQueryResultWriterFactory;
import org.openrdf.query.resultio.BooleanQueryResultWriterRegistry;
import org.openrdf.query.resultio.TupleQueryResultWriterFactory;
import org.openrdf.query.resultio.TupleQueryResultWriterRegistry;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFWriterFactory;
import org.openrdf.rio.RDFWriterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

/**
 * @author Thomas Pellissier Tanon
 */
@Path("/sparql")
public class SPARQLActions {

    private static final int QUERY_TIMOUT_IN_S = 30;
    private static final Logger LOGGER = LoggerFactory.getLogger(SPARQLActions.class);
    private static Repository REPOSITORY = RepositoryFactory.getInstance().getRepository();

    @GET
    public Response get(@QueryParam("query") String query, @Context Request request) {
        return executeQuery(query, null, request);
    }

    @POST
    @Consumes({"application/x-www-form-urlencoded", "multipart/form-data"})
    public Response postForm(@FormParam("query") String query, @Context Request request) {
        return executeQuery(query, null, request);
    }

    @POST
    @Consumes("application/sparql-query")
    public Response postDirect(String query, @Context Request request) {
        return executeQuery(query, null, request);
    }

    private Response executeQuery(String queryString, String baseIRI, Request request) {
        try {
            RepositoryConnection repositoryConnection = REPOSITORY.getConnection();
            try {
                Query query = repositoryConnection.prepareQuery(QueryLanguage.SPARQL, queryString);
                query.setMaxQueryTime(QUERY_TIMOUT_IN_S);
                if (query instanceof BooleanQuery) {
                    return evaluateBooleanQuery((BooleanQuery) query, request);
                } else if (query instanceof GraphQuery) {
                    return evaluateGraphQuery((GraphQuery) query, request);
                } else if (query instanceof TupleQuery) {
                    return evaluateTupleQuery((TupleQuery) query, request);
                } else {
                    throw new BadRequestException("Unsupported kind of query: " + queryString);
                }
            } finally {
                repositoryConnection.close();
            }
        } catch (MalformedQueryException e) {
            LOGGER.info(e.getMessage(), e);
            throw new BadRequestException(e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    private Response evaluateBooleanQuery(BooleanQuery query, Request request) {
        SesameContentNegotiation.FormatService<BooleanQueryResultWriterFactory> format =
                SesameContentNegotiation.getServiceForFormat(BooleanQueryResultWriterRegistry.getInstance(), request);
        return Response.ok(
                (StreamingOutput) outputStream -> {
                    try {
                        format.getService().getWriter(outputStream).handleBoolean(query.evaluate());
                    } catch (QueryResultHandlerException | QueryEvaluationException e) {
                        LOGGER.warn(e.getMessage(), e);
                        throw new InternalServerErrorException(e.getMessage(), e);
                    }
                },
                SesameContentNegotiation.variantForFormat(format.getFormat())
        ).build();
    }

    private Response evaluateGraphQuery(GraphQuery query, Request request) {
        SesameContentNegotiation.FormatService<RDFWriterFactory> format =
                SesameContentNegotiation.getServiceForFormat(RDFWriterRegistry.getInstance(), request);
        return Response.ok(
                (StreamingOutput) outputStream -> {
                    try {
                        query.evaluate(format.getService().getWriter(outputStream));
                    } catch (RDFHandlerException | QueryEvaluationException e) {
                        LOGGER.warn(e.getMessage(), e);
                        throw new InternalServerErrorException(e.getMessage(), e);
                    }
                },
                SesameContentNegotiation.variantForFormat(format.getFormat())
        ).build();
    }

    private Response evaluateTupleQuery(TupleQuery query, Request request) {
        SesameContentNegotiation.FormatService<TupleQueryResultWriterFactory> format =
                SesameContentNegotiation.getServiceForFormat(TupleQueryResultWriterRegistry.getInstance(), request);
        return Response.ok(
                (StreamingOutput) outputStream -> {
                    try {
                        query.evaluate(format.getService().getWriter(outputStream));
                    } catch (TupleQueryResultHandlerException | QueryEvaluationException e) {
                        LOGGER.warn(e.getMessage(), e);
                        throw new InternalServerErrorException(e.getMessage(), e);
                    }
                },
                SesameContentNegotiation.variantForFormat(format.getFormat())
        ).build();
    }
}
