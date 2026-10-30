package com.github.sftwnd.etsiorg;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class Main {

    private static final String DEFAULT_URI = "https://www.etsi.org/deliver/";
        // "https://www.etsi.org/deliver/etsi_ts/129000_129099/";
        // "https://www.etsi.org/deliver/etsi_ts/129000_129099/129013/";
        // "https://www.etsi.org/deliver/etsi_ts/129000_129099/129002/";
        // "https://www.etsi.org/deliver/etsi_ts/129000_129099/129079";
        // "https://www.etsi.org/deliver/etsi_ts/";
        // "https://www.etsi.org/deliver/etsi_ts/129000_129099/129079/10.07.00_60";
        // "https://www.etsi.org/deliver/etsi_ts/129000_129099/129078/17.00.00_60";
        // "https://www.etsi.org/deliver/etsi_ts/136500_136599/13652103/17.01.00_60/";

    private static final String URI_PROPERTY = "uri";
    private static final String DEST_PROPERTY = "dest";

    public static void main(String[] args) {
        LinkPolicy linkPolicy;
        try {
            linkPolicy = new LinkPolicy(new URI(Optional.ofNullable(System.getProperty(URI_PROPERTY))
                    .filter(Predicate.not(String::isBlank))
                    .orElse(DEFAULT_URI)));
        } catch (URISyntaxException | IllegalArgumentException exception) {
            logger.error("Invalid start uri: {}", Printable.of(exception.getMessage()));
            return;
        }
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        try {
            Path dest = Optional.ofNullable(System.getProperty(DEST_PROPERTY))
                    .filter(Predicate.not(String::isBlank))
                    .map(Path::of)
                    .orElseGet(() -> Path.of("."));
            Destination destination = new Destination(dest);
            var processorFactory = new ComplexProcessorFactory(destination, linkPolicy, executor, new ExpiredRemover(destination));
            linkPolicy.visit(linkPolicy.getRoot());
            processorFactory
                    .processor(Page.of(HREF.builder().uri(linkPolicy.getRoot()).build()))
                    .process()
                    .thenApply(Stream::sorted)
                    .thenApply(stream -> stream.map(Path::toString))
                    .thenApply(Optional::of)
                    .join()
                    .map(stream -> stream.collect(Collectors.toList()))
                    .filter(Predicate.not(Collection::isEmpty))
                    .ifPresentOrElse(
                            paths -> logger.info("There are {} files has been loaded", paths.size()),
                            () -> logger.info("No files were uploaded")
                    );
        } finally {
            executor.shutdown();
        }
    }

}