package com.github.sftwnd.etsiorg;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HtmlTest {

    @Test
    void textWithoutReferencesTest() {
        String text = "/deliver/etsi_ts/ts_129078v170000p.pdf";
        assertSame(text, Html.unescape(text));
    }

    @Test
    void namedReferencesTest() {
        assertEquals("a&b<c>d\"e'f", Html.unescape("a&amp;b&lt;c&gt;d&quot;e&apos;f"));
    }

    @Test
    void numericReferencesTest() {
        assertEquals("ts_1/2 Д", Html.unescape("ts&#95;1&#x2F;2&#32;&#x414;"));
    }

    @Test
    void singlePassTest() {
        assertEquals("&amp;", Html.unescape("&amp;amp;"));
    }

    @Test
    void invalidReferencesAreKeptTest() {
        assertEquals("&#0;&#x110000;&unknown;&amp", Html.unescape("&#0;&#x110000;&unknown;&amp"));
    }

    @Test
    void nullTest() {
        assertThrows(NullPointerException.class, () -> Html.unescape(null));
    }

}
