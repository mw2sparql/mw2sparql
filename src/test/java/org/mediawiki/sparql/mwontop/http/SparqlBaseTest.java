package org.mediawiki.sparql.mwontop.http;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.glassfish.jersey.test.JerseyTest;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import static org.junit.Assert.assertEquals;

/**
 * @author MZhavzharov.
 */
public class SparqlBaseTest extends JerseyTest {

    /**
     * Removes trailing end of line characters from compare strings and asserts the result.
     */
    public void assertEqualsWithoutEOL( String expected, String actual ) {
        if ( expected != null ) {
            expected = expected.replaceAll( "\\r\\n", "" ).replaceAll( "\\r", "" );
        }
        if ( actual != null ) {
            actual = actual.replaceAll( "\\r\\n", "" ).replaceAll( "\\r", "" );
        }
        assertEquals( expected, actual );
    }

    @NonNull
    public String encodeUrl( @NonNull String url ) {
        try {
            String encoded = URLEncoder.encode( url, "UTF-8" );
            encoded = encoded.replace( "+", " " );
            return encoded;
        } catch ( UnsupportedEncodingException e ) {
            throw new RuntimeException( e );
        }
    }
}
