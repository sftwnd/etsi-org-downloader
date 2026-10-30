package com.github.sftwnd.etsiorg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunLockTest {

    @TempDir
    Path tempDir;

    @Test
    @SuppressWarnings("try")
    void lockFileIsCreatedAndKeptTest() throws IOException {
        Path file = this.tempDir.resolve("run.lock");
        try (RunLock ignored = RunLock.tryAcquire(file).orElseThrow()) {
            assertTrue(Files.isRegularFile(file));
        }
        assertTrue(Files.isRegularFile(file));
    }

    @Test
    @SuppressWarnings("try")
    void secondRunTest() throws IOException {
        Path file = this.tempDir.resolve("run.lock");
        try (RunLock ignored = RunLock.tryAcquire(file).orElseThrow()) {
            assertTrue(RunLock.tryAcquire(file).isEmpty());
            assertTrue(RunLock.tryAcquire(this.tempDir.resolve("folder").resolve("..").resolve("run.lock")).isEmpty());
        }
        RunLock.tryAcquire(file).orElseThrow().close();
    }

    @Test
    @SuppressWarnings("try")
    void otherFilesTest() throws IOException {
        try (RunLock ignored = RunLock.tryAcquire(this.tempDir.resolve("first.lock")).orElseThrow()) {
            RunLock.tryAcquire(this.tempDir.resolve("second.lock")).orElseThrow().close();
        }
    }

    @Test
    void missingFolderTest() throws IOException {
        Path file = this.tempDir.resolve("missing").resolve("run.lock");
        assertThrows(NoSuchFileException.class, () -> RunLock.tryAcquire(file));
        Files.createDirectory(file.getParent());
        RunLock.tryAcquire(file).orElseThrow().close();
    }

}
