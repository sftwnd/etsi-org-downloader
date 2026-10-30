package com.github.sftwnd.etsiorg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URI;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsTest {

    @Test
    void defaultsTest() {
        Settings settings = settings(Map.of(), Map.of());
        assertEquals(URI.create("https://www.etsi.org/deliver/"), settings.getUri());
        assertEquals(Path.of("."), settings.getDest());
        assertEquals(4, settings.getThreads());
        assertEquals(ZoneOffset.UTC, settings.getZone());
        assertFalse(settings.isDryRun());
    }

    @Test
    void dryRunTest() {
        assertTrue(settings(Map.of("dry-run", "true"), Map.of()).isDryRun());
        assertTrue(settings(Map.of(), Map.of("ETSIORG_DRY_RUN", " TRUE ")).isDryRun());
        assertFalse(settings(Map.of("dry-run", "False"), Map.of("ETSIORG_DRY_RUN", "true")).isDryRun());
    }

    @Test
    void propertiesTest() {
        Settings settings = settings(Map.of(
                "uri", "https://www.etsi.org/deliver/etsi_ts/",
                "dest", "/data/etsi",
                "threads", "2",
                "zone", "Europe/Paris"), Map.of());
        assertEquals(URI.create("https://www.etsi.org/deliver/etsi_ts/"), settings.getUri());
        assertEquals(Path.of("/data/etsi"), settings.getDest());
        assertEquals(2, settings.getThreads());
        assertEquals(ZoneId.of("Europe/Paris"), settings.getZone());
    }

    @Test
    void environmentTest() {
        Settings settings = settings(Map.of(), Map.of(
                "ETSIORG_URI", "https://www.etsi.org/deliver/etsi_en/",
                "ETSIORG_DEST", "/srv/etsi",
                "ETSIORG_THREADS", "8",
                "ETSIORG_ZONE", "+03:00"));
        assertEquals(URI.create("https://www.etsi.org/deliver/etsi_en/"), settings.getUri());
        assertEquals(Path.of("/srv/etsi"), settings.getDest());
        assertEquals(8, settings.getThreads());
        assertEquals(ZoneOffset.ofHours(3), settings.getZone());
    }

    @Test
    void propertyOverridesEnvironmentTest() {
        Settings settings = settings(
                Map.of("uri", "https://www.etsi.org/deliver/etsi_ts/", "threads", "2"),
                Map.of("ETSIORG_URI", "https://www.etsi.org/deliver/etsi_en/", "ETSIORG_THREADS", "8", "ETSIORG_ZONE", "+03:00"));
        assertEquals(URI.create("https://www.etsi.org/deliver/etsi_ts/"), settings.getUri());
        assertEquals(2, settings.getThreads());
        assertEquals(ZoneOffset.ofHours(3), settings.getZone());
    }

    @Test
    void blankValueIsNotSetTest() {
        Settings settings = settings(
                Map.of("threads", " ", "zone", "", "dest", "\t"),
                Map.of("ETSIORG_THREADS", "8", "ETSIORG_ZONE", " ", "ETSIORG_DEST", ""));
        assertEquals(8, settings.getThreads());
        assertEquals(ZoneOffset.UTC, settings.getZone());
        assertEquals(Path.of("."), settings.getDest());
    }

    @Test
    void trimmedValueTest() {
        Settings settings = settings(Map.of("threads", " 16 ", "zone", " +03:00 "), Map.of("ETSIORG_DEST", " /data/etsi "));
        assertEquals(16, settings.getThreads());
        assertEquals(ZoneOffset.ofHours(3), settings.getZone());
        assertEquals(Path.of("/data/etsi"), settings.getDest());
    }

    @ParameterizedTest
    @CsvSource({
            "uri, https://www.etsi.org/de liver/",
            "threads, 0", "threads, -1", "threads, four", "threads, 1.5", "threads, 99999999999",
            "zone, Mars/Olympus", "zone, +25:00", "zone, UTC+",
            "dry-run, yes", "dry-run, 1"})
    void invalidValueTest(String property, String value) {
        String variable = Settings.Key.valueOf(property.toUpperCase(Locale.ROOT).replace('-', '_')).variable();
        assertThrows(IllegalArgumentException.class, () -> settings(Map.of(property, value), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> settings(Map.of(), Map.of(variable, value)));
    }

    @Test
    void invalidDestinationTest() {
        assertThrows(IllegalArgumentException.class, () -> settings(Map.of("dest", "mir\0ror"), Map.of()));
    }

    @Test
    void helpTest() {
        String help = Settings.help();
        assertEquals(Settings.Key.values().length, help.lines().count(), help);
        for (Settings.Key key : Settings.Key.values()) {
            assertTrue(help.contains("-D" + key.property() + " "), help);
            assertTrue(help.contains(" ETSIORG_" + key.name() + " "), help);
        }
        assertTrue(help.contains("(default: https://www.etsi.org/deliver/)"), help);
        assertTrue(help.contains("(default: 4)"), help);
    }

    private static Settings settings(Map<String, String> properties, Map<String, String> environment) {
        return Settings.of(properties::get, environment::get);
    }

}
