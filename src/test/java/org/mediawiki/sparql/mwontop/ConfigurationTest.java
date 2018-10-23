package org.mediawiki.sparql.mwontop;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author MZhavzharov.
 */
public class ConfigurationTest {

    @BeforeClass
    public static void init() {
        System.setProperty( "app.test.list", "1,2,3" );
        System.setProperty( "app.test.list.spaces", "1, 2 ,  3" );
        System.setProperty( "app.test.list.empty", "" );
    }

    @Test
    public void shouldReturnListOfStrings() {
        List<String> list = Configuration.instance().getPropertyAsList( "app.test.list" );
        assertEquals( 3, list.size() );
        assertEquals( "1", list.get( 0 ) );
        assertEquals( "2", list.get( 1 ) );
        assertEquals( "3", list.get( 2 ) );
    }

    @Test
    public void shouldReturnListOfStringsWithSpacesDelimiters() {
        List<String> list = Configuration.instance().getPropertyAsList( "app.test.list.spaces" );
        assertEquals( 3, list.size() );
        assertEquals( "1", list.get( 0 ) );
        assertEquals( "2", list.get( 1 ) );
        assertEquals( "3", list.get( 2 ) );
    }

    @Test
    public void shouldReturnEmptyList() {
        List<String> list = Configuration.instance().getPropertyAsList( "no.such.key" );
        assertTrue( list.isEmpty() );

        list = Configuration.instance().getPropertyAsList( "app.test.list.empty" );
        assertTrue( list.isEmpty() );
    }
}
