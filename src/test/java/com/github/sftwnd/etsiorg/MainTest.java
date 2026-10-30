package com.github.sftwnd.etsiorg;

import com.sun.net.httpserver.HttpServer;
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
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MainTest {

    private static final String FOLDER = "/deliver/etsi_ts/129000_129099/129078/17.00.00_60/";
    private static final Map<String, Integer> FILES = Map.of(
            FOLDER + "ts_129078v170000p.pdf", 932564,
            FOLDER + "ts_129078v170000p0.zip", 38692);

    @TempDir
    Path tempDir;

    private final AtomicInteger requests = new AtomicInteger();

    @Test
    void runTest() throws Exception {
        AtomicInteger fileRequests = new AtomicInteger();
        HttpServer server = server(true, fileRequests);
        try {
            Map<String, String> properties = properties(server);
            assertEquals(Main.SUCCESS, run(properties, Map.of()));
            for (Map.Entry<String, Integer> file : FILES.entrySet()) {
                assertEquals((long) file.getValue(), Files.size(this.tempDir.resolve(file.getKey().substring(1))));
            }
            assertEquals(2, fileRequests.get());
            assertEquals(Main.SUCCESS, run(properties, Map.of()));
            assertEquals(2, fileRequests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void runWithEnvironmentTest() throws Exception {
        AtomicInteger fileRequests = new AtomicInteger();
        HttpServer server = server(true, fileRequests);
        try {
            Map<String, String> environment = new HashMap<>();
            properties(server).forEach((property, value) -> environment.put("ETSIORG_" + property.toUpperCase(Locale.ROOT), value));
            assertEquals(Main.SUCCESS, run(Map.of(), environment));
            assertEquals(2, fileRequests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void runFailureTest() throws Exception {
        HttpServer server = server(false, new AtomicInteger());
        try {
            assertEquals(Main.FAILURE, run(properties(server), Map.of()));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void runInvalidConfigurationTest() {
        assertEquals(Main.INVALID_CONFIGURATION, run(Map.of("uri", "ftp://www.etsi.org/deliver/"), Map.of()));
        assertEquals(Main.INVALID_CONFIGURATION, run(Map.of("threads", "none"), Map.of()));
        assertEquals(Main.INVALID_CONFIGURATION, run(Map.of(), Map.of("ETSIORG_ZONE", "Mars/Olympus")));
    }

    @Test
    void readOnlyDestinationTest() throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        HttpServer server = server(true, new AtomicInteger());
        Path dest = Files.createDirectory(this.tempDir.resolve("read-only"));
        Files.setPosixFilePermissions(dest, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            assumeFalse(Files.isWritable(dest), "the superuser writes to a read-only folder");
            Map<String, String> properties = new HashMap<>(properties(server));
            properties.put("dest", dest.toString());
            assertEquals(Main.INVALID_CONFIGURATION, run(properties, Map.of()));
            assertEquals(0, this.requests.get());
        } finally {
            Files.setPosixFilePermissions(dest, PosixFilePermissions.fromString("rwxr-xr-x"));
            server.stop(0);
        }
    }

    @Test
    @SuppressWarnings("try")
    void lockedTest() throws Exception {
        AtomicInteger fileRequests = new AtomicInteger();
        HttpServer server = server(true, fileRequests);
        try {
            try (RunLock ignored = RunLock.tryAcquire(this.tempDir.resolve(".etsiorg-downloader.lock")).orElseThrow()) {
                assertEquals(Main.LOCKED, run(properties(server), Map.of()));
                assertEquals(0, this.requests.get());
            }
            assertEquals(Main.SUCCESS, run(properties(server), Map.of()));
            assertEquals(2, fileRequests.get());
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"-h", "--help"})
    void helpTest(String argument) {
        assertEquals(Main.SUCCESS, Main.run(new String[] {argument}, name -> null, name -> null));
    }

    @Test
    void helpTextTest() {
        String help = Main.help();
        assertTrue(help.startsWith("Usage: etsiorg-downloader [-h | --help]"), help);
        assertTrue(help.contains(Settings.help()), help);
        assertTrue(help.contains("0 - no failures, 1 - some resources have failed, 2 - invalid configuration, "
                + "3 - another run mirrors to the destination folder"), help);
    }

    @ParameterizedTest
    @ValueSource(strings = {"help", "--uri=https://www.etsi.org/deliver/", "--help --verbose"})
    void unknownArgumentsTest(String arguments) {
        assertEquals(Main.INVALID_CONFIGURATION, Main.run(arguments.split(" "), name -> null, name -> null));
    }

    private static int run(Map<String, String> properties, Map<String, String> environment) {
        return Main.run(new String[0], properties::get, environment::get);
    }

    private Map<String, String> properties(HttpServer server) {
        return Map.of(
                "uri", "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + FOLDER,
                "dest", this.tempDir.toString(),
                "threads", "2");
    }

    /**
     * Local site with the listing {@code files.html} sent in chunks and its two files, or with nothing at all
     */
    private HttpServer server(boolean available, AtomicInteger fileRequests) throws IOException {
        byte[] listing;
        try (InputStream inputStream = MainTest.class.getClassLoader().getResourceAsStream("files.html")) {
            assertNotNull(inputStream);
            listing = inputStream.readAllBytes();
        }
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            this.requests.incrementAndGet();
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
