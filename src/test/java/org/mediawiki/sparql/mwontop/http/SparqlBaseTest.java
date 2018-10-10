package org.mediawiki.sparql.mwontop.http;

import org.glassfish.jersey.test.JerseyTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;

/**
 * @author MZhavzharov.
 */
public class SparqlBaseTest extends JerseyTest {

    private static final Logger LOGGER = LoggerFactory.getLogger( SparqlBaseTest.class );

    /**
     * Removes trailing end of line characters from compare strings and asserts the result.
     */
    public void assertEqualsWithoutEOL( String expected, String actual ) {
        if ( expected != null ) {
            expected = expected.replaceAll( "\\r\\n", "" ).replaceAll( "\\r", "" );
        }
        LOGGER.info( "EXPECTED: " + expected );
        if ( actual != null ) {
            actual = actual.replaceAll( "\\r\\n", "" ).replaceAll( "\\r", "" );
        }
        LOGGER.info( "ACTUAL: " + actual );
        assertEquals( expected, actual );
    }
}
