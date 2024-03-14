package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Page process helper
 */
@AllArgsConstructor
@Getter
public class Page {

    private static final Pattern CONTENT_TYPE_PATTERN = Pattern.compile("^\\s*(\\S+)\\s*(?:;\\s*(\\S+)\\s*=\\s*(\\S+)\\s*)?$");

    @SneakyThrows
    public static Page of(@NonNull HREF href) {
        return new Page(Objects.requireNonNull(href, "Page::of - href is null"));
    }

    /**
     * Page URI
     */
    private HREF href;

    /**
     * Page content type
     */
    private String contentType;


    /**
     * Page content length
     */
    private int contentLength;


    /**
     * Page character set
     */
    private Charset charset;


    /**
     * Page data stream
     */
    private InputStream inputStream;

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

    /**
     * Check that path is child of URI
     */
    public boolean checkPath(@NonNull Path path) {
        return !path().normalize().startsWith(path.normalize());
    }

    /**
     * Connect to URI resource and load resource properties
     * @param href file resource reference
     * @throws IOException if an error occurs
     */
    private Page(@NonNull HREF href) throws IOException {
        URLConnection connection = Objects.requireNonNull(href, "Loader::new - URI is null")
                .getUri()
                .toURL()
                .openConnection();
        this.href = href;
        this.inputStream = connection.getInputStream();
        this.contentLength = connection.getContentLength();
        Matcher matcher = CONTENT_TYPE_PATTERN.matcher(connection.getContentType());
        if (matcher.matches()) {
            this.contentType = matcher.group(1);
            if ("charset".equalsIgnoreCase(matcher.group(2))) {
                this.charset = Charset.forName(matcher.group(3));
            } else {
                this.charset = UTF_8;
            }
        } else {
            this.contentType = connection.getContentType();
            this.charset = UTF_8;
        }
    }

    /**
     * Returns a string representation of the object.
     * @return  a string representation of the object.
     */
    @Override
    public String toString() {
        return "Path [ " + href +
                ", path: '" + path() + '\'' +
                ", contentLength: " + getContentLength() +
                ", contentType: '" + contentType + '\'' +
                ", fileName: '" + fileName() + '\'' +
                ( charset == null ? "" : ", charset: '" + charset + '\'' ) +
                " ]";
    }

}
