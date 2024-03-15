package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

abstract class AbstractFileSourceTest {

    private static final URI FOO_URI = URI.create("http://localhost/path/");
    static final List<String> FILES = List.of(
            "files.html", "files.txt", "non-version.html",
            "non-version.txt", "versions.html", "versions.txt");

    String sourceFile;
    InputStream inputStream;
    byte[] buff;
    long bytes;
    LocalDateTime dateTime;
    String root;
    HREF href;
    Page page;


    Path root() {
        return Path.of("target/temp/root");
    }

    Path path() {
        return Path.of("target/temp/path", fileName().toString());
    }

    Path fileName() {
        return Path.of(sourceFile);
    }

    void setSourceFile(@NonNull String sourceFileName) throws IOException {
        this.sourceFile = sourceFileName;
        try (InputStream inputStream = openInputStream()) {
            this.buff = new byte[16384];
            this.bytes = inputStream == null ? 0 : inputStream.read(buff);
            this.dateTime = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).minusHours(1);
        }
        this.inputStream = this.openInputStream();
    }

    InputStream openInputStream() {
        return this.getClass().getClassLoader().getResourceAsStream(this.sourceFile);
    }

    void startUp(String fileName) throws IOException {
        setSourceFile(fileName != null ? fileName : FILES.get(new Random().nextInt(FILES.size())));

        this.root = root().toString();
        this.href = mock(HREF.class);
        this.page = spy(Page.of(href));

        doNothing().when(this.page).connect(anyLong());
        when(this.href.getUri()).thenReturn(FOO_URI);
        when(this.href.getBytes()).thenReturn(this.bytes);
        when(this.href.getDateTime()).thenReturn(this.dateTime);
        when(this.href.path()).thenReturn(path());
        when(this.href.name()).thenReturn(fileName());
        when(this.page.getHref()).thenReturn(this.href);
        when(this.page.inputStream()).thenReturn(this.inputStream);

    }

    void tearDown() {
        sourceFile = null;
        inputStream = null;
        buff = null;
        bytes = 0;
        dateTime = null;
        root = null;
        href = null;
        page = null;
    }

}