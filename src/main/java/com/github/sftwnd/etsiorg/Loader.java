package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

@Slf4j
@Getter(value = AccessLevel.PRIVATE)
public class Loader {

    private final String root;
    private final Executor executor;

    private Loader(@NonNull String root, @Nullable Executor executor) {
        this.root = root;
        this.executor = executor;
    }

    /**
     * Load tree of files recursively by the root path URI
     * @param uri root page path
     * @return Stream of loaded file paths
     */
    public @NonNull CompletableFuture<Stream<Path>> load(@NonNull URI uri) {
        return Page.loadAsync(uri, executor).thenCompose(this::processFile);
    }

    /**
     * Process file with content or references recursively to load the tree of files
     * @param page Loaded page
     * @return Stream of loaded file paths
     */
    public @NonNull CompletableFuture<Stream<Path>> processFile(@NonNull Page page) {
        logger.debug("Start file process: {}", page.path());
        CompletableFuture<Stream<Path>> result;
        if ("text/html".equalsIgnoreCase(page.getContentType())) {
            result = swap(parseToUri(page)
                    .map(uri -> Page.loadAsync(uri, executor))
                    .map(cf -> executor == null
                            ? cf.thenComposeAsync(this::processFile)
                            : cf.thenComposeAsync(this::processFile, executor))
            );
            logger.trace("File has been processed as set of references: {}", page.path());
        } else {
            result = CompletableFuture.completedFuture(Stream.of(saveFile(page)));
            logger.trace("File has been processed as regular file: {}", page.path());
        }
        return result;
    }

    /**
     * Swap Stream of CompletableFuture of Stream to CompletableFuture of Stream
     * @param futureCollection Collection of CompletableFuture of Collection
     * @return CompletableFuture of Stream
     * @param <X> type of collection element
     */
    private <X> CompletableFuture<Stream<X>> swap(
            @NonNull Stream<CompletableFuture<Stream<X>>> futureCollection
    ) {
        @SuppressWarnings("unchecked")
        CompletableFuture<Stream<X>>[] futures = (CompletableFuture<Stream<X>>[])futureCollection.toArray(size -> new CompletableFuture<?>[size]);
        Function<? super Void, Stream<X>> swapAsync = ignore -> Arrays.stream(futures).flatMap(CompletableFuture::join);
        return executor == null
                ? CompletableFuture.allOf(futures).thenApplyAsync(swapAsync)
                : CompletableFuture.allOf(futures).thenApplyAsync(swapAsync, executor);
    }

    /**
     * Parse page and create Page loadings
     * @param page page to pars for page set
     * @return Collection of future with loadable pages
     */
    private Stream<URI> parseToUri(@NonNull Page page) {
        return parseFile(page)
                .stream()
                .filter(this::notExists);
    }

    /**
     * Checking that the file does not exist
     * @param uri URI for the file
     * @return true checking that the file does not exist
     */
    private boolean notExists(@NonNull URI uri) {
        Path path = Path.of(".", uri.getPath());
        return !(Files.exists(path) && Files.isRegularFile(path));
    }

    private final Pattern REF_PATTERN = Pattern.compile("<a\\s+.*?href\\s*=\\s*\"(.*?)\".*?>(.*?)</a>", CASE_INSENSITIVE);
    /**
     * Parse page with references to the list of URI
     * @param page Loaded file content
     * @return list of uri to load
     */
    private @NonNull Collection<URI> parseFile(@NonNull Page page) {
        String string = new String(page.getBuff(), page.getCharset());
        Matcher matcher = REF_PATTERN.matcher(string);
        List<URI> uriList = new LinkedList<>();
        while (matcher.find()) {
            Path path = Path.of(matcher.group(1));
            if ( matcher.group(2).charAt(0) != '[' && !page.path().startsWith(path)) {
                uriList.add(page.getUri().resolve("/").resolve(path.toString()));
                logger.trace("Found uri: {}", path);
            }
        }
        return uriList;
    }

    /**
     * File will be loaded and Path to file ill be returned
     * @param page Loaded file content
     * @return path to saved file
     */
    @SneakyThrows
    private @NonNull Path saveFile(@NonNull Page page) {
        Path path = Path.of(this.getRoot(), page.path().toString());
        if (checkDir(path.getParent())) {
            try (FileOutputStream fos = new FileOutputStream(path.toFile(), false)) {
                fos.write(page.getBuff());
                logger.info("Saved file: {}", path);
            }
        }
        return path;
    }

    private synchronized boolean checkDir(@NonNull Path dir) {
        if (Files.exists(dir)) {
            if (Files.isRegularFile(dir)) {
                logger.error("Unable to create dir: {}. File with such name exists.", dir);
                return false;
            }
        } else {
            try {
                Files.createDirectories(dir);
            } catch (FileAlreadyExistsException ignore) {
            } catch (IOException ioex) {
                logger.error("Unable to create dir: {}. Cause: {}", dir, ioex.getLocalizedMessage());
                return false;
            }
        }
        return true;
    }

    /**
     * Create Builder for Loader
     * @return Builder for Loader
     */
    public  static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for loader
     */
    public static class Builder {

        private Path root;
        private Executor executor;

        private Builder() {
            root(".");
        }

        public Builder root(@NonNull String root) {
            return(root(Path.of(root)));
        }

        public Builder root(@NonNull Path root) {
            this.root = root;
            return this;
        }

        public Builder executor(@NonNull Executor executor) {
            this.executor = executor;
            return this;
        }

        public Loader build() {
            return new Loader(this.root.toString(), executor);
        }

    }

}
