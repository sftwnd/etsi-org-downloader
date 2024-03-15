package com.github.sftwnd.etsiorg;

import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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

    @BeforeEach
    void startUp() throws IOException {
        this.tempDir = Files.createDirectories(Path.of(TEMPDIR_BASE));
        this.tempFile = Files.createTempFile(tempDir, "fileName", "data");

        super.startUp(null);

        this.fileSaveProcessor = mock(FileSaveProcessor.class);
        when(this.fileSaveProcessor.getPage()).thenReturn(this.page);
        when(this.fileSaveProcessor.getRoot()).thenReturn(this.root);
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