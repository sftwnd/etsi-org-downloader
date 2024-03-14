package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * Page process helper
 */
@AllArgsConstructor
public class Page {
    
    @SneakyThrows
    public static Page of(@NonNull HREF href) {
        return new Page(Objects.requireNonNull(href, "Page::of - href is null"));
    }

    /**
     * Page URI
     */
    @Getter
    private HREF href;

    /**
     * Page content length
     */
    private Integer contentLength;

    /**
     * Page data stream
     */
    private InputStream inputStream;

    public Instant dateTime() {
        return Optional.ofNullable(href.getDateTime())
                .map(dateTime -> dateTime.atZone(ZoneId.systemDefault()))
                .map(ZonedDateTime::toInstant)
                .map(instant -> instant.truncatedTo(ChronoUnit.SECONDS))
                .orElse(null);
    }

    public int contentLength() throws IOException {
        if (this.contentLength != null) {
            return this.contentLength;
        } else if (href.getBytes() != null) {
            return href.getBytes().intValue();
        }
        connect();
        return this.contentLength;
    }

    public InputStream inputStream() throws IOException {
        connect();
        return this.inputStream;
    }
    
    /**
     * Full path to file (including file name) in the URI
     * @return Normalized full path to file (including file name)
     */
    public Path path() {
        return href.path();
    }

    /**
     * Name of file
     * @return name of file
     */
    public String fileName() {
        return href.name().toString();
    }

    /**
     * URI of file
     * @return uri of file
     */
    public URI getUri() {
        return href.getUri();
    }
    
    private void connect() throws IOException {
        if (this.inputStream == null) {
            URLConnection connection = href.getUri().toURL().openConnection();
            this.inputStream = connection.getInputStream();
            this.contentLength = connection.getContentLength();
        }
    }

    /**
     * Connect to URI resource and load resource properties
     * @param href file resource reference
     */
    private Page(@NonNull HREF href) {
        this.href = Objects.requireNonNull(href, "Loader::new - URI is null");
    }

    /**
     * Returns a string representation of the object.
     * @return  a string representation of the object.
     */
    @Override
    public String toString() {
        return "Path [ " + href +
                ", path: '" + path() + '\'' +
                ", fileName: '" + fileName() + '\'' +
                (this.contentLength == null ? "" : ", contentLength: " + this.contentLength) +
                " ]";
    }

}
