package org.mediawiki.sparql.mwontop.http;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Test;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;

/**
 * @author MZhavzharov.
 */
public class OntologyActionTest extends JerseyTest {

    @Override
    protected Application configure() {
        return new ResourceConfig( OntologyAction.class );
    }

    @Test
    public void ontologyShouldResponseWithModel() {
        Response response = target( "ontology" ).request().get();
        assertEquals( 200, response.getStatus() );
        String s = response.readEntity( String.class );
        assertNotNull( s );
        assertFalse( s.isEmpty() );
    }

    @Test
    public void ontologyShouldResponseWithDifferentVariantsModels() throws IOException, URISyntaxException {
        Response response = target( "ontology" ).request("application/n-triples").get();
        assertEquals( 200, response.getStatus() );
        assertTrue( response.readEntity( String.class ).contains( "<http://mw2sparql.toolforge.org/ontology#Page>" ) );

        URI resource = getClass().getResource( "/ontology.ttl" ).toURI();
        String ontologyResourceContent = new String( Files.readAllBytes( Paths.get( resource ) ) );
        response = target( "ontology" ).request("text/turtle").get();
        assertEquals( 200, response.getStatus() );
        assertEquals( ontologyResourceContent, response.readEntity( String.class ));
    }
}
