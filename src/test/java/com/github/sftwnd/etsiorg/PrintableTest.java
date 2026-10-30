package com.github.sftwnd.etsiorg;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class PrintableTest {

    @Test
    void safeTextIsNotChangedTest() {
        String text = "/deliver/etsi_ts/129000_129099/ts_129078v170000p.pdf";
        assertSame(text, Printable.of(text));
    }

    @Test
    void nonAsciiTextIsNotChangedTest() {
        assertEquals("Документ ETSI", Printable.of("Документ ETSI"));
    }

    @Test
    void nullTest() {
        assertEquals("null", Printable.of(null));
    }

    @Test
    void objectTest() {
        assertEquals("/deliver/a\\u000Ab", Printable.of(Path.of("/deliver/a\nb")));
    }

    @Test
    void lineBreaksTest() {
        assertEquals("a\\u000D\\u000Ab\\u2028c\\u2029d", Printable.of("a\r\nb c d"));
    }

    @Test
    void terminalEscapeSequenceTest() {
        assertEquals("\\u001B]0;title\\u0007", Printable.of("\u001B]0;title\u0007"));
    }

    @Test
    void controlCharactersTest() {
        assertEquals("\\u0000\\u0009\\u007F\\u009B", Printable.of("\u0000\t\u007F\u009B"));
    }

    @Test
    void bidiOverrideTest() {
        assertEquals("file\\u202Efdp.exe", Printable.of("file‮fdp.exe"));
    }

    @Test
    void supplementaryFormatCharacterTest() {
        assertEquals("a\\uE0001b", Printable.of("a" + new String(Character.toChars(0xE0001)) + "b"));
    }

}
