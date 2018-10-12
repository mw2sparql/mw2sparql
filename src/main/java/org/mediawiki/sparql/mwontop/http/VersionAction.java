package org.mediawiki.sparql.mwontop.http;

import org.mediawiki.sparql.mwontop.Configuration;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

/**
 * Simple service to return build version.
 *
 * @author MZhavzharov.
 */
@Path( "/" )
public class VersionAction {

    @GET
    public Response get( @Context Request request ) {
        String buildDate = Configuration.getInstance().getProperty( "build.date" );
        String appVersion = Configuration.getInstance().getProperty( "application.version" );

        return Response.ok( "Current version: " + appVersion + " from " + buildDate ).build();
    }
}
