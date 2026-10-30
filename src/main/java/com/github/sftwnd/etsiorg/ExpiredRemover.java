package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Removes the local copies of the expired resources. Symbolic links are removed themselves and are never followed,
 * so nothing outside the destination folder is removed
 */
@Slf4j
@Getter
public class ExpiredRemover implements Function<Collection<URI>, Summary> {

    private final Destination destination;

    /**
     * Constructor of the expired resources remover
     * @param destination destination folder of the mirror
     */
    public ExpiredRemover(@NonNull Destination destination) {
        this.destination = Objects.requireNonNull(destination, "ExpiredRemover::new - destination is null");
    }

    /**
     * Remove the local copies of the expired resources
     * @param expires uris of the expired resources
     * @return summary with the number of the removed local copies
     */
    @Override
    public @NonNull Summary apply(@NonNull Collection<URI> expires) {
        return Summary.removed(localCopies(Objects.requireNonNull(expires, "ExpiredRemover::apply - expires is null")).stream()
                .filter(this::remove)
                .count());
    }

    /**
     * What {@link #apply(Collection)} would remove, without changes
     * @param expires uris of the expired resources
     * @return summary with the number of the local copies to remove
     */
    public @NonNull Summary plan(@NonNull Collection<URI> expires) {
        List<Path> localCopies = localCopies(Objects.requireNonNull(expires, "ExpiredRemover::plan - expires is null"));
        localCopies.forEach(expired -> logger.info("Expired path is to be removed: '{}'", Printable.of(expired)));
        return Summary.removed(localCopies.size());
    }

    /**
     * Existing local copies of the expired resources
     * @param expires uris of the expired resources
     * @return sorted local paths that exist, symbolic links included
     */
    private @NonNull List<Path> localCopies(@NonNull Collection<URI> expires) {
        return expires.stream()
                .map(this::localPath)
                .flatMap(Optional::stream)
                .filter(path -> Files.exists(path, LinkOption.NOFOLLOW_LINKS))
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Local path of the expired resource
     * @param uri uri of the expired resource
     * @return local path or empty if the resource path is unsafe
     */
    private @NonNull Optional<Path> localPath(@NonNull URI uri) {
        try {
            return Optional.of(this.getDestination().pathOf(uri));
        } catch (IllegalArgumentException iaex) {
            logger.warn("Expired path is skipped. {}", iaex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Remove the file, the link or the directory tree without following symbolic links
     * @param expired local path of the expired resource
     * @return true if the tree has been walked; a path that cannot be removed in it is reported
     */
    private boolean remove(@NonNull Path expired) {
        try {
            Files.walkFileTree(expired, new SimpleFileVisitor<>() {
                @Override
                public @NonNull FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attributes) {
                    delete(file, attributes.isSymbolicLink() ? "Link" : "File");
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NonNull FileVisitResult visitFileFailed(@NonNull Path file, @NonNull IOException ioex) {
                    logger.warn("Unable to remove: '{}'. Cause: {} {}", Printable.of(file), ioex.getClass().getSimpleName(), Printable.of(ioex.getMessage()));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NonNull FileVisitResult postVisitDirectory(@NonNull Path directory, @Nullable IOException ioex) {
                    if (ioex != null) {
                        logger.warn("Unable to list directory: '{}'. Cause: {} {}", Printable.of(directory), ioex.getClass().getSimpleName(), Printable.of(ioex.getMessage()));
                    }
                    delete(directory, "Directory");
                    return FileVisitResult.CONTINUE;
                }
            });
            logger.info("Expired path has been removed: '{}'", Printable.of(expired));
            return true;
        } catch (IOException ioex) {
            logger.warn("Unable to remove expired path: '{}'. Cause: {} {}", Printable.of(expired), ioex.getClass().getSimpleName(), Printable.of(ioex.getMessage()));
            return false;
        }
    }

    /**
     * Delete the file, the link or the empty directory
     * @param path path to delete
     * @param kind kind of the path for the log
     */
    private static void delete(@NonNull Path path, @NonNull String kind) {
        try {
            Files.delete(path);
            logger.debug("{}: '{}' has been deleted", kind, Printable.of(path));
        } catch (IOException ioex) {
            logger.warn("Unable to delete {}: '{}'. Cause: {} {}", kind.toLowerCase(Locale.ROOT), Printable.of(path), ioex.getClass().getSimpleName(), Printable.of(ioex.getMessage()));
        }
    }

}
