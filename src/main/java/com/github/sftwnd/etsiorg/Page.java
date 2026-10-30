package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.EOFException;
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

    /**
     * Request resource creation date
     * @return date of resource creation
     */
    public Instant dateTime() {
        return Optional.ofNullable(href.getDateTime())
                .map(dateTime -> dateTime.atZone(ZoneId.systemDefault()))
                .map(ZonedDateTime::toInstant)
                .map(instant -> instant.truncatedTo(ChronoUnit.SECONDS))
                .orElse(null);
    }

    /**
     * Size of file resource if defined in HREF or contentLength from HTTP Connection
     * @return file resource size
     * @throws IOException in the case of error
     */
    public long contentLength() throws IOException {
        if (this.contentLength != null) {
            return this.contentLength;
        } else if (href.getBytes() != null) {
            return href.getBytes();
        }
        connect(0L);
        return this.contentLength;
    }

    /**
     * InputStream of resource if connected
     * @return actual resource stream
     * @throws IOException in the case of error
     */
    public InputStream inputStream() throws IOException {
        return inputStreamFrom(0L);
    }

    /**
     * InputStream of resource from the offset
     * @param offset position in the resource to read from, connection is reopened for the positive offset
     * @return resource stream positioned at the offset
     * @throws IOException in the case of error
     */
    public InputStream inputStreamFrom(long offset) throws IOException {
        connect(offset);
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
     * Make HttpURLConnection if is not connected or reconnect from the positive offset.
     * The bytes before the offset are skipped if the server ignores the Range request
     * @param offset position in the resource to read from
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
                    logger.warn("Accept-Ranges was not defined for connection with offset request for: '{}'", Printable.of(href.path()));
                }
                connection.setRequestProperty("Range", "bytes=" + offset + "-");
            }
            this.connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                this.contentLength = connection.getContentLengthLong();
                Optional.ofNullable(href.getBytes())
                        .filter(Predicate.not(this.contentLength::equals))
                        .ifPresent(bytes -> logger.warn("The length of the file: {} is different from the declared size {} of: '{}'", this.contentLength, bytes, Printable.of(href.path())));
                this.acceptRange = Optional.ofNullable(connection.getHeaderField("Accept-Ranges")).map("bytes"::equals).orElse(false);
                this.inputStream = connection.getInputStream();
                if (offset > 0L) {
                    logger.warn("Range request was ignored, {} bytes will be skipped for: '{}'", offset, Printable.of(href.path()));
                    skipFully(this.inputStream, offset);
                }
            } else if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                this.inputStream = connection.getInputStream();
            } else {
                throw new IOException("Unable to open HTTP connection: " + Printable.of(connection.getHeaderField(0)));
            }
        }
    }

    /**
     * Skip exactly the given number of bytes in the stream
     * @param inputStream stream to skip the bytes in
     * @param bytes number of bytes to skip
     * @throws IOException if the stream ends before the bytes are skipped or in the case of error
     */
    private static void skipFully(@NonNull InputStream inputStream, long bytes) throws IOException {
        for (long remaining = bytes; remaining > 0; ) {
            long skipped = inputStream.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
            } else if (inputStream.read() >= 0) {
                remaining--;
            } else {
                throw new EOFException("Unable to skip " + bytes + " bytes: end of stream after " + (bytes - remaining));
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
        return "Path [ " + this.getHref() +
                ", path: '" + this.path() + '\'' +
                ", fileName: '" + this.fileName() + '\'' +
                (this.contentLength == null ? "" : ", contentLength: " + this.contentLength) +
                " ]";
    }

}
