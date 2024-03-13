package com.github.sftwnd.etsiorg;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Slf4j
public class Main {

    private static final String DEFAULT_URI = "https://www.etsi.org/deliver/etsi_ts/129000_129099/";
        // "https://www.etsi.org/deliver/etsi_ts/129000_129099/129013/";
        // "https://www.etsi.org/deliver/etsi_ts/129000_129099/129002/";
        // "https://www.etsi.org/deliver/etsi_ts/129000_129099/129079";

    public static void main(String[] args) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        try {
            String dest = Optional.ofNullable(System.getProperty("dest")).orElse(".");
            String uri = Optional.ofNullable(System.getProperty("uri")).orElse(DEFAULT_URI);
            Loader.builder()
                    .root(dest)
                    .executor(executor)
                    .build()
                    .load(URI.create(uri))
                    .thenApply(Optional::of)
                    .join()
                    .map(Stream::sorted)
                    .map(stream -> stream.map(Path::toString))
                    .map(Stream::toList)
                    .filter(Predicate.not(Collection::isEmpty))
                    .ifPresentOrElse(
                            paths -> logger.info("There are {} files has been loaded: {}", paths.size(), String.join("\n\t", paths)),
                            () -> logger.info("No files were uploaded")
                    );
        } finally {
            Thread.sleep(12000);
            executor.shutdown();
        }
    }

}