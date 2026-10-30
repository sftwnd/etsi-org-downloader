package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
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
    private final LinkPolicy linkPolicy;
    private final Executor executor;
    private final Consumer<Collection<URI>> onExpires;

    /**
     * Constructor of Text Html Processor
     * @param page text/html reference
     * @param processorFactory factory for processor resource
     * @param linkPolicy policy of the links to follow
     * @param executor executor for the async execution
     * @param onExpires listener for the expired resources
     */
    TextHtmlProcessor(@NonNull Page page,
                      @NonNull ProcessorFactory<CompletableFuture<Stream<Path>>> processorFactory,
                      @NonNull LinkPolicy linkPolicy,
                      @Nullable Executor executor,
                      @Nullable Consumer<Collection<URI>> onExpires) {
        this.page = Objects.requireNonNull(page, "TextHtmlProcessor::new - page is null");
        this.processorFactory = Objects.requireNonNull(processorFactory, "TextHtmlProcessor::new - processorFactory is null");
        this.linkPolicy = Objects.requireNonNull(linkPolicy, "TextHtmlProcessor::new - linkPolicy is null");
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
        logger.debug("Start text/html process: '{}'", Printable.of(page.path()));
        try {
            var result = swap(
                    parseFile(page)
                            .map(href -> CompletableFuture
                                    .supplyAsync(() -> Page.of(href))
                                    .thenApply(this.getProcessorFactory()::processor)
                                    .thenCompose(Processor::process)));
            logger.trace("Text/html page has been processed: '{}'", Printable.of(page.path()));
            return result;
        } catch (IOException ioex) {
            logger.error("Unable to process text/html page: '{}'. Cause[{}]: {}", Printable.of(page.path()), ioex.getClass().getSimpleName(), Printable.of(ioex.getMessage()));
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
    /**
     * Listing entry: date, time, size or {@code <dir>}, link and its text. Every quantifier is either bounded or stops at
     * a delimiter it cannot cross, so the matching time is linear in the listing length
     */
    private static final Pattern REF_PATTERN = Pattern.compile(
            "<br>\\s*(\\d{1,2})/(\\d{1,2})/(\\d{4})\\s+(\\d{1,2}):(\\d{2})\\s+([AP])M\\s+(?:(\\d{1,18})|\\S{0,16}(dir)\\S{0,16})"
                    + "\\s+<a\\s+[^>]*?href\\s*=\\s*\"([^\"]*)\"[^>]*>([^<]*)</a>",
            CASE_INSENSITIVE);

    /**
     * Any link of the listing with its text
     */
    private static final Pattern ANCHOR = Pattern.compile("<a\\s[^>]*>([^<]*)</a>", CASE_INSENSITIVE);

    /**
     * Text of the link to the parent folder, which is not a listing entry
     */
    private static final String PARENT_DIRECTORY = "[To Parent Directory]";

    /**
     * Maximal size of a listing: the largest ETSI listings are tens of kilobytes
     */
    static final int MAX_LISTING_SIZE = 4 << 20;

    /**
     * Parse loader with references to the stream of URI
     * @param page Page description to load
     * @return list of uri to load
     */
    private @NonNull Stream<HREF> parseFile(@NonNull Page page) throws IOException {
        String listing = loadFile(page);
        Matcher matcher = REF_PATTERN.matcher(listing);
        Collection<HREF> hrefs = new LinkedList<>();
        Collection<HREF> versionedHrefs = new LinkedList<>();
        long entries = 0;
        while (matcher.find()) {
            entries++;
            boolean folder = matcher.group(8) != null;
            URI uri = page.getUri().resolve(Html.unescape(matcher.group(9)));
            if (!this.getLinkPolicy().accepts(uri)) {
                logger.warn("Link is outside of the mirrored folder and is skipped: '{}'", Printable.of(uri));
                continue;
            }
            if (folder && !uri.getRawPath().endsWith("/")) {
                uri = URI.create(uri + "/");
            }
            HREF href = HREF.builder()
                    .uri(uri)
                    .bytes(Optional.ofNullable(matcher.group(7))
                            .filter(Predicate.not(String::isBlank))
                            .map(Long::parseLong).orElse(null))
                    .dateTime(LocalDateTime.of(
                            Integer.parseInt(matcher.group(3)),
                            Integer.parseInt(matcher.group(1)),
                            Integer.parseInt(matcher.group(2)),
                            Integer.parseInt(matcher.group(4)) % 12 + ("P".equalsIgnoreCase(matcher.group(6)) ? 12 : 0),
                            Integer.parseInt(matcher.group(5))))
                    .regularFile(!folder)
                    .build();
            (href.isVersioned() ? versionedHrefs : hrefs).add(href);
            logger.trace("Found {}: {}", href.isRegularFile() ? "file" : "path", Printable.of(href));
        }
        long links = ANCHOR.matcher(listing).results()
                .filter(anchor -> !PARENT_DIRECTORY.equals(anchor.group(1).trim()))
                .count();
        if (links > entries) {
            logger.warn("{} of {} links are not recognized in the listing: '{}'", links - entries, links, Printable.of(page.path()));
        }
        versionedHrefs
                .stream()
                .max(Comparator.comparingLong(HREF::getVersion))
                .ifPresent(
                        actualRef -> {
                            hrefs.add(actualRef);
                            logger.info("Found actual version: '{}'", Printable.of(actualRef.path()));
                            if (this.getOnExpires() != null) {
                                Optional.of(versionedHrefs
                                                .stream()
                                                .filter(href -> href.getVersion() < actualRef.getVersion())
                                                .map(HREF::getUri)
                                                .distinct()
                                                .collect(Collectors.toList()))
                                        .filter(Predicate.not(Collection::isEmpty))
                                        .ifPresent(this.getOnExpires());
                            }
                        });
        return hrefs.stream().filter(this::firstVisit);
    }

    /**
     * Register the visit of the resource
     * @param href resource reference
     * @return true if the resource has not been visited yet
     */
    private boolean firstVisit(@NonNull HREF href) {
        boolean first = this.getLinkPolicy().visit(href.getUri());
        if (!first) {
            logger.debug("Resource has already been processed: '{}'", Printable.of(href.path()));
        }
        return first;
    }

    /**
     * Load text/html file to String
     * @param page page to load
     * @return loaded text file as String
     * @throws IOException in the case of error
     */
    private @NonNull String loadFile(@NonNull Page page) throws IOException {
        long contentLength = page.contentLength();
        if (contentLength > MAX_LISTING_SIZE) {
            throw new IOException("Listing is too large: " + contentLength + " bytes, the limit is " + MAX_LISTING_SIZE);
        }
        byte[] content;
        try (InputStream inputStream = page.inputStream()) {
            content = inputStream.readNBytes(MAX_LISTING_SIZE + 1);
        }
        if (content.length > MAX_LISTING_SIZE) {
            throw new IOException("Listing is larger than the limit of " + MAX_LISTING_SIZE + " bytes");
        }
        if (contentLength >= 0 && content.length < contentLength) {
            throw new EOFException("Listing has ended after " + content.length + " of " + contentLength + " bytes");
        }
        return new String(content, page.charset());
    }

}