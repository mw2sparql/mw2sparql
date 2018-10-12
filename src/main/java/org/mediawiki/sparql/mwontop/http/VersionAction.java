package org.mediawiki.sparql.mwontop.http;

import org.apache.commons.lang3.StringUtils;
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
        String gitCommit = Configuration.getInstance().getProperty( "git.commit" );

        //short git commit version
        if ( StringUtils.isNotBlank(gitCommit) && gitCommit.length() > 7 && !gitCommit.contains( "$" )) {
            gitCommit = gitCommit.substring( 0, 7 );
        }

        return Response.ok( "Current version: " + appVersion + "( " + gitCommit + " ) from " + buildDate ).build();
    }
}
