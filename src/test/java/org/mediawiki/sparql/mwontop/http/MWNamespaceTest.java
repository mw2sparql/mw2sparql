package org.mediawiki.sparql.mwontop.http;

import static org.mediawiki.sparql.mwontop.http.MWNamespace.*;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author MZhavzharov.
 */
public class MWNamespaceTest {

    @Test
    public void shouldReturnTheSameTextWithoutPatternMatches() {
        String test = mutateNamespace( "test", true );
        assertEquals( "test", test );

        test = mutateNamespace( "test", false );
        assertEquals( "test", test );
    }

    @Test
    public void shouldReplaceNamespaceForCategory() {
        String test = mutateNamespace( "https://en.wikipedia.org/wiki/Category:Voice_types", true );
        assertEquals( "https://en.wikipedia.org/wiki/mw14ns:Voice_types", test );
    }

    @Test
    public void shouldDecodeUrlButLeaveExceptedSymbols() {
        String test = mutateNamespace( "https://en.wikipedia.org/wiki/Category:test %60 %22 %28 %29 %2F %3A %60 %22 %28 %29 %2F %3A", true );
        assertEquals( "https://en.wikipedia.org/wiki/mw14ns:test %60 %22 ( ) / : %60 %22 ( ) / :", test );
    }

    @Test
    public void shouldEncodeUrlButLeaveExceptedSymbols() {
        String test = mutateNamespace( "https://en.wikipedia.org/wiki/Category:test ` \" ( ) / : ` \" ( ) / :", true );
        assertEquals( "https://en.wikipedia.org/wiki/mw14ns:test %60 %22 ( ) / : %60 %22 ( ) / :", test );
    }
}