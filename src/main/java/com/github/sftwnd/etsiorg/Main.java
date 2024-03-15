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
            var processorFactory = new ComplexProcessorFactory(dest, executor, getOnnExpires(dest));
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

    private static Consumer<Collection<Path>> getOnnExpires(@NonNull Path root) {
        return expires -> expires.stream()
                .map(path -> Path.of(root.toString(), path.toString()))
                .sorted()
                .forEach(Main::onExpire);
    }

    private static void onExpire(@NonNull Path expired) {
        try {
            if (Files.exists(expired)) {
                delete(expired);
            }
        } catch (Exception exception) {
            logger.warn("Unable to delete expired path: '{}'. Cause: {} {}", expired, exception.getClass().getSimpleName(), exception.getMessage());
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
                logger.debug("{}: '{}' has been deleted", isRegular ? "File" : "Directory", path);
            }
        } catch (IOException ioex) {
            logger.warn("Unable to delete {}: '{}'. Cause: {} {}", isRegular ? "File" : "Directory", path, ioex.getClass().getSimpleName(), ioex.getMessage());
        }
    }

}