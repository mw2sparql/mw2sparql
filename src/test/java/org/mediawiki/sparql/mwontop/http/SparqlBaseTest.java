package org.mediawiki.sparql.mwontop.http;

import org.glassfish.jersey.test.JerseyTest;

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
}
