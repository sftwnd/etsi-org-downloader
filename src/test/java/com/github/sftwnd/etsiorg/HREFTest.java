package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HREFTest {

    private static final String DEFAULT_URI = "http://localhost/";
    private static final String DEFAULT_VERSIONED_NAME = "01.02.03_04";
    private static final long DEFAULT_VERSION = LongStream.range(0,4).map(l -> (l + 1) << (12 * (4 - l - 1))).sum();
    private HREF.Builder builder;

    @Test
    void noUriTest() {
        assertThrows(NullPointerException.class, builder::build);
    }
    @Test
    void withUriTest() {
        assertDoesNotThrow(builder()::build);
    }

    @Test
    void unknownVersionTest() {
        assertEquals(0, builder().build().getVersion());
    }

    @Test
    void knownVersionTest() {
        long version = new Random().nextLong();
        assertEquals(version, builder().version(version).build().getVersion());
    }

    @Test
    void versionNameZeroTest() {
        IntStream.of(-1,0)
                .mapToObj(HREF::versionName)
                .forEach(Assertions::assertNull);
    }

    @Test
    void versionNameTest() {
        assertEquals(DEFAULT_VERSIONED_NAME, HREF.versionName(DEFAULT_VERSION));
    }

    @Test
    void calculatedVersionTest() {
        assertEquals(DEFAULT_VERSION, builder(DEFAULT_VERSIONED_NAME).build().getVersion());
    }

    @Test
    void noBytesTest() {
        assertNull(builder().build().getBytes());
    }

    @Test
    void bytesTest() {
        assertEquals(10, builder().regularFile(true).bytes(10).dateTime(LocalDateTime.now()).build().getBytes());
    }
    @Test
    void negativeBytesTest() {
        assertThrows(IllegalArgumentException.class, builder().bytes(-1L)::build);
    }

    @Test
    void noDateTimeTest() {
        assertNull(builder().build().getDateTime());
    }

    @Test
    void dateTimeTest() {
        LocalDateTime dateTime = LocalDateTime.now().plusMinutes(17);
        assertEquals(dateTime, builder().bytes(0L).dateTime(dateTime).build().getDateTime());
    }

    @Test
    void noRegularFileTest() {
        assertNull(builder().build().getRegularFile());
    }

    @Test
    void throwRegularFileDateTimeTest() {
        assertThrows(NullPointerException.class, builder().bytes(1L)::build);
    }

    @Test
    void throwNonRegularFileBytesTest() {
        assertThrows(IllegalArgumentException.class, builder().bytes(0L).regularFile(false)::build);
    }

    @Test
    void throwNonRegularFileDateTimeTest() {
        assertThrows(IllegalArgumentException.class, builder().dateTime(LocalDateTime.now()).regularFile(false)::build);
    }

    @Test
    void rootNamePath() {
        assertNotNull(builder().build().name());
    }

    @Test
    void throwRegularFileBytesTest() {
        assertThrows(NullPointerException.class, builder().dateTime(LocalDateTime.now())::build);
    }

    @Test
    void regularFileTest() {
        assertEquals(true, builder().bytes(0L).dateTime(LocalDateTime.now()).regularFile(true).build().getRegularFile());
    }

    @Test
    void nameTest() {
        assertEquals(DEFAULT_VERSIONED_NAME, builder(Path.of("alpha", DEFAULT_VERSIONED_NAME).toString()).build().name().toString());
    }

    @Test
    void pathTest() {
        String path = Path.of("/zero", DEFAULT_VERSIONED_NAME).toString();
        assertEquals(path, builder(path).build().path().toString());
    }

    @Test
    void nonVersionedTest() {
        assertFalse(builder("nevada").build().isVersioned());
    }

    @Test
    void isVersioned() {
        assertTrue(builder(DEFAULT_VERSIONED_NAME).build().isVersioned());
    }

    private HREF.Builder builder() {
        return builder("");
    }

    private HREF.Builder builder(@NonNull String name) {
        return builder.uri(
                URI.create(DEFAULT_URI)
                        .resolve("/")
                        .resolve(Path.of(name).toString()));
    }

    @BeforeEach
    void startUp() {
        this.builder = HREF.builder();
    }

    @AfterEach
    void tearDown() {
        this.builder = null;
    }

}