package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Page process helper
 */
@Slf4j
public class Page {

    private static final String USER_AGENT = "Mozilla/5.0";

    /**
     * Charset parameter of the Content-Type header
     */
    private static final Pattern CHARSET = Pattern.compile(";\\s*charset\\s*=\\s*\"?([^\";\\s]+)", Pattern.CASE_INSENSITIVE);

    /**
     * Seconds of the Retry-After header
     */
    private static final Pattern RETRY_AFTER_SECONDS = Pattern.compile("\\s*(\\d{1,9})\\s*");

    /**
     * Maximal delay requested with the Retry-After header that is respected
     */
    private static final Duration MAX_RETRY_AFTER = Duration.ofMinutes(1);

    /**
     * Page of the resource with the default HTTP settings
     * @param href resource reference
     * @return page of the resource
     */
    public static Page of(@NonNull HREF href) {
        return of(href, HttpSettings.DEFAULT);
    }

    /**
     * Page of the resource
     * @param href resource reference
     * @param settings settings of the HTTP requests
     * @return page of the resource
     */
    static Page of(@NonNull HREF href, @NonNull HttpSettings settings) {
        return new Page(Objects.requireNonNull(href, "Page::of - href is null"), Objects.requireNonNull(settings, "Page::of - settings is null"));
    }

    /**
     * Page URI
     */
    @Getter
    private final HREF href;

