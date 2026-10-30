package com.github.sftwnd.etsiorg;

import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SummaryTest {

    @Test
    void emptyTest() {
        assertEquals(0L, Summary.EMPTY.getDownloaded());
        assertEquals(0L, Summary.EMPTY.getBytes());
        assertEquals(0L, Summary.EMPTY.getUpToDate());
        assertEquals(0L, Summary.EMPTY.getRemoved());
        assertEquals(0L, Summary.EMPTY.getFailed());
    }

    @Test
    void singleFileTest() {
        Summary downloaded = Summary.downloaded(1024L);
        assertEquals(1L, downloaded.getDownloaded());
        assertEquals(1024L, downloaded.getBytes());
        assertEquals(0L, downloaded.getUpToDate());
        assertEquals(0L, Summary.UP_TO_DATE.getDownloaded());
        assertEquals(1L, Summary.UP_TO_DATE.getUpToDate());
        assertEquals(1L, Summary.downloaded(0L).getDownloaded());
        assertThrows(IllegalArgumentException.class, () -> Summary.downloaded(-1L));
    }

    @Test
    void plusTest() {
        Summary summary = Stream.of(Summary.downloaded(10L), Summary.UP_TO_DATE, Summary.downloaded(20L), Summary.removed(2), Summary.EMPTY)
                .reduce(Summary.EMPTY, Summary::plus);
        assertEquals(2L, summary.getDownloaded());
        assertEquals(30L, summary.getBytes());
        assertEquals(1L, summary.getUpToDate());
        assertEquals(2L, summary.getRemoved());
        assertEquals(Summary.downloaded(10L).plus(Summary.UP_TO_DATE), Summary.UP_TO_DATE.plus(Summary.downloaded(10L)));
    }

    @Test
    void removedTest() {
        assertEquals(Summary.EMPTY, Summary.removed(0));
        assertEquals(3L, Summary.removed(3).getRemoved());
        assertEquals(0L, Summary.removed(3).getFailed());
        assertThrows(IllegalArgumentException.class, () -> Summary.removed(-1));
    }

    @Test
    void failedTest() {
        assertEquals(1L, Summary.FAILED.getFailed());
        assertEquals(Summary.EMPTY, Summary.failed(0));
        assertEquals(3L, Summary.failed(3).getFailed());
        assertEquals(4L, Summary.failed(3).plus(Summary.FAILED).plus(Summary.downloaded(1L)).getFailed());
        assertThrows(IllegalArgumentException.class, () -> Summary.failed(-1));
    }

    @Test
    void plusNullTest() {
        assertThrows(NullPointerException.class, () -> Summary.EMPTY.plus(null));
    }

}
