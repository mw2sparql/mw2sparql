package org.mediawiki.sparql.mwontop.http;

import org.glassfish.jersey.server.ResourceConfig;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mediawiki.sparql.mwontop.sql.RepositoryFactory;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author MZhavzharov.
 */
public class SPARQLActionsTest extends SparqlBaseTest {

    private final String baseQueryPartForGet = "PREFIX rdf: %3Chttp://www.w3.org/1999/02/22-rdf-syntax-ns%23%3E PREFIX schema: %3Chttp://schema.org/%3E PREFIX mw: " +
            "%3Chttp://tools.wmflabs.org/mw2sparql/ontology%23%3E ";
    private final String baseQueryPartForPost = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> PREFIX schema: <http://schema.org/> PREFIX mw: <http://tools.wmflabs" +
            ".org/mw2sparql/ontology#> ";

    @Override
    protected Application configure() {
        return new ResourceConfig( SPARQLActions.class );
    }

    @BeforeClass
    public static void init() throws Exception {
        RepositoryFactory.getInstance().initializeRepository();
    }

    @Test
    public void shouldReturnBadRequestWithoutQuery() {
        Response response = target( "/sparql" ).request().get();
        assertEquals( 400, response.getStatus() );
    }

    @Test()
    public void shouldResponseWithTrueOnAskQuery() {
        //ASK for fr-category with url-encoded umlauts:
        String query = "ASK %7B%3Chttps://fr.wikipedia.org/wiki/Cat%C3%A9gorie:Langage_de_requ%C3%AAte%3E ?predicate ?object%7D";
        Response response = target( "/sparql" ).queryParam( "query", baseQueryPartForGet + query ).request().get();
        assertEquals( 200, response.getStatus() );
        String result = response.readEntity( String.class );
        assertTrue( result.contains( ">true<" ) );
    }

    @Test
    public void shouldResponseWithLinkAndCategoryRu() {
        //Reverse category lookup for ruwikisource-category
        String query = "SELECT * %7B?object ?predicate %3Chttps://ru.wikisource.org/wiki/Категория:ЕЭБЕ:Перенаправления%3E%7D";
        Response response = target( "/sparql" ).queryParam( "query", baseQueryPartForGet + query ).request().get();
        assertEquals( 200, response.getStatus() );
        String result = response.readEntity( String.class );
        String[] splitResult = result.split( System.lineSeparator() );
        assertTrue( splitResult.length > 2 );
        assertEqualsWithoutEOL( "object,predicate", splitResult[ 0 ] );
        assertTrue( splitResult[ 1 ].contains( "ru.wikisource.org" ) );
    }

    @Test
    public void shouldResponseWithInnerLinksFr() {
        //Outbound links for fr-template with non-encoded umlaut:
        String query = "SELECT * %7B%3Chttps://fr.wikipedia.org/wiki/Mod%C3%A8le:Infobox_Galaxie%3E mw:internalLinkTo ?object%7D";
        Response response = target( "/sparql" ).queryParam( "query", baseQueryPartForGet + query ).request().get();
        assertEquals( 200, response.getStatus() );
        String result = response.readEntity( String.class );
        String[] splitResult = result.split( System.lineSeparator() );
        assertTrue( splitResult.length > 2 );
        assertEqualsWithoutEOL( "object", splitResult[ 0 ] );
        assertTrue( splitResult[ 1 ].contains( "fr.wikipedia.org" ) );
    }

    @Test
    public void shouldResponseWithWikidataLinksLimitBy10000() {
        //10000 usages of ru-wiki module:
        String query = "SELECT * %7B?page mw:includesPage %3Chttps://ru.wikipedia.org/wiki/%D0%9C%D0%BE%D0%B4%D1%83%D0%BB%D1%8C:Wikidata%3E%7D LIMIT 10000";
        Response response = target( "/sparql" ).queryParam( "query", baseQueryPartForGet + query ).request().get();
        assertEquals( 200, response.getStatus() );
        String result = response.readEntity( String.class );
        String[] splitResult = result.split( System.lineSeparator() );
        assertEquals( 10001, splitResult.length );
        assertEqualsWithoutEOL( "page", splitResult[ 0 ] );
    }

    @Test
    public void shouldResponseWithPageNameDe() {
        //Name of the page in non-default namespace of de-wiki
        String query = "SELECT * %7B%3Chttps://de.wikipedia.org/wiki/Wikipedia:Hauptseite%3E schema:name ?page%7D";
        Response response = target( "/sparql" ).queryParam( "query", baseQueryPartForGet + query ).request().get();
        assertEquals( 200, response.getStatus() );
        String result = response.readEntity( String.class );
        String[] splitResult = result.split( System.lineSeparator() );
        assertEquals( 2, splitResult.length );
        assertEqualsWithoutEOL( "page", splitResult[ 0 ] );
        assertEqualsWithoutEOL( "Hauptseite", splitResult[ 1 ] );
    }

    @Test
    public void shouldResponseWithNamespaceIdZh() {
        //Namespace id of zh-wiki talk page:
        String query = "SELECT * %7B%3Chttps://zh.wikipedia.org/wiki/Talk:%E5%9C%8B%E7%AB%8B%E5%AE%9C%E8%98%AD%E9%AB%98%E7%B4%9A%E4%B8%AD%E5%AD%B8%3E mw:pageNamespaceId ?ns%7D";
        Response response = target( "/sparql" ).queryParam( "query", baseQueryPartForGet + query ).request().get();
        assertEquals( 200, response.getStatus() );
        String result = response.readEntity( String.class );
        String[] splitResult = result.split( System.lineSeparator() );
        assertEquals( 2, splitResult.length );
        assertEqualsWithoutEOL( "ns", splitResult[ 0 ] );
        assertEqualsWithoutEOL( "1", splitResult[ 1 ] );
    }

    @Test
    public void shouldResponseWithPageClassEn() {
        //Class for page in enwiki-specific namespace:
        String query = "SELECT * %7B%3Chttps://en.wikipedia.org/wiki/TimedText:Raelsample.ogg.fr.srt%3E rdf:type ?class%7D";
        Response response = target( "/sparql" ).queryParam( "query", baseQueryPartForGet + query ).request().get();
        assertEquals( 200, response.getStatus() );
        String result = response.readEntity( String.class );
        String[] splitResult = result.split( System.lineSeparator() );
        assertEquals( 2, splitResult.length );
        assertEqualsWithoutEOL( "class", splitResult[ 0 ] );
        assertEqualsWithoutEOL( "http://tools.wmflabs.org/mw2sparql/ontology#Page", splitResult[ 1 ] );
    }

    @Test
    public void shouldReturnBadRequestWithoutQueryPost() {
        MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();
        Response response = target( "/sparql" ).request().post( Entity.form( formData ) );
        assertEquals( 400, response.getStatus() );
    }

    @Test
    public void shouldResponseWithTrueOnAskQueryPost() {
        //ASK for fr-category with url-encoded umlauts:
        String query = "ASK{<https://fr.wikipedia.org/wiki/Catégorie:Langage_de_requête>?predicate?object}";

        MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();
        formData.add( "query", baseQueryPartForPost + query );

        Invocation.Builder request = target( "/sparql" ).request();
        Response response = request.post( Entity.form( formData ) );
        assertEquals( 200, response.getStatus() );
        String result = response.readEntity( String.class );
        assertTrue( result.contains( ">true<" ) );
    }
}