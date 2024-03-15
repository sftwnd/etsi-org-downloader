package com.github.sftwnd.etsiorg;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PageTest {

    @Test
    void ofNullHrefThrowsTest() {
        assertThrows(NullPointerException.class, () -> Page.of(nullHref.get()));
    }

    @Test
    void ofNonNullHrefDoesNotThrowTest() {
        assertDoesNotThrow(this::page);
    }

    @Test
    void ofNonNullHrefTest() {
        assertSame(this.href, this.page().getHref());
    }

    @Test
    void dateTimeTest() {
        LocalDateTime dateTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).minusHours(1);
        when(href.getDateTime()).thenReturn(dateTime);
        Instant checkInstant = dateTime.atZone(ZoneId.systemDefault()).toInstant();
        assertEquals(checkInstant, this.page().dateTime());
    }

    @Test
    void contentLengthTest() throws IOException {
        long bytes = Math.abs(new Random().nextLong()) + 1;
        when(this.href.getBytes()).thenReturn(bytes);
        assertDoesNotThrow(() -> this.page().contentLength());
        verify(this.href, atLeastOnce()).getBytes();
        assertEquals(bytes, this.page().contentLength());
    }

    @Test
    void inputStreamTest() throws IOException {
        Page page = spy(this.page());
        doNothing().when(page).connect(0L);
        assertDoesNotThrow(page::inputStream);
        verify(page, atLeastOnce()).connect(0L);
    }

    @Test
    void pathTest() {
        assertSame(this.href.path(), this.page().path());
    }

    @Test
    void fileNameTest() {
        String fileName = "fileName";
        when(href.name()).thenReturn(Path.of(fileName));
        assertEquals(fileName, this.page().fileName());
    }

    @Test
    void getUriTest() {
        assertSame(this.uri, this.page().getUri());
    }

    private Page page() {
        return Page.of(this.href);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void startUp() {
        this.href = mock(HREF.class);
        this.nullHref = mock(Supplier.class);
        when(this.nullHref.get()).thenReturn(null);
        this.uri = mock(URI.class);
        when(this.href.getUri()).thenReturn(this.uri);
    }

    @AfterEach
    void tearDown() {
        this.href = null;
        this.nullHref = null;
        this.uri = null;
    }

    private URI uri;
    private HREF href;
    private Supplier<HREF> nullHref;

}