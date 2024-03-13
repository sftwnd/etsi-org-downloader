package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
@Slf4j
public class Page {

    private final URI uri;
    private final String contentType;
    private final Charset charset;
    private final byte[] buff;

    private final Pattern CONTENT_TYPE_PATTERN = Pattern.compile("^\\s*(\\S+)\\s*(?:;\\s*(\\S+)\\s*=\\s*(\\S+)\\s*)?$");

    /**
     * Load and construct File by uri
     * @param uri URI to file
     * @throws IOException if an error occurs
     */
    public Page(@NonNull URI uri) throws IOException {
        this.uri = uri;
        URLConnection connection = Objects.requireNonNull(uri, "Page::new - URI is null")
                .toURL()
                .openConnection();
        String contentType = connection.getContentType();
        Matcher matcher = CONTENT_TYPE_PATTERN.matcher(contentType);
        if (matcher.matches()) {
            this.contentType = matcher.group(1);
            if ("charset".equalsIgnoreCase(matcher.group(2))) {
                this.charset = Charset.forName(matcher.group(3));
            } else {
                this.charset = null;
            }
        } else {
            this.contentType = contentType;
            this.charset = null;
        }
        this.buff = new byte[connection.getContentLength()];
        connection.connect();
        try (InputStream inputStream = connection.getInputStream()) {
            for (int readed = 0; readed < buff.length; ) {
                readed += inputStream.read(buff, readed, buff.length - readed);
            }
        }
    }

    /**
     * Normalized full path to file (including file name) in the URI
     * @return Normalized full path to file (including file name)
     */
    public Path path() {
        return Path.of(uri.getPath()).normalize();
    }

    /**
     * Name of file
     * @return name of file
     */
    public String fileName() {
        return path().getFileName().toString();
    }

    /**
     * Load the page synchronously
     * @param uri URI fo file
     * @return loaded file
     */
    @SneakyThrows
    private static Page load(@NonNull URI uri) {
        Page page = new Page(uri);
        logger.trace("Loaded page: {}", uri.getPath());
        return page;
    }

    /**
     * Start to load file asynchronously
     * @param uri URI to file
     * @return CompletableFuture for the file load
     */
    public static CompletableFuture<Page> loadAsync(@NonNull URI uri, @Nullable Executor executor) {
        logger.trace("Start to load async: {}", uri.getPath());
        return executor == null
                ? CompletableFuture.supplyAsync(() -> load(uri))
                : CompletableFuture.supplyAsync(() -> load(uri), executor);
    }

    /**
     * Returns a string representation of the object.
     * @return  a string representation of the object.
     */
    @Override
    public String toString() {
        return "Path [path: '" + path() + '\'' +
                ", length: " + buff.length +
                ", contentType: '" + contentType + '\'' +
                ", fileName: '" + fileName() + '\'' +
                ( charset == null ? "" : ", charset: '" + charset + '\'' ) +
                " ]";
    }

}
