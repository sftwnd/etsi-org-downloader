package com.github.sftwnd.etsiorg;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileSaveProcessorTest extends AbstractFileSourceTest {

    private static final String FILE = "deliver/etsi_ts/129000_129099/129078/17.00.00_60/ts_129078v170000p.pdf";

    @TempDir
    Path tempDir;

    @Test
    void downloadTest() throws IOException {
        assertEquals(List.of(this.target), process());
        assertArrayEquals(content(), Files.readAllBytes(this.target));
        assertTrue(Files.notExists(this.part));
    }

    @Test
    void listingTimeIsUtcTest() throws IOException {
        process();
        assertEquals(this.dateTime.toInstant(ZoneOffset.UTC), Files.getLastModifiedTime(this.target).toInstant());
    }

    @Test
    void listingTimeZoneTest() throws IOException {
        ZoneId zone = ZoneId.of("Europe/Moscow");
        this.processor = new FileSaveProcessor(new Destination(root()), zone, this.page);
        process();
        assertEquals(this.dateTime.atZone(zone).toInstant(), Files.getLastModifiedTime(this.target).toInstant());
    }

    @Test
    void resumePartTest() throws IOException {
        int offset = length() / 2;
        Files.createDirectories(this.part.getParent());
        Files.write(this.part, Arrays.copyOf(this.buff, offset));
        when(this.page.inputStreamFrom(offset)).thenReturn(new ByteArrayInputStream(this.buff, offset, length() - offset));
        assertEquals(List.of(this.target), process());
        assertArrayEquals(content(), Files.readAllBytes(this.target));
        assertTrue(Files.notExists(this.part));
    }

    @Test
    void interruptedDownloadIsResumedTest() throws IOException {
        int offset = length() / 3;
        when(this.page.inputStreamFrom(0L)).thenReturn(new FailingInputStream(this.buff, offset));
        when(this.page.inputStreamFrom(offset)).thenReturn(new ByteArrayInputStream(this.buff, offset, length() - offset));
        assertEquals(List.of(this.target), process());
        assertArrayEquals(content(), Files.readAllBytes(this.target));
    }

    @Test
    void failedDownloadKeepsPartTest() throws IOException {
        int offset = length() / 3;
        when(this.page.inputStreamFrom(0L)).thenReturn(new FailingInputStream(this.buff, offset));
        when(this.page.inputStreamFrom(offset)).thenReturn(new ByteArrayInputStream(new byte[0]));
        assertEquals(List.of(), process());
        assertTrue(Files.notExists(this.target));
        assertArrayEquals(Arrays.copyOf(this.buff, offset), Files.readAllBytes(this.part));
    }

    @Test
    void failedDownloadKeepsExistingFileTest() throws IOException {
        byte[] old = "old content".getBytes(StandardCharsets.US_ASCII);
        Files.createDirectories(this.target.getParent());
        Files.write(this.target, old);
        when(this.page.inputStreamFrom(0L)).thenReturn(new FailingInputStream(this.buff, 0));
        assertEquals(List.of(), process());
        assertArrayEquals(old, Files.readAllBytes(this.target));
    }

    @Test
    void fileOfAnotherSizeIsReplacedTest() throws IOException {
        Files.createDirectories(this.target.getParent());
        Files.write(this.target, "old content".getBytes(StandardCharsets.US_ASCII));
        assertEquals(List.of(this.target), process());
        assertArrayEquals(content(), Files.readAllBytes(this.target));
    }

    @Test
    void upToDateFileIsKeptTest() throws IOException {
        byte[] local = new byte[length()];
        Files.createDirectories(this.target.getParent());
        Files.write(this.target, local);
        Files.setLastModifiedTime(this.target, FileTime.from(this.dateTime.toInstant(ZoneOffset.UTC)));
        clearInvocations(this.page);
        assertEquals(List.of(this.target), process());
        assertArrayEquals(local, Files.readAllBytes(this.target));
        verify(this.page, never()).inputStreamFrom(anyLong());
    }

    @Test
    void stalePartIsRemovedTest() throws IOException {
        Path stale = this.target.resolveSibling(this.target.getFileName() + ".200001010000" + FileSaveProcessor.PART_SUFFIX);
        Files.createDirectories(stale.getParent());
        Files.write(stale, new byte[length() / 2]);
        assertEquals(List.of(this.target), process());
        assertArrayEquals(content(), Files.readAllBytes(this.target));
        assertTrue(Files.notExists(stale));
        verify(this.page, never()).inputStreamFrom(length() / 2);
    }

    @Test
    void changedFileIsDownloadedAgainTest() throws IOException {
        Files.createDirectories(this.target.getParent());
        Files.write(this.target, new byte[length()]);
        Files.setLastModifiedTime(this.target, FileTime.from(this.dateTime.minusDays(3).minusMinutes(7).toInstant(ZoneOffset.UTC)));
        assertEquals(List.of(this.target), process());
        assertArrayEquals(content(), Files.readAllBytes(this.target));
        assertEquals(this.dateTime.toInstant(ZoneOffset.UTC), Files.getLastModifiedTime(this.target).toInstant());
    }

    @Test
    void zoneShiftIsResetTest() throws IOException {
        byte[] local = new byte[length()];
        Files.createDirectories(this.target.getParent());
        Files.write(this.target, local);
        Files.setLastModifiedTime(this.target, FileTime.from(this.dateTime.toInstant(ZoneOffset.ofHours(3))));
        clearInvocations(this.page);
        assertEquals(List.of(this.target), process());
        assertArrayEquals(local, Files.readAllBytes(this.target));
        assertEquals(this.dateTime.toInstant(ZoneOffset.UTC), Files.getLastModifiedTime(this.target).toInstant());
        verify(this.page, never()).inputStreamFrom(anyLong());
    }

    @Test
    void remoteLengthMismatchTest() throws IOException {
        doReturn(this.bytes, this.bytes + 1).when(this.page).contentLength();
        assertEquals(List.of(), process());
        assertTrue(Files.notExists(this.target));
        assertTrue(Files.notExists(this.part));
    }

    @Test
    void readIsBoundedByLengthTest() throws IOException {
        when(this.page.inputStreamFrom(0L)).thenReturn(new ByteArrayInputStream(this.buff));
        assertEquals(List.of(this.target), process());
        assertArrayEquals(content(), Files.readAllBytes(this.target));
    }

    @Test
    void longerPartIsDiscardedTest() throws IOException {
        Files.createDirectories(this.part.getParent());
        Files.write(this.part, new byte[length() + 1]);
        assertEquals(List.of(this.target), process());
        assertArrayEquals(content(), Files.readAllBytes(this.target));
    }

    @Test
    void directoryIsNotReplacedTest() throws IOException {
        Path file = Files.createDirectories(this.target).resolve("file.txt");
        Files.writeString(file, "keep me");
        assertEquals(List.of(), process());
        assertTrue(Files.isDirectory(this.target));
        assertTrue(Files.exists(file));
    }

    @Test
    void outsideDestinationTest() {
        when(this.href.getUri()).thenReturn(URI.create("http://localhost/../outside.pdf"));
        assertEquals(List.of(), process());
        assertTrue(Files.notExists(this.tempDir.resolve("outside.pdf")));
    }

    @Override
    Path root() {
        return this.tempDir.resolve("mirror");
    }

    @Override
    URI uri() {
        return URI.create("http://localhost/" + FILE);
    }

    @Override
    Path path() {
        return Path.of("/" + FILE);
    }

    private List<Path> process() {
        return this.processor.process().join().collect(Collectors.toList());
    }

    private int length() {
        return Long.valueOf(this.bytes).intValue();
    }

    private byte[] content() {
        return Arrays.copyOf(this.buff, length());
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

    @BeforeEach
    void startUp() throws IOException {
        super.startUp(null);
        this.target = root().resolve(FILE).toAbsolutePath();
        this.part = this.target.resolveSibling(this.target.getFileName() + "." + FileSaveProcessor.PART_STAMP.format(this.dateTime) + FileSaveProcessor.PART_SUFFIX);
        this.processor = new FileSaveProcessor(new Destination(root()), ZoneOffset.UTC, this.page);
    }

    @AfterEach
    @Override
    void tearDown() {
        this.target = null;
        this.part = null;
        this.processor = null;
        super.tearDown();
    }

    private FileSaveProcessor processor;
    private Path target;
    private Path part;

}
