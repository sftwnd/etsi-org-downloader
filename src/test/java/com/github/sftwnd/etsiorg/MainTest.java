package com.github.sftwnd.etsiorg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "four", "1.5", "99999999999"})
    void invalidThreadsTest(String value) {
        assertThrows(IllegalArgumentException.class, () -> Main.threads(value));
    }

}