    /**
     * Settings of the HTTP requests
     */
    private final HttpSettings settings;

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
     * @return file resource size or -1 if the server does not report it
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
     * Charset of the content from the Content-Type header of the response
     * @return charset of the content or UTF-8 if the page is not connected, the charset is not defined or not supported
     */
    public @NonNull Charset charset() {
        return Optional.ofNullable(this.connection)
                .map(HttpURLConnection::getContentType)
                .map(CHARSET::matcher)
                .filter(Matcher::find)
                .flatMap(matcher -> charsetOf(matcher.group(1)))
                .orElse(StandardCharsets.UTF_8);
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
     * Network errors and 408, 429, 5xx responses are retried according to the settings.
     * The bytes before the offset are skipped if the server ignores the Range request
     * @param offset position in the resource to read from
     * @throws IOException if the connection cannot be established
     */
    public void connect(long offset) throws IOException {
        if (this.connection != null && offset > 0) {
            disconnect();
        }
        if (this.connection == null) {
            URL url = href.getUri().toURL();
            if (!"http".equalsIgnoreCase(url.getProtocol()) && !"https".equalsIgnoreCase(url.getProtocol())) {
                throw new IOException("Resource is not available over HTTP: " + Printable.of(href.getUri()));
            }
            for (int attempt = 1; ; attempt++) {
                try {
                    open(url, offset);
                    return;
                } catch (IOException ioex) {
                    Duration delay = retryDelay(ioex, attempt);
                    if (delay == null) {
                        throw ioex;
                    }
                    logger.warn("Attempt {} of {} has failed for: '{}'. Cause: {}. Next attempt in {} ms",
                            attempt, this.settings.getAttempts(), Printable.of(href.path()), Printable.of(ioex.getMessage()), delay.toMillis());
                    pause(delay);
                }
            }
        }
    }

    /**
     * Close the stream and the connection of the page. The next request opens a new connection
     */
    public void disconnect() {
        closeQuietly(this.inputStream);
        if (this.connection != null) {
            this.connection.disconnect();
        }
        this.inputStream = null;
        this.connection = null;
    }

    /**
     * Open the connection once; the page fields are changed only if the connection is established
     * @param url url of the resource
     * @param offset position in the resource to read from
     * @throws IOException if the connection cannot be established; the connection is released
     */
    private void open(@NonNull URL url, long offset) throws IOException {
        HttpURLConnection httpConnection = (HttpURLConnection) url.openConnection();
        httpConnection.setConnectTimeout(millis(this.settings.getConnectTimeout()));
        httpConnection.setReadTimeout(millis(this.settings.getReadTimeout()));
        httpConnection.setRequestMethod("GET");
        httpConnection.setRequestProperty("User-Agent", USER_AGENT);
        if (offset > 0L) {
            if (!this.acceptRange) {
                logger.warn("Accept-Ranges was not defined for connection with offset request for: '{}'", Printable.of(href.path()));
            }
            httpConnection.setRequestProperty("Range", "bytes=" + offset + "-");
        }
        InputStream stream = null;
        try {
            int responseCode = httpConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                long length = httpConnection.getContentLengthLong();
                Optional.ofNullable(href.getBytes())
                        .filter(Predicate.not(Long.valueOf(length)::equals))
                        .ifPresent(bytes -> logger.warn("The length of the file: {} is different from the declared size {} of: '{}'", length, bytes, Printable.of(href.path())));
                this.acceptRange = "bytes".equals(httpConnection.getHeaderField("Accept-Ranges"));
                stream = httpConnection.getInputStream();
                if (offset > 0L) {
                    logger.warn("Range request was ignored, {} bytes will be skipped for: '{}'", offset, Printable.of(href.path()));
                    skipFully(stream, offset);
                }
                this.contentLength = length;
            } else if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                stream = httpConnection.getInputStream();
            } else {
                throw new HttpStatusException(responseCode, retryAfter(httpConnection).orElse(null),
                        "Unable to open HTTP connection: " + Printable.of(httpConnection.getHeaderField(0)));
            }
            this.connection = httpConnection;
            this.inputStream = stream;
        } catch (IOException ioex) {
            closeQuietly(stream);
            closeQuietly(httpConnection.getErrorStream());
            httpConnection.disconnect();
            throw ioex;
        }
    }

    /**
     * Delay before the next attempt
     * @param ioex failure of the attempt
     * @param attempt number of the failed attempt
     * @return delay before the next attempt or null if the failure is final
     */
    private @Nullable Duration retryDelay(@NonNull IOException ioex, int attempt) {
        if (attempt >= this.settings.getAttempts()) {
            return null;
        }
        Duration backoff = this.settings.getRetryDelay().multipliedBy(1L << Math.min(attempt - 1, 16));
        if (ioex instanceof HttpStatusException) {
            HttpStatusException statusException = (HttpStatusException) ioex;
            if (!statusException.isTransient()) {
                return null;
            }
            return statusException.getRetryAfter()
                    .filter(retryAfter -> retryAfter.compareTo(backoff) > 0)
                    .map(retryAfter -> retryAfter.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : retryAfter)
                    .orElse(backoff);
        }
        return backoff;
    }

    /**
     * Delay requested by the server in seconds with the Retry-After header
     * @param httpConnection connection with the response
     * @return requested delay or empty if it is not defined in seconds
     */
    private static @NonNull Optional<Duration> retryAfter(@NonNull HttpURLConnection httpConnection) {
        return Optional.ofNullable(httpConnection.getHeaderField("Retry-After"))
                .map(RETRY_AFTER_SECONDS::matcher)
                .filter(Matcher::matches)
                .map(matcher -> Duration.ofSeconds(Long.parseLong(matcher.group(1))));
    }

    /**
     * Charset by name
     * @param name charset name
     * @return charset or empty if it is not supported
     */
    private static @NonNull Optional<Charset> charsetOf(@NonNull String name) {
        try {
            return Optional.of(Charset.forName(name));
        } catch (IllegalArgumentException iaex) {
            return Optional.empty();
        }
    }

    /**
     * Pause the current thread before the next attempt
     * @param delay pause duration
     * @throws InterruptedIOException if the thread is interrupted
     */
    private static void pause(@NonNull Duration delay) throws InterruptedIOException {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException iex) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Request retry has been interrupted");
        }
    }

    private static int millis(@NonNull Duration duration) {
        return (int) Math.min(Integer.MAX_VALUE, duration.toMillis());
    }

    private static void closeQuietly(@Nullable InputStream stream) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ignore) {
                // the stream is released anyway
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
     * Page of the resource
     * @param href file resource reference
     * @param settings settings of the HTTP requests
     */
    private Page(@NonNull HREF href, @NonNull HttpSettings settings) {
        this.href = Objects.requireNonNull(href, "Page::new - href is null");
        this.settings = Objects.requireNonNull(settings, "Page::new - settings is null");
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

    /**
     * Unsuccessful HTTP response
     */
    static final class HttpStatusException extends IOException {

        private static final long serialVersionUID = 1L;

        @Getter
        private final int statusCode;
        private final Duration retryAfter;

        HttpStatusException(int statusCode, @Nullable Duration retryAfter, @NonNull String message) {
            super(message);
            this.statusCode = statusCode;
            this.retryAfter = retryAfter;
        }

        /**
         * Delay requested by the server
         * @return requested delay or empty if it is not defined
         */
        @NonNull Optional<Duration> getRetryAfter() {
            return Optional.ofNullable(this.retryAfter);
        }

        /**
         * Check that the request may succeed later
         * @return true for 408 Request Timeout, 429 Too Many Requests and server errors
         */
        boolean isTransient() {
            return this.statusCode == HttpURLConnection.HTTP_CLIENT_TIMEOUT || this.statusCode == 429 || this.statusCode >= 500;
        }

    }

}
