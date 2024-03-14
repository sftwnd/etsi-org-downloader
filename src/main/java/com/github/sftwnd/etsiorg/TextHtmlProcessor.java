package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

/**
 * Save file if one doesn't exist
 */
@Getter
@Slf4j
public class TextHtmlProcessor implements Processor<CompletableFuture<Stream<Path>>> {

    private final Page page;
    private final ProcessorFactory<CompletableFuture<Stream<Path>>> processorFactory;
    private final Executor executor;

    TextHtmlProcessor(@NonNull Page page, @NonNull ProcessorFactory<CompletableFuture<Stream<Path>>> processorFactory, @Nullable Executor executor) {
        this.page = Objects.requireNonNull(page, "TextHtmlProcessor::new - page is null");
        this.processorFactory = Objects.requireNonNull(processorFactory, "TextHtmlProcessor::new - processorFactory is null");
        this.executor = executor;
    }
    /**
     * Process file with content or references recursively to load the tree of files
     * @return Stream of loaded file paths
     */
    @Override
    public @NonNull CompletableFuture<Stream<Path>> process() {
        logger.debug("Start text/html process: {}", page.path());
        try {
            var result = swap(
                    parseFile(page)
                            .map(uri -> CompletableFuture
                                    .supplyAsync(() -> Page.of(HREF.builder().uri(uri).build()))
                                    .thenApply(processorFactory::processor)
                                    .thenCompose(Processor::process)));
            logger.trace("Text/html fila has been processed: {}", page.path());
            return result;
        } catch (IOException ioex) {
            logger.error("Unable to process text/html file: {} by cause: {}", page.path(), ioex.getLocalizedMessage());
            return CompletableFuture.completedFuture(Stream.empty());
        }
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

    private final Pattern REF_PATTERN = Pattern.compile("<a\\s+.*?href\\s*=\\s*\"(.*?)\".*?>(.*?)</a>", CASE_INSENSITIVE);
    /**
     * Parse loader with references to the stream of URI
     * @param page Page description to load
     * @return list of uri to load
     */
    private @NonNull Stream<URI> parseFile(@NonNull Page page) throws IOException {
        Matcher matcher = REF_PATTERN.matcher(loadFile(page));
        List<URI> uriList = new LinkedList<>();
        while (matcher.find()) {
            Path path = Path.of(matcher.group(1));
            if (matcher.group(2).charAt(0) != '[' && page.checkPath(path)) {
                uriList.add(page.getUri().resolve("/").resolve(path.toString()));
                logger.trace("Found uri: {}", path);
            }
        }
        return uriList.stream();
    }

    /**
     * Load text/html file to String
     * @param page page to load
     * @return loaded text file as String
     * @throws IOException in the case of error
     */
    private @NonNull String loadFile(@NonNull Page page) throws IOException {
        byte[] buff = new byte[page.getContentLength()];
        try (InputStream inputStream = page.getInputStream()) {
            for (int readed = 0; readed < buff.length; ) {
                readed += inputStream.read(buff, readed, buff.length - readed);
            }
        }
        return new String(buff, page.getCharset());
    }

}