package com.github.sftwnd.etsiorg;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import edu.umd.cs.findbugs.annotations.NonNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class TextHtmlProcessorTest extends AbstractFileSourceTest {

    @Test
    @SuppressWarnings("unchecked")
    void textHtmlProcessorNullPageTest() {
        assertThrows(
                NullPointerException.class,
                () -> new TextHtmlProcessor(this.<Page>nullRef().get(), mock(ProcessorFactory.class), LINK_POLICY, mock(Executor.class), mock(Consumer.class))
        );
    }

    @Test
    @SuppressWarnings({"unchecked"})
    void textHtmlProcessorProcessorFactoryNull() {
        assertThrows(
                NullPointerException.class,
                () -> new TextHtmlProcessor(
                        mock(Page.class),
                        this.<ProcessorFactory<CompletableFuture<Stream<Path>>>>nullRef().get(),
                        LINK_POLICY,
                        mock(Executor.class),
                        mock(Consumer.class)));
    }

    @Test
    @SuppressWarnings({"unchecked"})
    void textHtmlProcessorLinkPolicyNull() {
        assertThrows(
                NullPointerException.class,
                () -> new TextHtmlProcessor(
                        mock(Page.class),
                        mock(ProcessorFactory.class),
                        this.<LinkPolicy>nullRef().get(),
                        mock(Executor.class),
                        mock(Consumer.class)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void textHtmlProcessorTest() {
        assertDoesNotThrow(
                () -> new TextHtmlProcessor(mock(Page.class), mock(ProcessorFactory.class), LINK_POLICY, null, null)
        );
    }

    @Test
    void processFilesHtmlTest() throws IOException {
        processFileTest("files");
    }

    @Test
    void processVersionsHtmlTest() throws IOException {
        processFileTest("versions");
    }

    @Test
    void processNonVersionHtmlTest() throws IOException {
        processFileTest("non-version");
    }

    @Test
    void processHostileHtmlTest() throws IOException {
        processFileTest("hostile");
    }

    @Test
    void processUnpaddedVersionsHtmlTest() throws IOException {
        processFileTest("versions-unpadded");
    }

    @Test
    void processPrematureEndOfListingTest() throws IOException {
        startUp("files");
        when(this.href.getBytes()).thenReturn(this.bytes + 10);
        var completableFuture = assertDoesNotThrow(() -> this.textHtmlProcessor(this.page).process());
        assertEquals(List.of(), assertDoesNotThrow(completableFuture::join).collect(Collectors.toList()));
    }

    @Test
    void processRealListingTest() throws IOException {
        startUp("etsi-129078");
        Path actual = Path.of("/deliver/etsi_ts/129000_129099/129078/19.00.00_60");
        assertEquals(List.of(actual), this.textHtmlProcessor(this.page).process().join().collect(Collectors.toList()));
        assertEquals(61, this.excluded.size());
        assertFalse(this.excluded.contains(actual));
    }

    @Test
    void processChunkedListingTest() throws IOException {
        startUp("files");
        doReturn(-1L).when(this.page).contentLength();
        assertEquals(FILES_PATHS, this.textHtmlProcessor(this.page).process().join().collect(Collectors.toList()));
    }

    @Test
    void processTooLargeDeclaredListingTest() throws IOException {
        startUp("files");
        doReturn(TextHtmlProcessor.MAX_LISTING_SIZE + 1L).when(this.page).contentLength();
        assertEquals(List.of(), this.textHtmlProcessor(this.page).process().join().collect(Collectors.toList()));
    }

    @Test
    void processTooLargeChunkedListingTest() throws IOException {
        startUp("files");
        doReturn(-1L).when(this.page).contentLength();
        when(this.page.inputStream()).thenReturn(new InputStream() {
            @Override
            public int read() {
                return 'x';
            }
        });
        assertEquals(List.of(), this.textHtmlProcessor(this.page).process().join().collect(Collectors.toList()));
    }

    @Test
    void processBacktrackingLineTest() throws IOException {
        startUp("files");
        byte[] line = ("<br> 1/1/2020  1:00 AM 123 <a " + "href=\"\">".repeat(20_000)).getBytes(StandardCharsets.US_ASCII);
        doReturn((long) line.length).when(this.page).contentLength();
        when(this.page.inputStream()).thenReturn(new ByteArrayInputStream(line));
        var result = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> this.textHtmlProcessor(this.page).process().join());
        assertEquals(List.of(), result.collect(Collectors.toList()));
    }

    @Test
    void processRelativeLinksTest() throws IOException {
        Logger logger = (Logger) LoggerFactory.getLogger(TextHtmlProcessor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            processFileTest("relative");
        } finally {
            logger.detachAppender(appender);
        }
        Map<String, HREF> processed = ((FooProcessorFactory) this.processorFactory).processed.stream()
                .collect(Collectors.toMap(href -> href.name().toString(), Function.identity()));
        assertEquals(URI.create("http://localhost/deliver/etsi_ts/129000_129099/129011/17.00.00_60/"), processed.get("17.00.00_60").getUri());
        assertEquals(LocalDateTime.of(2022, 4, 21, 20, 41), processed.get("ts_129011v170000p0.zip").getDateTime());
        assertEquals(LocalDateTime.of(2022, 4, 21, 20, 41), processed.get("ts_129011_readme.txt").getDateTime());
        assertEquals(List.of("1 of 5 links are not recognized in the listing: '" + this.path() + "'"),
                appender.list.stream()
                        .filter(event -> event.getLevel() == Level.WARN)
                        .map(ILoggingEvent::getFormattedMessage)
                        .collect(Collectors.toList()));
    }

    void processFileTest(@NonNull String name) throws IOException {
        startUp(name);
        var completableFuture = assertDoesNotThrow(() -> this.textHtmlProcessor(this.page).process());
        assertNotNull(completableFuture);
        var pathsStream = assertDoesNotThrow(completableFuture::join);
        assertEquals(this.paths, pathsStream.collect(Collectors.toList()));
        assertEquals(this.excludes, this.excluded);
    }

    private <T> Supplier<T> nullRef() {
        return () -> null;
    }

    private ProcessorFactory<CompletableFuture<Stream<Path>>> processorFactory;
    private Consumer<Collection<URI>> onExpires;
    private Collection<Path> paths;
    private Collection<Path> excludes;
    private Collection<Path> excluded;

    @Override
    URI uri() {
        return URI.create("http://localhost/deliver/etsi_ts/129000_129099/129011/");
    }

    void startUp(@NonNull String name) throws IOException {
        super.startUp(name+".html");
        this.paths = RESULTS_MAP.get(name + "-paths");
        this.excludes = RESULTS_MAP.get(name + "-excludes");
        this.excluded = null;
        this.processorFactory = spy(new FooProcessorFactory());
        this.onExpires = list -> this.excluded = list.stream().map(uri -> Path.of(uri.getPath())).collect(Collectors.toList());
    }

    private @NonNull TextHtmlProcessor textHtmlProcessor(@NonNull Page page) {
        return new TextHtmlProcessor(page, this.processorFactory, new LinkPolicy(URI.create("http://localhost/deliver/")), null, this.onExpires);
    }

    @AfterEach
    void tearDown() {
        try {
            super.tearDown();
        } finally {
            this.onExpires = null;
            this.paths = null;
            this.excludes = null;
            this.excluded = null;
            this.processorFactory = null;
        }
    }

    static class FooProcessorFactory implements ProcessorFactory<CompletableFuture<Stream<Path>>> {

        final List<HREF> processed = Collections.synchronizedList(new ArrayList<>());

        @NonNull
        @Override
        public Processor<CompletableFuture<Stream<Path>>> processor(@NonNull Page page) {
            processed.add(page.getHref());
            return new FooProcessor(page.path());
        }
    }

    @AllArgsConstructor
    static class FooProcessor implements Processor<CompletableFuture<Stream<Path>>> {

        @Getter(value = AccessLevel.PRIVATE)
        private final Path path;

        @NonNull
        @Override
        public CompletableFuture<Stream<Path>> process() {
            return CompletableFuture.completedFuture(Stream.of(this.getPath()));
        }
    }

    /*  files:

    + /deliver/etsi_ts/129000_129099/129078/17.00.00_60/ts_129078v170000p.pdf
    + /deliver/etsi_ts/129000_129099/129078/17.00.00_60/ts_129078v170000p0.zip
*/
    private static final Collection<Path> FILES_PATHS = Stream
            .of("ts_129078v170000p.pdf", "ts_129078v170000p0.zip")
            .map(fileName -> Path.of("/deliver/etsi_ts/129000_129099/129078/17.00.00_60", fileName))
            .collect(Collectors.toList());

    /*  non-version:

        + /deliver/etsi_ts/129000_129099/129002
        + /deliver/etsi_ts/129000_129099/129006
        + /deliver/etsi_ts/129000_129099/129007
        + /deliver/etsi_ts/129000_129099/129010
        + /deliver/etsi_ts/129000_129099/129011
        + /deliver/etsi_ts/129000_129099/129013
        + /deliver/etsi_ts/129000_129099/129016
        + /deliver/etsi_ts/129000_129099/129018
        + /deliver/etsi_ts/129000_129099/129060
        + /deliver/etsi_ts/129000_129099/129061
        + /deliver/etsi_ts/129000_129099/129078
        + /deliver/etsi_ts/129000_129099/129079
    */

    private static final Collection<Path> NON_VERSION_PATHS = Stream
            .of( "129002", "129006", "129007", "129010", "129011", "129013",
                    "129016", "129018", "129060", "129061", "129078", "129079")
            .map(fileName -> Path.of("/deliver/etsi_ts/129000_129099", fileName))
            .collect(Collectors.toList());

    /*  versions:

        + /deliver/etsi_ts/129000_129099/129011/17.00.00_60

        - /deliver/etsi_ts/129000_129099/129011/03.00.00_60
        - /deliver/etsi_ts/129000_129099/129011/04.00.00_60
        - /deliver/etsi_ts/129000_129099/129011/04.00.01_60
        - /deliver/etsi_ts/129000_129099/129011/05.00.00_60
        - /deliver/etsi_ts/129000_129099/129011/06.00.00_60
        - /deliver/etsi_ts/129000_129099/129011/07.00.00_60
        - /deliver/etsi_ts/129000_129099/129011/08.00.00_60
        - /deliver/etsi_ts/129000_129099/129011/09.00.00_60
        - /deliver/etsi_ts/129000_129099/129011/10.00.00_60
        - /deliver/etsi_ts/129000_129099/129011/11.00.00_60
        - /deliver/etsi_ts/129000_129099/129011/12.00.00_60
        - /deliver/etsi_ts/129000_129099/129011/13.00.00_60
        - /deliver/etsi_ts/129000_129099/129011/14.00.00_60
        - /deliver/etsi_ts/129000_129099/129011/15.00.00_60
        - /deliver/etsi_ts/129000_129099/129011/16.00.00_60]
    */

    private static final Collection<Path> VERSIONS_PATHS = List.of(Path.of("/deliver/etsi_ts/129000_129099/129011/17.00.00_60"));

    private static final Collection<Path> VERSION_EXCLUDES = Stream
            .of("03.00.00_60", "04.00.00_60", "04.00.01_60", "05.00.00_60", "06.00.00_60", "07.00.00_60",
                    "08.00.00_60", "09.00.00_60", "10.00.00_60", "11.00.00_60", "12.00.00_60", "13.00.00_60",
                    "14.00.00_60", "15.00.00_60", "16.00.00_60")
            .map(fileName -> Path.of("/deliver/etsi_ts/129000_129099/129011", fileName))
            .collect(Collectors.toList());

    /*  hostile: links outside the mirrored folder are skipped, a duplicate is processed once

        + /deliver/etsi_ts/129000_129099/129011/ts_129011v170000p.pdf
        + /deliver/etsi_ts/129000_129099/129011/17.00.00_60

        - /deliver/etsi_ts/129000_129099/129011/16.00.00_60
    */

    private static final Collection<Path> HOSTILE_PATHS = List.of(
            Path.of("/deliver/etsi_ts/129000_129099/129011/ts_129011v170000p.pdf"),
            Path.of("/deliver/etsi_ts/129000_129099/129011/17.00.00_60"));

    private static final Collection<Path> HOSTILE_EXCLUDES = List.of(Path.of("/deliver/etsi_ts/129000_129099/129011/16.00.00_60"));

    /*  versions-unpadded: the actual version is chosen by number, only strictly older versions expire once

        + /deliver/etsi_ts/129000_129099/129011/10.00.00_60

        - /deliver/etsi_ts/129000_129099/129011/9.00.00_60
        - /deliver/etsi_ts/129000_129099/129011/8.05.00_60
    */

    private static final Collection<Path> UNPADDED_VERSIONS_PATHS = List.of(Path.of("/deliver/etsi_ts/129000_129099/129011/10.00.00_60"));

    private static final Collection<Path> UNPADDED_VERSIONS_EXCLUDES = Stream.of("9.00.00_60", "8.05.00_60")
            .map(fileName -> Path.of("/deliver/etsi_ts/129000_129099/129011", fileName))
            .collect(Collectors.toList());

    /*  relative: entities are decoded, relative links are resolved against the listing page,
        the folder link gets a trailing '/', 'pm' is recognized, one line of an unknown format is reported

        + /deliver/etsi_ts/129000_129099/129011/ts&annex.pdf
        + /deliver/etsi_ts/129000_129099/129011/ts_129011v170000p0.zip
        + /deliver/etsi_ts/129000_129099/129011/ts_129011_readme.txt
        + /deliver/etsi_ts/129000_129099/129011/17.00.00_60
    */

    private static final Collection<Path> RELATIVE_PATHS = Stream
            .of("ts&annex.pdf", "ts_129011v170000p0.zip", "ts_129011_readme.txt", "17.00.00_60")
            .map(fileName -> Path.of("/deliver/etsi_ts/129000_129099/129011", fileName))
            .collect(Collectors.toList());

    private static final LinkPolicy LINK_POLICY = new LinkPolicy(URI.create("http://localhost/deliver/"));

    private static final Map<String, Collection<Path>> RESULTS_MAP = Map.of(
            "files-paths", FILES_PATHS,
            "non-version-paths", NON_VERSION_PATHS,
            "versions-paths", VERSIONS_PATHS,
            "versions-excludes", VERSION_EXCLUDES,
            "hostile-paths", HOSTILE_PATHS,
            "hostile-excludes", HOSTILE_EXCLUDES,
            "versions-unpadded-paths", UNPADDED_VERSIONS_PATHS,
            "versions-unpadded-excludes", UNPADDED_VERSIONS_EXCLUDES,
            "relative-paths", RELATIVE_PATHS);

}