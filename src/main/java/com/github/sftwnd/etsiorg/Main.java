package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
    private static final String THREADS_PROPERTY = "threads";
    private static final String ZONE_PROPERTY = "zone";

    public static void main(String[] args) {
        LinkPolicy linkPolicy;
        int threads;
        ZoneId zone;
        try {
            linkPolicy = new LinkPolicy(new URI(Optional.ofNullable(System.getProperty(URI_PROPERTY))
                    .filter(Predicate.not(String::isBlank))
                    .orElse(DEFAULT_URI)));
            threads = threads(System.getProperty(THREADS_PROPERTY));
            zone = zone(System.getProperty(ZONE_PROPERTY));
        } catch (URISyntaxException | IllegalArgumentException exception) {
            logger.error("Invalid configuration: {}", Printable.of(exception.getMessage()));
            return;
        }
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            Path dest = Optional.ofNullable(System.getProperty(DEST_PROPERTY))
                    .filter(Predicate.not(String::isBlank))
                    .map(Path::of)
                    .orElseGet(() -> Path.of("."));
            Destination destination = new Destination(dest);
            logger.info("Mirroring '{}' to '{}' with {} threads, listing time zone: {}",
                    Printable.of(linkPolicy.getRoot()), Printable.of(destination.getRoot()), threads, zone);
            var processorFactory = new ComplexProcessorFactory(destination, zone, linkPolicy, executor, new ExpiredRemover(destination));
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

    /**
     * Number of the threads that process the resources, which is also the limit of the simultaneous requests
     * @param value value of the property or null
     * @return positive number of the threads, the number of available processors by default
     * @throws IllegalArgumentException if the value is not a positive integer
     */
    static int threads(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return Runtime.getRuntime().availableProcessors();
        }
        try {
            int threads = Integer.parseInt(value.trim());
            if (threads > 0) {
                return threads;
            }
        } catch (NumberFormatException ignore) {
            // reported below
        }
        throw new IllegalArgumentException("Number of threads has to be a positive integer: " + Printable.of(value));
    }

    /**
     * Time zone of the listing time: www.etsi.org lists the time in UTC
     * @param value value of the property or null
     * @return time zone, UTC by default
     * @throws IllegalArgumentException if the value is not a time zone id
     */
    static @NonNull ZoneId zone(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(value.trim());
        } catch (DateTimeException dtex) {
            throw new IllegalArgumentException("Unknown time zone: " + Printable.of(value), dtex);
        }
    }

}