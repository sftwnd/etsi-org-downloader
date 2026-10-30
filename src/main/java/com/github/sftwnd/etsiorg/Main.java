package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
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
            var processorFactory = new ComplexProcessorFactory(destination, linkPolicy, executor, getOnExpires(destination));
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

    private static Consumer<Collection<URI>> getOnExpires(@NonNull Destination destination) {
        return expires -> expires.stream()
                .map(uri -> localPath(destination, uri))
                .flatMap(Optional::stream)
                .sorted()
                .forEach(Main::onExpire);
    }

    private static Optional<Path> localPath(@NonNull Destination destination, @NonNull URI uri) {
        try {
            return Optional.of(destination.pathOf(uri));
        } catch (IllegalArgumentException iaex) {
            logger.warn("Expired path is skipped. {}", iaex.getMessage());
            return Optional.empty();
        }
    }

    private static void onExpire(@NonNull Path expired) {
        try {
            if (Files.exists(expired)) {
                delete(expired);
            }
        } catch (Exception exception) {
            logger.warn("Unable to delete expired path: '{}'. Cause: {} {}", Printable.of(expired), exception.getClass().getSimpleName(), Printable.of(exception.getMessage()));
        }
    }

    @SneakyThrows
    private static void delete(@NonNull Path path) {
        boolean isRegular = Files.isRegularFile(path);
        if (!isRegular) {
            try (Stream<Path> files = Files.list(path).sorted()) {
                files.forEach(Main::delete);
            }
        }
        try {
            if (Files.deleteIfExists(path)) {
                logger.debug("{}: '{}' has been deleted", isRegular ? "File" : "Directory", Printable.of(path));
            }
        } catch (IOException ioex) {
            logger.warn("Unable to delete {}: '{}'. Cause: {} {}", isRegular ? "File" : "Directory", Printable.of(path), ioex.getClass().getSimpleName(), Printable.of(ioex.getMessage()));
        }
    }

}