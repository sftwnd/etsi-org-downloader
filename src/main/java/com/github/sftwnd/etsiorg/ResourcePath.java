package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Path of a site resource as the list of its safe decoded segments
 */
final class ResourcePath {

    /**
     * Separator of the path segments encoded in the raw path
     */
    private static final Pattern ENCODED_SEPARATOR = Pattern.compile("%(2f|5c)", Pattern.CASE_INSENSITIVE);

    private ResourcePath() {
    }

    /**
     * Decoded segments of the resource path
     * @param uri resource uri
     * @return decoded segments; empty list for the site root, a trailing '/' does not add a segment
     * @throws IllegalArgumentException if the path does not start at the site root or contains an encoded separator,
     *                                  an empty, '.' or '..' segment, a backslash, control or format characters
     */
    static @NonNull List<String> segments(@NonNull URI uri) {
        Objects.requireNonNull(uri, "ResourcePath::segments - uri is null");
        String rawPath = uri.getRawPath();
        if (rawPath == null || !rawPath.startsWith("/")) {
            throw new IllegalArgumentException("Resource path is not absolute: " + Printable.of(uri));
        }
        if (ENCODED_SEPARATOR.matcher(rawPath).find()) {
            throw new IllegalArgumentException("Resource path contains an encoded separator: " + Printable.of(uri));
        }
        String path = uri.getPath().substring(1);
        if (path.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)
                    || segment.indexOf('\\') >= 0 || !Printable.isPrintable(segment)) {
                throw new IllegalArgumentException("Resource path contains an unsafe segment '" + Printable.of(segment) + "': " + Printable.of(uri));
            }
            segments.add(segment);
        }
        return Collections.unmodifiableList(segments);
    }

}
