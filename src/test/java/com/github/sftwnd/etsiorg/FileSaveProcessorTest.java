package com.github.sftwnd.etsiorg;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class FileSaveProcessorTest {

    @Test
    void processPathCheckTest() {
        var future = this.fileSaveProcessor.process();
        assertTrue(future::isDone);
        assertDoesNotThrow(future::join);
        assertEquals(List.of(tempFile), future.join().collect(Collectors.toList()));
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
    void getRoot() {
    }

    @Test
    void getPage() {
    }

    private FileSaveProcessor fileSaveProcessor;

    private final static String TEMPDIR_BASE = "target/fileSaveProcessorTest.test";
    private static final List<String> FILES = List.of(
            "files.html", "files.txt", "non-version.html",
            "non-version.txt", "versions.html", "versions.txt");
    String sourceFile;
    Path tempDir;
    Path tempFile;
    String root;
    HREF href;
    Page page;
    InputStream inputStream;
    byte[] buff;
    long bytes;
    LocalDateTime dateTime;

    @BeforeEach
    void startUp() throws IOException {
        this.sourceFile = FILES.get(new Random().nextInt(FILES.size()));
        try (InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(this.sourceFile)) {
            this.buff = new byte[16384];
            this.bytes = inputStream == null ? 0 : inputStream.read(buff);
            this.dateTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).minusHours(1);
        }
        this.inputStream = this.getClass().getClassLoader().getResourceAsStream(this.sourceFile);
        this.tempDir = Files.createDirectories(Path.of(TEMPDIR_BASE));
        this.tempFile = Files.createTempFile(tempDir, "fileName", "data");
        this.root = tempDir.getParent().toString();
        this.href = mock(HREF.class);
        this.page = spy(Page.of(href));
        doNothing().when(page).connect(anyLong());
        when(href.getBytes()).thenReturn(bytes);
        when(href.getDateTime()).thenReturn(dateTime);
        when(href.path()).thenReturn(Path.of(tempDir.getFileName().toString(), tempFile.getFileName().toString()));
        when(href.name()).thenReturn(tempFile.getFileName());
        when(page.getHref()).thenReturn(this.href);
        when(page.inputStream()).thenReturn(this.inputStream);
        this.fileSaveProcessor = mock(FileSaveProcessor.class);
        when(this.fileSaveProcessor.getPage()).thenReturn(page);
        when(this.fileSaveProcessor.getRoot()).thenReturn(root);
        when(this.fileSaveProcessor.process()).thenCallRealMethod();
    }

    @AfterEach
    void tearDown() throws IOException {
        try {
            Files.delete(this.tempFile);
        } finally {
            try {
                Files.delete(this.tempDir);
            } finally {
                this.tempFile = null;
                this.tempDir = null;
                this.root = null;
                this.page = null;
                this.inputStream = null;
                this.sourceFile = null;
                this.dateTime = null;
                this.href = null;
            }
        }

    }

}