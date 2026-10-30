package com.github.sftwnd.etsiorg;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void errorStatusLineIsEscapedTest() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread server = new Thread(() -> {
                try (Socket socket = serverSocket.accept()) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
                    for (String line = reader.readLine(); line != null && !line.isEmpty(); line = reader.readLine()) {
                        // skip the request headers
                    }
                    socket.getOutputStream().write(
                            "HTTP/1.1 403 Forbidden\u001B[2J\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
                } catch (IOException ignore) {
                }
            });
            server.start();
            URI uri = new URI("http", null, serverSocket.getInetAddress().getHostAddress(), serverSocket.getLocalPort(), "/file.pdf", null, null);
            Page page = Page.of(HREF.builder().uri(uri).bytes(1L).dateTime(LocalDateTime.now()).build());
            IOException exception = assertThrows(IOException.class, page::inputStream);
            assertEquals("Unable to open HTTP connection: HTTP/1.1 403 Forbidden\\u001B[2J", exception.getMessage());
            server.join(5_000L);
        }
    }

    @Test
    void readTimeoutTest() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Page page = Page.of(href(uri(serverSocket.getLocalPort())),
                    new HttpSettings(Duration.ofSeconds(2), Duration.ofMillis(200), 1, Duration.ZERO));
            long start = System.nanoTime();
            assertThrows(SocketTimeoutException.class, page::inputStream);
            assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofSeconds(5)) < 0);
        }
    }

    @Test
    void retryTransientStatusTest() throws Exception {
        assertResponses(List.of(HttpURLConnection.HTTP_UNAVAILABLE, HttpURLConnection.HTTP_OK), 2,
                page -> assertArrayEquals(CONTENT, page.inputStream().readAllBytes()));
    }

    @Test
    void attemptsAreLimitedTest() throws Exception {
        assertResponses(List.of(HttpURLConnection.HTTP_INTERNAL_ERROR), 3, page -> {
            IOException exception = assertThrows(IOException.class, page::inputStream);
            assertTrue(exception.getMessage().contains("500"), exception.getMessage());
        });
    }

    @Test
    void clientErrorIsNotRetriedTest() throws Exception {
        assertResponses(List.of(HttpURLConnection.HTTP_NOT_FOUND), 1, page -> assertThrows(IOException.class, page::inputStream));
    }

    @Test
    void reconnectAfterFailureTest() throws Exception {
        assertResponses(List.of(HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_OK), 2, page -> {
            assertThrows(IOException.class, page::inputStream);
            assertArrayEquals(CONTENT, page.inputStream().readAllBytes());
        });
    }

    @Test
    void retryAfterTest() throws Exception {
        HttpServer server = server(List.of(HttpURLConnection.HTTP_UNAVAILABLE, HttpURLConnection.HTTP_OK), "text/plain", "1", new AtomicInteger());
        try {
            Page page = Page.of(href(uri(server.getAddress().getPort())), SETTINGS);
            long start = System.nanoTime();
            assertArrayEquals(CONTENT, page.inputStream().readAllBytes());
            assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofMillis(900)) >= 0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void userAgentTest() throws Exception {
        Collection<String> userAgents = new ConcurrentLinkedQueue<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            userAgents.add(String.valueOf(exchange.getRequestHeaders().getFirst("User-Agent")));
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, CONTENT.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(CONTENT);
            }
        });
        server.start();
        try {
            try (InputStream inputStream = Page.of(href(uri(server.getAddress().getPort())), SETTINGS).inputStream()) {
                inputStream.readAllBytes();
            }
            assertEquals(List.of(Page.USER_AGENT), List.copyOf(userAgents));
            assertTrue(Page.USER_AGENT.startsWith("etsiorg-downloader "));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void partialResponseLengthTest() throws Exception {
        assertPartialResponse("bytes 12-35/36", 1, page -> {
            try (InputStream inputStream = page.inputStreamFrom(12)) {
                assertArrayEquals(Arrays.copyOfRange(CONTENT, 12, CONTENT.length), inputStream.readAllBytes());
            }
            assertEquals(CONTENT.length, page.contentLength());
        });
    }

    @Test
    void partialResponseUnknownLengthTest() throws Exception {
        assertPartialResponse("bytes 12-35/*", 1, page -> {
            page.inputStreamFrom(12).close();
            assertEquals(CONTENT.length, page.contentLength());
        });
    }

    @Test
    void partialResponseFromAnotherOffsetTest() throws Exception {
        assertPartialResponse("bytes 0-23/36", 1, page -> assertThrows(ProtocolException.class, () -> page.inputStreamFrom(12)));
    }

    @Test
    void partialResponseWithoutRangeTest() throws Exception {
        assertPartialResponse(null, 1, page -> assertThrows(ProtocolException.class, () -> page.inputStreamFrom(12)));
    }

    private void assertPartialResponse(String contentRange, int expectedRequests, PageAssertion assertion) throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            if (contentRange != null) {
                exchange.getResponseHeaders().add("Content-Range", contentRange);
            }
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_PARTIAL, CONTENT.length - 12);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(CONTENT, 12, CONTENT.length - 12);
            }
        });
        server.start();
        try {
            assertion.accept(Page.of(href(uri(server.getAddress().getPort())), SETTINGS));
            assertEquals(expectedRequests, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void charsetTest() throws Exception {
        assertEquals(Charset.forName("windows-1251"), charsetOf("text/html; charset=windows-1251"));
        assertEquals(StandardCharsets.ISO_8859_1, charsetOf("text/html;Charset=\"ISO-8859-1\""));
        assertEquals(StandardCharsets.UTF_8, charsetOf("text/html"));
        assertEquals(StandardCharsets.UTF_8, charsetOf("text/html; charset=unknown-charset"));
        assertEquals(StandardCharsets.UTF_8, Page.of(href(uri(1))).charset());
    }

    @Test
    void notHttpResourceTest() {
        Page page = Page.of(href(URI.create("file:///etc/passwd")));
        IOException exception = assertThrows(IOException.class, page::inputStream);
        assertTrue(exception.getMessage().startsWith("Resource is not available over HTTP"), exception.getMessage());
    }

    @Test
    void invalidSettingsTest() {
        assertThrows(IllegalArgumentException.class, () -> new HttpSettings(Duration.ZERO, Duration.ofSeconds(1), 1, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new HttpSettings(Duration.ofSeconds(1), Duration.ofSeconds(-1), 1, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new HttpSettings(Duration.ofSeconds(1), Duration.ofSeconds(1), 0, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new HttpSettings(Duration.ofSeconds(1), Duration.ofSeconds(1), 1, Duration.ofSeconds(-1)));
    }

    private interface PageAssertion {
        void accept(Page page) throws Exception;
    }

    private static final byte[] CONTENT = "0123456789abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.US_ASCII);
    private static final HttpSettings SETTINGS = new HttpSettings(Duration.ofSeconds(2), Duration.ofSeconds(2), 3, Duration.ofMillis(10));

    private void assertResponses(List<Integer> codes, int expectedRequests, PageAssertion assertion) throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(codes, "text/plain", null, requests);
        try {
            assertion.accept(Page.of(href(uri(server.getAddress().getPort())), SETTINGS));
            assertEquals(expectedRequests, requests.get());
        } finally {
            server.stop(0);
        }
    }

    private Charset charsetOf(String contentType) throws Exception {
        HttpServer server = server(List.of(HttpURLConnection.HTTP_OK), contentType, null, new AtomicInteger());
        try {
            Page page = Page.of(href(uri(server.getAddress().getPort())), SETTINGS);
            try (InputStream inputStream = page.inputStream()) {
                inputStream.readAllBytes();
            }
            return page.charset();
        } finally {
            server.stop(0);
        }
    }

    /**
     * Local server that answers with the codes in turn, the last code is repeated
     */
    private static HttpServer server(List<Integer> codes, String contentType, String retryAfter, AtomicInteger requests) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            int code = codes.get(Math.min(requests.getAndIncrement(), codes.size() - 1));
            exchange.getResponseHeaders().add("Content-Type", contentType);
            if (retryAfter != null && code != HttpURLConnection.HTTP_OK) {
                exchange.getResponseHeaders().add("Retry-After", retryAfter);
            }
            byte[] body = code == HttpURLConnection.HTTP_OK ? CONTENT : new byte[0];
            exchange.sendResponseHeaders(code, body.length == 0 ? -1 : body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();
        return server;
    }

    private static URI uri(int port) {
        return URI.create("http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":" + port + "/file.pdf");
    }

    private static HREF href(URI uri) {
        return HREF.builder().uri(uri).bytes((long) CONTENT.length).dateTime(LocalDateTime.now()).build();
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
            if (from > 0) {
                exchange.getResponseHeaders().add("Content-Range", "bytes " + from + "-" + (content.length - 1) + "/" + content.length);
            }
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