package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class TextHtmlProcessorTest extends AbstractFileSourceTest {

    @Test
    @SuppressWarnings("unchecked")
    void textHtmlProcessorNullPageTest() {
        assertThrows(
                NullPointerException.class,
                () -> new TextHtmlProcessor(this.<Page>nullRef().get(), mock(ProcessorFactory.class), mock(Executor.class), mock(Consumer.class))
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
                        mock(Executor.class),
                        mock(Consumer.class)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void textHtmlProcessorTest() {
        assertDoesNotThrow(
                () -> new TextHtmlProcessor(mock(Page.class), mock(ProcessorFactory.class), null, null)
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
    private Consumer<Collection<Path>> onExpires;
    private Collection<Path> paths;
    private Collection<Path> excludes;
    private Collection<Path> excluded;

    void startUp(@NonNull String name) throws IOException {
        super.startUp(name+".html");
        this.paths = RESULTS_MAP.get(name + "-paths");
        this.excludes = RESULTS_MAP.get(name + "-excludes");
        this.excluded = null;
        this.processorFactory = spy(new FooProcessorFactory());
        this.onExpires = list -> this.excluded = list;
    }

    private @NonNull TextHtmlProcessor textHtmlProcessor(@NonNull Page page) {
        return new TextHtmlProcessor(page, this.processorFactory, null, this.onExpires);
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
        @NonNull
        @Override
        public Processor<CompletableFuture<Stream<Path>>> processor(@NonNull Page page) {
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

    private static final Map<String, Collection<Path>> RESULTS_MAP = Map.of(
            "files-paths", FILES_PATHS,
            "non-version-paths", NON_VERSION_PATHS,
            "versions-paths", VERSIONS_PATHS,
            "versions-excludes", VERSION_EXCLUDES);

}