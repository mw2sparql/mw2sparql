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

import static org.junit.Assert.*;

/**
 * @author MZhavzharov.
 */
public class SPARQLActionsIntegrationTest extends SparqlBaseTest {

    private final String baseQueryPartForGet = encodeUrl( "PREFIX rdf: <https://www.w3.org/1999/02/22-rdf-syntax-ns#> PREFIX schema: <http://schema.org/> " +
            "PREFIX mw: <https://mw2sparql.toolforge.org/ontology#> " );

    private final String baseQueryPartForPost = "PREFIX rdf: <https://www.w3.org/1999/02/22-rdf-syntax-ns#> PREFIX schema: <http://schema.org/> PREFIX mw: <http://tools.wmflabs" +
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

    @Test
    public void shouldResponseWithTrueOnAskQuery() {
        //ASK for fr-category with url-encoded umlauts:
        String query = encodeUrl( "ASK {<https://fr.wikipedia.org/wiki/Catégorie:Langage_de_requête> ?predicate ?object}" );
        Response response = target( "/sparql" ).queryParam( "query", baseQueryPartForGet + query ).request().get();
        assertEquals( 200, response.getStatus() );
        String result = response.readEntity( String.class );
        assertTrue( result.contains( ">true<" ) );
    }

//    @Test
//    public void shouldResponseWithLinkAndCategoryRu() {
        //Reverse category lookup for ruwikisource-category
//        String query = encodeUrl( "SELECT * {?object ?predicate <https://ru.wikisource.org/wiki/Категория:ЕЭБЕ:Перенаправления>}" );
//        Response response = target( "/sparql" ).queryParam( "query", baseQueryPartForGet + query ).request().get();
//        assertEquals( 200, response.getStatus() );
//        String result = response.readEntity( String.class );
//        String[] splitResult = result.split( System.lineSeparator() );
//        assertTrue( splitResult.length > 2 );
//        assertEqualsWithoutEOL( "object,predicate", splitResult[ 0 ] );
//        assertTrue( splitResult[ 1 ].contains( "ru.wikisource.org" ) );
//    }

    @Test
    public void shouldResponseWithInnerLinksFr() {
        //Outbound links for fr-template with non-encoded umlaut:
        String query = encodeUrl( "SELECT * {<https://fr.wikipedia.org/wiki/Modèle:Infobox_Galaxie> mw:internalLinkTo ?object}" );
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
        String query = encodeUrl( "SELECT * {?page mw:includesPage <https://ru.wikipedia.org/wiki/Модуль:Wikidata>} LIMIT 10000" );
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
        String query = encodeUrl( "SELECT * {<https://de.wikipedia.org/wiki/Wikipedia:Hauptseite> schema:name ?page}" );
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
        String query = encodeUrl( "SELECT * {<https://zh.wikipedia.org/wiki/Talk:國立宜蘭高級中學> mw:pageNamespaceId ?ns}" );
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
        String query = encodeUrl( "SELECT * {<https://en.wikipedia.org/wiki/TimedText:Raelsample.ogg.fr.srt> rdf:type ?class}" );
        Response response = target( "/sparql" ).queryParam( "query", baseQueryPartForGet + query ).request().get();
        assertEquals( 200, response.getStatus() );
        String result = response.readEntity( String.class );
        String[] splitResult = result.split( System.lineSeparator() );
        assertEquals( 2, splitResult.length );
        assertEqualsWithoutEOL( "class", splitResult[ 0 ] );
        assertEqualsWithoutEOL( "http://tools.wmflabs.org/mw2sparql/ontology#Page", splitResult[ 1 ] );
    }

    @Test
    public void shouldResponseContainingApostrophe() {
        String query = encodeUrl( "SELECT * {<https://fr.wikipedia.org/wiki/`Anizzah> mw:includesPage ?object}" );

        Response response = target( "/sparql" ).queryParam( "query", baseQueryPartForGet + query ).request().get();
        assertEquals( 200, response.getStatus() );
        String result = response.readEntity( String.class );
        String[] splitResult = result.split( System.lineSeparator() );
        assertTrue( splitResult.length > 1 );
        assertEqualsWithoutEOL( "object", splitResult[ 0 ] );
        assertTrue( splitResult[ 1 ].contains( "https://fr.wikipedia.org" ) );
    }

