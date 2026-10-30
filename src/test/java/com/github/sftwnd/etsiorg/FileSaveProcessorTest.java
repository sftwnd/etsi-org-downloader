package com.github.sftwnd.etsiorg;

import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileSaveProcessorTest extends AbstractFileSourceTest {

    @Test
    void processPathCheckTest() {
        var future = this.fileSaveProcessor.process();
        assertTrue(future::isDone);
        assertDoesNotThrow(future::join);
        assertEquals(List.of(tempFile.toAbsolutePath()), future.join().collect(Collectors.toList()));
    }

    @Test
    void processOutsideDestinationTest() {
        Path outside = root().resolve("..").resolve(tempFile.getFileName()).toAbsolutePath().normalize();
        when(this.href.getUri()).thenReturn(URI.create("http://localhost/../" + tempFile.getFileName()));
        assertEquals(List.of(), this.fileSaveProcessor.process().join().collect(Collectors.toList()));
        assertTrue(Files.notExists(outside));
    }

    @Test
    void processFileContentTest() throws IOException {
        Path path = this.fileSaveProcessor.process().join().findFirst().orElse(null);
        assertNotNull(path);
        assertEquals(this.bytes, Files.size(path));
        int fileSize = Long.valueOf(bytes).intValue();
        byte[] buff = new byte[fileSize];
        try (InputStream is = Files.newInputStream(path)) {
            assertEquals(fileSize, is.read(buff));
        }
        assertEquals(0, Arrays.compare(this.buff, 0, fileSize, buff, 0, fileSize));
    }

    @Test
    void processResumeFileContentTest() throws IOException {
        int fileSize = Long.valueOf(this.bytes).intValue();
        int offset = fileSize / 2;
        Files.write(this.tempFile, Arrays.copyOf(this.buff, offset));
        when(this.page.inputStreamFrom(offset)).thenReturn(new ByteArrayInputStream(this.buff, offset, fileSize - offset));
        Path path = this.fileSaveProcessor.process().join().findFirst().orElse(null);
        assertNotNull(path);
        assertArrayEquals(Arrays.copyOf(this.buff, fileSize), Files.readAllBytes(path));
    }

    @Test
    void listingTimeIsUtcTest() throws IOException {
        Path path = this.fileSaveProcessor.process().join().findFirst().orElseThrow();
        assertEquals(this.dateTime.toInstant(ZoneOffset.UTC), Files.getLastModifiedTime(path).toInstant());
    }

    @Test
    void listingTimeZoneTest() throws IOException {
        ZoneId zone = ZoneId.of("Europe/Moscow");
        when(this.fileSaveProcessor.getZone()).thenReturn(zone);
        Path path = this.fileSaveProcessor.process().join().findFirst().orElseThrow();
        assertEquals(this.dateTime.atZone(zone).toInstant(), Files.getLastModifiedTime(path).toInstant());
    }

    @Test
    void processInterruptedDownloadTest() throws IOException {
        int fileSize = Long.valueOf(this.bytes).intValue();
        int offset = fileSize / 3;
        when(this.page.inputStreamFrom(0L)).thenReturn(new FailingInputStream(this.buff, offset));
        when(this.page.inputStreamFrom(offset)).thenReturn(new ByteArrayInputStream(this.buff, offset, fileSize - offset));
        Path path = this.fileSaveProcessor.process().join().findFirst().orElse(null);
        assertNotNull(path);
        assertArrayEquals(Arrays.copyOf(this.buff, fileSize), Files.readAllBytes(path));
    }

    /**
     * Stream that fails with a timeout after the given number of bytes
     */
    private static class FailingInputStream extends InputStream {

        private final byte[] buff;
        private final int failAt;
        private int position;

        FailingInputStream(byte[] buff, int failAt) {
            this.buff = buff;
            this.failAt = failAt;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xFF;
        }

        @Override
        public int read(byte[] target, int offset, int length) throws IOException {
            if (this.position >= this.failAt) {
                throw new SocketTimeoutException("Read timed out");
            }
            int count = Math.min(length, this.failAt - this.position);
            System.arraycopy(this.buff, this.position, target, offset, count);
            this.position += count;
            return count;
        }
    }

    private FileSaveProcessor fileSaveProcessor;

    private final static String TEMPDIR_BASE = "target/fileSaveProcessorTest.test";
    Path tempDir;
    Path tempFile;

    @Override
    Path root() {
        return tempDir.getParent();
    }

    @Override
    Path path() {
        return Path.of(tempDir.getFileName().toString(), tempFile.getFileName().toString());
    }

    @Override
    Path fileName() {
        return tempFile.getFileName();
    }

    @Override
    URI uri() {
        return URI.create("http://localhost/" + tempDir.getFileName() + "/" + tempFile.getFileName());
    }

    @BeforeEach
    void startUp() throws IOException {
        this.tempDir = Files.createDirectories(Path.of(TEMPDIR_BASE));
        this.tempFile = Files.createTempFile(tempDir, "fileName", "data");

        super.startUp(null);

        this.fileSaveProcessor = mock(FileSaveProcessor.class);
        when(this.fileSaveProcessor.getPage()).thenReturn(this.page);
        when(this.fileSaveProcessor.getDestination()).thenReturn(new Destination(root()));
        when(this.fileSaveProcessor.getZone()).thenReturn(ZoneOffset.UTC);
        when(this.fileSaveProcessor.process()).thenCallRealMethod();
    }

    @AfterEach
    @Override
    @SneakyThrows
    void tearDown() {
        try {
            Files.delete(this.tempFile);
        } finally {
            try {
                Files.delete(this.tempDir);
            } finally {
                this.tempFile = null;
                this.tempDir = null;
                super.tearDown();
            }
        }
    }

}