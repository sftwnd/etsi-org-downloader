package com.github.sftwnd.etsiorg;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
        Page page = this.page();
        doNothing().when(page).connect(0L);
        assertDoesNotThrow(page::inputStream);
        verify(page, atLeastOnce()).connect(0L);
    }

    @Test
    void inputStreamOffsetPartialContentTest() throws IOException, URISyntaxException {
        assertInputStreamFromOffset(true);
    }

    @Test
    void inputStreamOffsetRangeIgnoredTest() throws IOException, URISyntaxException {
        assertInputStreamFromOffset(false);
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

    @Test
    void toStringTest() {
        Page page = page();
        when(href.name()).thenReturn(Path.of("path", "fileName"));
        when(page.fileName()).thenReturn("fileName.pdf");
        assertDoesNotThrow(page::toString);
        verify(page, atLeastOnce()).getHref();
        verify(page, atLeastOnce()).path();
        verify(page, atLeastOnce()).fileName();
    }

    private Page page() {
        return spy(Page.of(this.href));
    }

    private void assertInputStreamFromOffset(boolean acceptRanges) throws IOException, URISyntaxException {
        byte[] content = "0123456789abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.US_ASCII);
        int offset = content.length / 3;
        Collection<String> ranges = new ConcurrentLinkedQueue<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            String range = exchange.getRequestHeaders().getFirst("Range");
            ranges.add(String.valueOf(range));
            int from = acceptRanges && range != null ? offset : 0;
            exchange.sendResponseHeaders(from > 0 ? HttpURLConnection.HTTP_PARTIAL : HttpURLConnection.HTTP_OK, content.length - from);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(content, from, content.length - from);
            }
        });
        server.start();
        try {
            URI uri = new URI("http", null, server.getAddress().getHostString(), server.getAddress().getPort(), "/file.pdf", null, null);
            Page page = Page.of(HREF.builder().uri(uri).bytes((long) content.length).dateTime(LocalDateTime.now()).build());
            try (InputStream inputStream = page.inputStreamFrom(offset)) {
                assertArrayEquals(Arrays.copyOfRange(content, offset, content.length), inputStream.readAllBytes());
            }
            assertEquals(List.of("bytes=" + offset + "-"), List.copyOf(ranges));
        } finally {
            server.stop(0);
        }
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