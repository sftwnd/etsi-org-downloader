package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Page process helper
 */
@Slf4j
@AllArgsConstructor
public class Page {

    private static final String USER_AGENT = "Mozilla/5.0";

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
     * Page data connection
     */
    private HttpURLConnection connection;

    /**
     * Page data stream
     */
    private InputStream inputStream;

    /**
     * Page content length
     */
    private Long contentLength;

    /**
     * Accept range flag
     */
    private boolean acceptRange = true;

    public Instant dateTime() {
        return Optional.ofNullable(href.getDateTime())
                .map(dateTime -> dateTime.atZone(ZoneId.systemDefault()))
                .map(ZonedDateTime::toInstant)
                .map(instant -> instant.truncatedTo(ChronoUnit.SECONDS))
                .orElse(null);
    }

    public long contentLength() throws IOException {
        if (this.contentLength != null) {
            return this.contentLength;
        } else if (href.getBytes() != null) {
            return href.getBytes();
        }
        connect(0L);
        return this.contentLength;
    }

    public InputStream inputStream() throws IOException {
        connect(0L);
        return this.inputStream;
    }

    /**
     * Full path to file (including file name) in the URI
     *
     * @return Normalized full path to file (including file name)
     */
    public Path path() {
        return href.path();
    }

    /**
     * Name of file
     *
     * @return name of file
     */
    public String fileName() {
        return href.name().toString();
    }

    /**
     * URI of file
     *
     * @return uri of file
     */
    public URI getUri() {
        return href.getUri();
    }

    /**
     * Make HttpURLConnection with offset if is not connected
     * @throws IOException if an exception
     */
    public void connect(long offset) throws IOException {
        if (this.connection != null && offset > 0) {
            this.inputStream.close();
            this.connection.disconnect();
            this.connection = null;
            this.inputStream = null;
        }
        if (this.connection == null) {
            this.connection = (HttpURLConnection) href.getUri().toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            if (offset > 0L) {
                if(! this.acceptRange) {
                    logger.warn("Accept-Ranges was not defined for connection with offset request for: '{}'", href.path());
                }
                connection.setRequestProperty("Range", "bytes=" + offset + "-" + (this.contentLength - 1));
            }
            this.connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                this.contentLength = connection.getContentLengthLong();
                Optional.ofNullable(href.getBytes())
                        .filter(Predicate.not(this.contentLength::equals))
                        .ifPresent(bytes -> logger.warn("The length of the file: {} is different from the declared size {} of: '{}'", this.contentLength, bytes, href.path()));
                this.acceptRange = Optional.ofNullable(connection.getHeaderField("Accept-Ranges")).map("bytes"::equals).orElse(false);
                this.inputStream = connection.getInputStream();
            } else if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                this.inputStream = connection.getInputStream();
            } else {
                throw new IOException("Unable to open HTTP connection: " + connection.getHeaderField(0));
            }
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
