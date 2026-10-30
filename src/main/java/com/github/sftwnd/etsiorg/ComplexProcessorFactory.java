package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Choose transformer by file header and delegate transform operation
 */
@Getter
@Slf4j
public class ComplexProcessorFactory implements ProcessorFactory<CompletableFuture<Stream<Path>>> {

    private final Destination destination;
    private final ZoneId zone;
    private final LinkPolicy linkPolicy;
    private final Executor executor;
    private final Consumer<Collection<URI>> onExpires;

    public ComplexProcessorFactory(@NonNull Destination destination,
                                   @NonNull ZoneId zone,
                                   @NonNull LinkPolicy linkPolicy,
                                   @NonNull Executor executor,
                                   @Nullable Consumer<Collection<URI>> onExpires) {
        this.destination = Objects.requireNonNull(destination, "ComplexProcessorFactory::new - destination is null");
        this.zone = Objects.requireNonNull(zone, "ComplexProcessorFactory::new - zone is null");
        this.linkPolicy = Objects.requireNonNull(linkPolicy, "ComplexProcessorFactory::new - linkPolicy is null");
        this.executor = Objects.requireNonNull(executor, "ComplexProcessorFactory::new - executor is null");
        this.onExpires = onExpires;
    }

    /**
     * Instantiate File Save Processor from the page reference
     * @param page the page reference
     * @return Processor to load file from the page reference
     */
    private Processor<CompletableFuture<Stream<Path>>> fileSaveProcessor(@NonNull Page page) {
        return new FileSaveProcessor(destination, zone, page);
    }

    /**
     * Instantiate text/html Processor from the page reference
     * @param page the page reference
     * @return Processor to load text/html, parse and initialize child files loading
     */
    private Processor<CompletableFuture<Stream<Path>>> textHtmlProcessor(@NonNull Page page) {
        return new TextHtmlProcessor(page, this, linkPolicy, executor, onExpires);
    }

    /**
     * Get processor for page
     * @param page page to transform
     * @return processor for age
     */
    @Override
    public @NonNull Processor<CompletableFuture<Stream<Path>>> processor(@NonNull Page page) {
        return ! page.getHref().isRegularFile() ? this.textHtmlProcessor(page)
                : page.fileName().endsWith(".log") || page.fileName().endsWith(".tmp") || page.fileName().endsWith(".temp") ? EmptyProcessor.DEFAULT
                : this.fileSaveProcessor(page);
    }

}