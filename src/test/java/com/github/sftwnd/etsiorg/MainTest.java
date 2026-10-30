package com.github.sftwnd.etsiorg;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MainTest {

    private static final String FOLDER = "/deliver/etsi_ts/129000_129099/129078/17.00.00_60/";
    private static final Map<String, Integer> FILES = Map.of(
            FOLDER + "ts_129078v170000p.pdf", 932564,
            FOLDER + "ts_129078v170000p0.zip", 38692);

    @TempDir
    Path tempDir;

    @Test
    void defaultThreadsTest() {
        assertEquals(4, Main.threads(null));
        assertEquals(4, Main.threads(" "));
    }

    @Test
    void threadsTest() {
        assertEquals(2, Main.threads("2"));
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

    @Test
    void runTest() throws Exception {
        AtomicInteger fileRequests = new AtomicInteger();
        HttpServer server = server(true, fileRequests);
        try {
            configure(server);
            assertEquals(Main.SUCCESS, Main.run());
            for (Map.Entry<String, Integer> file : FILES.entrySet()) {
                assertEquals((long) file.getValue(), Files.size(this.tempDir.resolve(file.getKey().substring(1))));
            }
            assertEquals(2, fileRequests.get());
            assertEquals(Main.SUCCESS, Main.run());
            assertEquals(2, fileRequests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void runFailureTest() throws Exception {
        HttpServer server = server(false, new AtomicInteger());
        try {
            configure(server);
            assertEquals(Main.FAILURE, Main.run());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void runInvalidConfigurationTest() {
        System.setProperty("uri", "ftp://www.etsi.org/deliver/");
        assertEquals(Main.INVALID_CONFIGURATION, Main.run());
        System.setProperty("uri", "https://www.etsi.org/deliver/");
        System.setProperty("threads", "none");
        assertEquals(Main.INVALID_CONFIGURATION, Main.run());
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("uri");
        System.clearProperty("dest");
        System.clearProperty("threads");
        System.clearProperty("zone");
    }

    private void configure(HttpServer server) {
        System.setProperty("uri", "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + FOLDER);
        System.setProperty("dest", this.tempDir.toString());
        System.setProperty("threads", "2");
    }

    /**
     * Local site with the listing {@code files.html} sent in chunks and its two files, or with nothing at all
     */
    private static HttpServer server(boolean available, AtomicInteger fileRequests) throws IOException {
        byte[] listing;
        try (InputStream inputStream = MainTest.class.getClassLoader().getResourceAsStream("files.html")) {
            assertNotNull(inputStream);
            listing = inputStream.readAllBytes();
        }
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            Integer size = FILES.get(path);
            if (available && FOLDER.equals(path)) {
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(listing);
                }
            } else if (available && size != null) {
                fileRequests.incrementAndGet();
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, size);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(new byte[size]);
                }
            } else {
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, -1);
                exchange.close();
            }
        });
        server.start();
        return server;
    }

}
