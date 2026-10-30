package com.github.sftwnd.etsiorg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MainTest {

    @Test
    void defaultThreadsTest() {
        assertEquals(Runtime.getRuntime().availableProcessors(), Main.threads(null));
        assertEquals(Runtime.getRuntime().availableProcessors(), Main.threads(" "));
    }

    @Test
    void threadsTest() {
        assertEquals(4, Main.threads("4"));
        assertEquals(16, Main.threads(" 16 "));
    }

    @Test
    void defaultZoneTest() {
        assertEquals(ZoneOffset.UTC, Main.zone(null));
        assertEquals(ZoneOffset.UTC, Main.zone(""));
    }

    @Test
    void zoneTest() {
        assertEquals(ZoneId.of("Europe/Paris"), Main.zone("Europe/Paris"));
        assertEquals(ZoneOffset.ofHours(3), Main.zone(" +03:00 "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Mars/Olympus", "+25:00", "UTC+"})
    void invalidZoneTest(String value) {
        assertThrows(IllegalArgumentException.class, () -> Main.zone(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "four", "1.5", "99999999999"})
    void invalidThreadsTest(String value) {
        assertThrows(IllegalArgumentException.class, () -> Main.threads(value));
    }

}