    @Test
    public void shouldSkipFilteredDomain() {
        String query = encodeUrl( "SELECT * {<https://meta.wikimedia.org/wiki/Category:Wikimedia_Foundation_staff> mw:includesPage ?object}" );

        Response response = target( "/sparql" ).queryParam( "query", baseQueryPartForGet + query ).request().get();
        assertEquals( 200, response.getStatus() );
        String result = response.readEntity( String.class );
        String[] splitResult = result.split( System.lineSeparator() );
        assertEquals( 1, splitResult.length );
        assertEqualsWithoutEOL( "object", splitResult[ 0 ] );
    }

    @Test
    public void shouldSkipFilteredDomainInBatch() {
        String query = encodeUrl( "SELECT ?article ?catArticle " +
                "WHERE { SELECT * { ?article mw:inCategory ?catArticle } }" +
                "VALUES ( ?catArticle) {" +
                "(<https://zh.wikiquote.org/wiki/Category:維基人>)" +
                "(<https://meta.wikimedia.org/wiki/Category:Wikimedia_Foundation_staff>)" +
                "(<https://nov.wikipedia.org/wiki/Category:Wikimedia_Foundation_staff>)" +
                "}" );

        Response response = target( "/sparql" ).queryParam( "query", baseQueryPartForGet + query ).request().get();
        assertEquals( 200, response.getStatus() );
        String result = response.readEntity( String.class );
        String[] splitResult = result.split( System.lineSeparator(), 2 );
        assertTrue( splitResult.length > 1 );
        assertEqualsWithoutEOL( "catArticle,article", splitResult[ 0 ] );
        assertFalse( result.contains( "meta.wikimedia.org" ) );
        assertTrue( result.contains( "zh.wikiquote.org" ) );
        assertTrue( result.contains( "nov.wikipedia.org" ) );
    }

    @Test
    public void shouldResponseWithPageFilteredByNameByCategoryAndByPageNamespaceId() {
        String query = encodeUrl( "SELECT ?page { ?page mw:inCategory  ?catArticle; mw:pageNamespaceId 0; schema:name \"Федущак_Інна_Вікторівна\"@uk }" +
                "VALUES ( ?catArticle) {\n" +
                "( <https://uk.wikipedia.org/wiki/%D0%9A%D0%B0%D1%82%D0%B5%D0%B3%D0%BE%D1%80%D1%96%D1%8F:%D0%A3%D0%BA%D1%80%D0%B0%D1%97%D0%BD%D1%81%D1%8C%D0%BA%D1%96_%D0%BA%D1" +
                "%80%D0%B0%D1%94%D0%B7%D0%BD%D0%B0%D0%B2%D1%86%D1%96> )}");

        Response response = target( "/sparql" ).queryParam( "query", baseQueryPartForGet + query ).request().get();
        assertEquals( 200, response.getStatus() );
        String result = response.readEntity( String.class );
        String[] splitResult = result.split( System.lineSeparator(), 2 );
        assertEqualsWithoutEOL( "page", splitResult[ 0 ] );
        assertEqualsWithoutEOL( "https://uk.wikipedia.org/wiki/%D0%A4%D0%B5%D0%B4%D1%83%D1%89%D0%B0%D0%BA_%D0%86%D0%BD%D0%BD%D0%B0_%D0%92%D1%96%D0%BA%D1%82%D0%BE%D1%80%D1%96%D0%B2%D0%BD%D0%B0", splitResult[ 1 ] );
    }

    @Test
    public void shouldResponseWithDefaultEnWikipediaOrgDomainSubstitution() {
        String query = encodeUrl( "SELECT ?a ?b ?c WHERE { ?a ?b ?c } LIMIT 1");

        Response response = target( "/sparql" ).queryParam( "query", baseQueryPartForGet + query ).request().get();
        assertEquals( 200, response.getStatus() );
        String result = response.readEntity( String.class );
        String[] splitResult = result.split( System.lineSeparator());
        assertEqualsWithoutEOL( "a,b,c", splitResult[0] );
        assertTrue( splitResult[ 1 ].contains( "https://en.wikipedia.org" ) );
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
