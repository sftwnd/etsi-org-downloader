package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import lombok.Getter;

import java.net.URI;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Destination folder of the mirror: the only place where a resource of the site is mapped to a local path
 */
@Getter
public class Destination {

    /**
     * Separator of the path segments encoded in the raw path
     */
    private static final Pattern ENCODED_SEPARATOR = Pattern.compile("%(2f|5c)", Pattern.CASE_INSENSITIVE);

    /**
     * Absolute normalized path of the destination folder
     */
    private final Path root;

    /**
     * Constructor of the destination folder
     * @param root path to the destination folder
     */
    public Destination(@NonNull Path root) {
        this.root = Objects.requireNonNull(root, "Destination::new - root is null").toAbsolutePath().normalize();
    }

    /**
     * Local path of the site resource: the segments of the resource path are resolved against the destination folder
     * @param uri resource uri
     * @return local path inside the destination folder
     * @throws IllegalArgumentException if the resource path is unsafe or leads outside the destination folder
     */
    public @NonNull Path pathOf(@NonNull URI uri) {
        Objects.requireNonNull(uri, "Destination::pathOf - uri is null");
        String rawPath = uri.getRawPath();
        if (rawPath == null || !rawPath.startsWith("/")) {
            throw new IllegalArgumentException("Resource path is not absolute: " + Printable.of(uri));
        }
        if (ENCODED_SEPARATOR.matcher(rawPath).find()) {
            throw new IllegalArgumentException("Resource path contains an encoded separator: " + Printable.of(uri));
        }
        Path path = this.root;
        String decodedPath = uri.getPath().substring(1);
        if (!decodedPath.isEmpty()) {
            for (String segment : decodedPath.split("/")) {
                path = resolve(path, segment, uri);
            }
        }
        if (!path.normalize().startsWith(this.root)) {
            throw new IllegalArgumentException("Resource path leads outside the destination folder: " + Printable.of(uri));
        }
        return path;
    }

    /**
     * Resolve the segment of the resource path against the local path
     * @param path local path of the parent segment
     * @param segment decoded segment of the resource path
     * @param uri resource uri
     * @return local path of the segment
     * @throws IllegalArgumentException if the segment is unsafe
     */
    private static @NonNull Path resolve(@NonNull Path path, @NonNull String segment, @NonNull URI uri) {
        if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)
                || segment.indexOf('\\') >= 0 || !Printable.isPrintable(segment)) {
            throw new IllegalArgumentException("Resource path contains an unsafe segment '" + Printable.of(segment) + "': " + Printable.of(uri));
        }
        try {
            Path resolved = path.resolve(segment);
            if (!path.equals(resolved.getParent())) {
                throw new IllegalArgumentException("Resource path contains an unsafe segment '" + Printable.of(segment) + "': " + Printable.of(uri));
            }
            return resolved;
        } catch (InvalidPathException ipex) {
            throw new IllegalArgumentException("Resource path contains an invalid segment '" + Printable.of(segment) + "': " + Printable.of(uri), ipex);
        }
    }

}
