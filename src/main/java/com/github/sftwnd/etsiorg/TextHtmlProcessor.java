package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
    private final Consumer<Collection<Path>> onExpires;

    /**
     * Constructor of Text Html Processor
     * @param page text/html reference
     * @param processorFactory factory for processor resource
     * @param executor executor for the async execution
     * @param onExpires listener for the expired resources
     */
    TextHtmlProcessor(@NonNull Page page,
                      @NonNull ProcessorFactory<CompletableFuture<Stream<Path>>> processorFactory,
                      @Nullable Executor executor,
                      @Nullable Consumer<Collection<Path>> onExpires) {
        this.page = Objects.requireNonNull(page, "TextHtmlProcessor::new - page is null");
        this.processorFactory = Objects.requireNonNull(processorFactory, "TextHtmlProcessor::new - processorFactory is null");
        this.executor = executor;
        this.onExpires = onExpires;
    }
    /**
     * Process file with content or references recursively to load the tree of files
     * @return Stream of loaded file paths
     */
    @Override
    public @NonNull CompletableFuture<Stream<Path>> process() {
        final Page page = this.getPage();
        logger.debug("Start text/html process: '{}'", page.path());
        try {
            var result = swap(
                    parseFile(page)
                            .map(href -> CompletableFuture
                                    .supplyAsync(() -> Page.of(href))
                                    .thenApply(this.getProcessorFactory()::processor)
                                    .thenCompose(Processor::process)));
            logger.trace("Text/html page has been processed: '{}'", page.path());
            return result;
        } catch (IOException ioex) {
            logger.error("Unable to process text/html page: '{}'. Cause[{}]: {}", page.path(), ioex.getClass().getSimpleName(), ioex.getMessage());
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
        return getExecutor() == null
                ? CompletableFuture.allOf(futures).thenApplyAsync(swapAsync)
                : CompletableFuture.allOf(futures).thenApplyAsync(swapAsync, getExecutor());
    }

    /*
        Folder example:

            <html><head><title>www.etsi.org - /deliver/etsi_ts/129000_129099/129079/</title></head><body><H1>www.etsi.org - /deliver/etsi_ts/129000_129099/129079/</H1><hr>

            <pre><A HREF="/deliver/etsi_ts/129000_129099/">[To Parent Directory]</A>
            <br><br> 4/21/2011 12:08 PM        &lt;dir&gt; <A HREF="/deliver/etsi_ts/129000_129099/129079/10.00.00_60/">10.00.00_60</A>
                <br>12/14/2022  4:46 PM        &lt;dir&gt; <A HREF="/deliver/etsi_ts/129000_129099/129079/10.01.00_60/">10.01.00_60</A>
                <br>10/21/2011  9:37 AM        &lt;dir&gt; <A HREF="/deliver/etsi_ts/129000_129099/129079/10.01.01_60/">10.01.01_60</A>
                <br> 1/10/2012 10:21 AM        &lt;dir&gt; <A HREF="/deliver/etsi_ts/129000_129099/129079/10.02.00_60/">10.02.00_60</A>
                <br> 7/10/2012  2:14 PM        &lt;dir&gt; <A HREF="/deliver/etsi_ts/129000_129099/129079/10.03.00_60/">10.03.00_60</A>
                <br> 1/25/2013 11:01 AM        &lt;dir&gt; <A HREF="/deliver/etsi_ts/129000_129099/129079/10.04.00_60/">10.04.00_60</A>
                <br> 7/16/2014  1:34 PM        &lt;dir&gt; <A HREF="/deliver/etsi_ts/129000_129099/129079/10.05.00_60/">10.05.00_60</A>
                <br> 10/6/2014  2:21 PM        &lt;dir&gt; <A HREF="/deliver/etsi_ts/129000_129099/129079/10.06.00_60/">10.06.00_60</A>
                <br>  4/9/2015  1:24 PM        &lt;dir&gt; <A HREF="/deliver/etsi_ts/129000_129099/129079/10.07.00_60/">10.07.00_60</A>
                <br> 7/23/2015 12:45 PM        &lt;dir&gt; <A HREF="/deliver/etsi_ts/129000_129099/129079/10.08.00_60/">10.08.00_60</A>
                <br>10/16/2012 12:39 PM        &lt;dir&gt; <A HREF="/deliver/etsi_ts/129000_129099/129079/11.01.00_60/">11.01.00_60</A>
                <br> 1/25/2013 11:01 AM        &lt;dir&gt; <A HREF="/deliver/etsi_ts/129000_129099/129079/11.02.00_60/">11.02.00_60</A>
                <br> 4/16/2013  9:47 AM        &lt;dir&gt; <A HREF="/deliver/etsi_ts/129000_129099/129079/11.03.00_60/">11.03.00_60</A>
                <br> 7/18/2014  1:20 PM        &lt;dir&gt; <A HREF="/deliver/etsi_ts/129000_129099/129079/11.04.00_60/">11.04.00_60</A>
                <br> 10/6/2014  2:21 PM        &lt;dir&gt; <A HREF="/deliver/etsi_ts/129000_129099/129079/11.05.00_60/">11.05.00_60</A>
                <br>  4/9/2015  1:24 PM        &lt;dir&gt; <A HREF="/deliver/etsi_ts/129000_129099/129079/11.06.00_60/">11.06.00_60</A>
                <br> 7/23/2015 12:46 PM        &lt;dir&gt; <A HREF="/deliver/etsi_ts/129000_129099/129079/11.07.00_60/">11.07.00_60</A>
                <br> 10/6/2014  2:21 PM        &lt;dir&gt; <A HREF="/deliver/etsi_ts/129000_129099/129079/12.01.00_60/">12.01.00_60</A>
                <br>  4/9/2015  1:23 PM        &lt;dir&gt; <A HREF="/deliver/etsi_ts/129000_129099/129079/12.02.00_60/">12.02.00_60</A>
                <br> 7/23/2015 12:45 PM        &lt;dir&gt; <A HREF="/deliver/etsi_ts/129000_129099/129079/12.03.00_60/">12.03.00_60</A>
                <br> 1/20/2016  2:29 PM        &lt;dir&gt; <A HREF="/deliver/etsi_ts/129000_129099/129079/13.00.00_60/">13.00.00_60</A>
                <br> 4/11/2017  9:10 AM        &lt;dir&gt; <A HREF="/deliver/etsi_ts/129000_129099/129079/14.00.00_60/">14.00.00_60</A>
                <br> 7/12/2018  6:51 AM        &lt;dir&gt; <A HREF="/deliver/etsi_ts/129000_129099/129079/15.00.00_60/">15.00.00_60</A>
                <br> 8/12/2020 12:45 PM        &lt;dir&gt; <A HREF="/deliver/etsi_ts/129000_129099/129079/16.00.00_60/">16.00.00_60</A>
                <br> 5/16/2022 12:51 PM        &lt;dir&gt; <A HREF="/deliver/etsi_ts/129000_129099/129079/17.00.00_60/">17.00.00_60</A>
            <br></pre><hr></body></html>

        With Files Folder:

            <html><head><title>www.etsi.org - /deliver/etsi_ts/129000_129099/129078/17.00.00_60/</title></head><body><H1>www.etsi.org - /deliver/etsi_ts/129000_129099/129078/17.00.00_60/</H1><hr>

            <pre><A HREF="/deliver/etsi_ts/129000_129099/129078/">[To Parent Directory]</A>
            <br><br> 4/21/2022  8:39 AM       932564 <A HREF="/deliver/etsi_ts/129000_129099/129078/17.00.00_60/ts_129078v170000p.pdf">ts_129078v170000p.pdf</A>
                <br> 4/21/2022  8:41 AM        38692 <A HREF="/deliver/etsi_ts/129000_129099/129078/17.00.00_60/ts_129078v170000p0.zip">ts_129078v170000p0.zip</A>
                <br></pre><hr></body></html>

     */
    private static final Pattern REF_PATTERN = Pattern.compile(
            "<br>\\s*(\\d+)/(\\d+)/(\\d+)\\s+(\\d+):(\\d+)\\s+(.)M\\s+(?:(\\d+)|\\S+(dir)\\S+)\\s+<a\\s+.*?href\\s*=\\s*\"(.*?)\".*?>(.*?)</a>",
            CASE_INSENSITIVE);

    /**
     * Parse loader with references to the stream of URI
     * @param page Page description to load
     * @return list of uri to load
     */
    private @NonNull Stream<HREF> parseFile(@NonNull Page page) throws IOException {
        Matcher matcher = REF_PATTERN.matcher(loadFile(page));
        Collection<HREF> hrefs = new LinkedList<>();
        Collection<HREF> versionedHrefs = new LinkedList<>();
        while (matcher.find()) {
            HREF href = HREF.builder()
                    .uri(page.getUri().resolve("/").resolve(matcher.group(9)))
                    .bytes(Optional.ofNullable(matcher.group(7))
                            .filter(Predicate.not(String::isBlank))
                            .map(Long::parseLong).orElse(null))
                    .dateTime(LocalDateTime.of(
                            Integer.parseInt(matcher.group(3)),
                            Integer.parseInt(matcher.group(1)),
                            Integer.parseInt(matcher.group(2)),
                            Integer.parseInt(matcher.group(4)) % 12 + (matcher.group(6).charAt(0) == 'P' ? 12 : 0),
                            Integer.parseInt(matcher.group(5))))
                    .regularFile(! Optional.ofNullable(matcher.group(8))
                            .map(String::toLowerCase).map(String::trim)
                            .map("dir"::equals)
                            .orElse(false))
                    .build();
            (href.isVersioned() ? versionedHrefs : hrefs).add(href);
            logger.trace("Found {}: {}", href.isRegularFile() ? "file" : "path", href);
        }
        versionedHrefs
                .stream()
                .max(Comparator.comparing(href -> href.name().toString()))
                .ifPresent(
                        actualRef -> {
                            hrefs.add(actualRef);
                            logger.info("Found actual version: '{}'", actualRef.path());
                            if (this.getOnExpires() != null) {
                                Optional.of(versionedHrefs
                                                .stream()
                                                .filter(Predicate.not(href -> href == actualRef))
                                                .map(HREF::path)
                                                .collect(Collectors.toList()))
                                        .filter(Predicate.not(Collection::isEmpty))
                                        .ifPresent(this.getOnExpires());
                            }
                        });
        return hrefs.stream();
    }

    /**
     * Load text/html file to String
     * @param page page to load
     * @return loaded text file as String
     * @throws IOException in the case of error
     */
    private @NonNull String loadFile(@NonNull Page page) throws IOException {
        byte[] buff = new byte[Long.valueOf(page.contentLength()).intValue()];
        try (InputStream inputStream = page.inputStream()) {
            for (int readed = 0; readed < buff.length; ) {
                readed += inputStream.read(buff, readed, buff.length - readed);
            }
        }
        return new String(buff);
    }

}