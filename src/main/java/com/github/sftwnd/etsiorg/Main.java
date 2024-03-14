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

    private static final String DEFAULT_URI = // "https://www.etsi.org/deliver/";
        // "https://www.etsi.org/deliver/etsi_ts/129000_129099/";
        // "https://www.etsi.org/deliver/etsi_ts/129000_129099/129013/";
        // "https://www.etsi.org/deliver/etsi_ts/129000_129099/129002/";
           "https://www.etsi.org/deliver/etsi_ts/129000_129099/129079";
        // "https://www.etsi.org/deliver/etsi_ts/129000_129099/129079/10.07.00_60";
        // "https://www.etsi.org/deliver/etsi_ts/129000_129099/129078/17.00.00_60";

    private static final String URI_PROPERTY = "uri";
    private static final String DEST_PROPERTY = "dest";

    public static void main(String[] args) throws URISyntaxException {
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        try {
            Path dest = Optional.ofNullable(System.getProperty(DEST_PROPERTY))
                    .filter(Predicate.not(String::isBlank))
                    .map(Path::of)
                    .orElseGet(() -> Path.of("."));
            URI uri = new URI(Optional.ofNullable(System.getProperty(URI_PROPERTY))
                    .filter(Predicate.not(String::isBlank))
                    .orElse(DEFAULT_URI));
            var processorFactory = new ComplexProcessorFactory(dest, executor);
            processorFactory
                    .processor(Page.of(HREF.builder().uri(uri).build()))
                    .process()
                    .thenApply(Stream::sorted)
                    .thenApply(stream -> stream.map(Path::toString))
                    .thenApply(Optional::of)
                    .join()
                    .map(stream -> stream.collect(Collectors.toList()))
                    .filter(Predicate.not(Collection::isEmpty))
                    .ifPresentOrElse(
                            paths -> logger.info("There are {} files has been loaded:\n\t{}", paths.size(), String.join("\n\t", paths)),
                            () -> logger.info("No files were uploaded")
                    );
        } finally {
            executor.shutdown();
        }
    }

}