package com.github.sftwnd.etsiorg;

import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SummaryTest {

    @Test
    void emptyTest() {
        assertEquals(0L, Summary.EMPTY.getDownloaded());
        assertEquals(0L, Summary.EMPTY.getUpToDate());
    }

    @Test
    void singleFileTest() {
        assertEquals(1L, Summary.DOWNLOADED.getDownloaded());
        assertEquals(0L, Summary.DOWNLOADED.getUpToDate());
        assertEquals(0L, Summary.UP_TO_DATE.getDownloaded());
        assertEquals(1L, Summary.UP_TO_DATE.getUpToDate());
    }

    @Test
    void plusTest() {
        Summary summary = Stream.of(Summary.DOWNLOADED, Summary.UP_TO_DATE, Summary.DOWNLOADED, Summary.EMPTY)
                .reduce(Summary.EMPTY, Summary::plus);
        assertEquals(2L, summary.getDownloaded());
        assertEquals(1L, summary.getUpToDate());
        assertEquals(Summary.DOWNLOADED.plus(Summary.UP_TO_DATE), Summary.UP_TO_DATE.plus(Summary.DOWNLOADED));
    }

    @Test
    void plusNullTest() {
        assertThrows(NullPointerException.class, () -> Summary.EMPTY.plus(null));
    }

}
