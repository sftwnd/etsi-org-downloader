package com.github.sftwnd.etsiorg;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmptyProcessorTest {

    @Test
    void processDoesNotThrowTest() {
        assertDoesNotThrow(EmptyProcessor.DEFAULT::process);
    }

    @Test
    void processNotNullTest() {
        assertNotNull(EmptyProcessor.DEFAULT.process());
    }

    @Test
    void processFutureIsDoneTest() {
        assertTrue(EmptyProcessor.DEFAULT.process().isDone());
    }

    @Test
    void processFutureCloseTest() {
        assertEquals(Collections.emptyList(), EmptyProcessor.DEFAULT.process().join().collect(Collectors.toList()));
    }

}