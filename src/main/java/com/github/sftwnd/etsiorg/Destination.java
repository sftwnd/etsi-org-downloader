package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import lombok.Getter;

import java.net.URI;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Destination folder of the mirror: the only place where a resource of the site is mapped to a local path
 */
@Getter
public class Destination {

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
        Path path = this.root;
        for (String segment : ResourcePath.segments(Objects.requireNonNull(uri, "Destination::pathOf - uri is null"))) {
            path = resolve(path, segment, uri);
        }
        if (!path.normalize().startsWith(this.root)) {
            throw new IllegalArgumentException("Resource path leads outside the destination folder: " + Printable.of(uri));
        }
        return path;
    }

    /**
     * Resolve the segment of the resource path against the local path
     * @param path local path of the parent segment
     * @param segment safe decoded segment of the resource path
     * @param uri resource uri
     * @return local path of the segment
     * @throws IllegalArgumentException if the segment is not a single name of the local file system
     */
    private static @NonNull Path resolve(@NonNull Path path, @NonNull String segment, @NonNull URI uri) {
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
