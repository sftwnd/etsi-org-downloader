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
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

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

    /**
     * Exit status of a run without failures
     */
    static final int SUCCESS = 0;

    /**
     * Exit status of a run in which some resources have failed
     */
    static final int FAILURE = 1;

    /**
     * Exit status of a run with an invalid configuration
     */
    static final int INVALID_CONFIGURATION = 2;

    public static void main(String[] args) {
        System.exit(run());
    }

    /**
     * Mirror the site folder with the configuration from the system properties
     * @return exit status: {@link #SUCCESS}, {@link #FAILURE} or {@link #INVALID_CONFIGURATION}
     */
    static int run() {
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
            return INVALID_CONFIGURATION;
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
            Summary summary = processorFactory
                    .processor(Page.of(HREF.builder().uri(linkPolicy.getRoot()).build()))
                    .process()
                    .join();
            logger.info("Files downloaded: {}, up to date: {}, failed: {}", summary.getDownloaded(), summary.getUpToDate(), summary.getFailed());
            return summary.getFailed() == 0 ? SUCCESS : FAILURE;
        } catch (RuntimeException rex) {
            logger.error("Mirroring has failed. Cause: {} {}", rex.getClass().getSimpleName(), Printable.of(rex.getMessage()));
            return FAILURE;
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