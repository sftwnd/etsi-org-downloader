package com.github.sftwnd.etsiorg;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ExpiredRemoverTest {

    private static final URI SITE = URI.create("https://www.etsi.org/");
    private static final String VERSION = "deliver/etsi_ts/129000_129099/129011/16.00.00_60/";

    @TempDir
    Path tempDir;

    @Test
    void removeVersionTreeTest() throws IOException {
        Path version = this.mirror.resolve(VERSION);
        Files.createDirectories(version.resolve("nested"));
        Files.writeString(version.resolve("ts.pdf"), "pdf");
        Files.writeString(version.resolve("nested").resolve("ts.zip"), "zip");
        this.remover.accept(List.of(SITE.resolve(VERSION)));
        assertTrue(Files.notExists(version));
        assertTrue(Files.exists(version.getParent()));
    }

    @Test
    void linkInsideVersionIsNotFollowedTest() throws IOException {
        Path version = Files.createDirectories(this.mirror.resolve(VERSION));
        Files.writeString(version.resolve("ts.pdf"), "pdf");
        assumeTrue(link(version.resolve("docs"), this.outside));
        this.remover.accept(List.of(SITE.resolve(VERSION)));
        assertTrue(Files.notExists(version, LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.exists(this.outsideFile));
    }

    @Test
    void expiredLinkIsNotFollowedTest() throws IOException {
        Path version = this.mirror.resolve(VERSION);
        Files.createDirectories(version.getParent());
        assumeTrue(link(version, this.outside));
        this.remover.accept(List.of(SITE.resolve(VERSION)));
        assertTrue(Files.notExists(version, LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.exists(this.outsideFile));
    }

    @Test
    void danglingLinkIsRemovedTest() throws IOException {
        Path version = this.mirror.resolve(VERSION);
        Files.createDirectories(version.getParent());
        assumeTrue(link(version, this.tempDir.resolve("missing")));
        this.remover.accept(List.of(SITE.resolve(VERSION)));
        assertTrue(Files.notExists(version, LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void pathOutsideDestinationIsSkippedTest() {
        this.remover.accept(List.of(SITE.resolve("/../outside/")));
        assertTrue(Files.exists(this.outsideFile));
    }

    @Test
    void missingPathTest() {
        assertDoesNotThrow(() -> this.remover.accept(List.of(SITE.resolve(VERSION))));
    }

    @Test
    void nullDestinationTest() {
        assertThrows(NullPointerException.class, () -> new ExpiredRemover(null));
    }

    @BeforeEach
    void startUp() throws IOException {
        this.mirror = Files.createDirectories(this.tempDir.resolve("mirror"));
        this.outside = Files.createDirectories(this.tempDir.resolve("outside"));
        this.outsideFile = Files.writeString(this.outside.resolve("important.txt"), "keep me");
        this.remover = new ExpiredRemover(new Destination(this.mirror));
    }

    private static boolean link(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (UnsupportedOperationException | IOException exception) {
            return false;
        }
    }

    private Path mirror;
    private Path outside;
    private Path outsideFile;
    private ExpiredRemover remover;

}
