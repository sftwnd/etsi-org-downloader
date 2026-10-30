package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Chooses the processor of a resource: a folder is a listing, a log or temporary file is skipped, and any other file
 * goes to the processor of the files, which saves it in a run and plans it in a dry run
 */
@Getter
@Slf4j
public class ComplexProcessorFactory implements ProcessorFactory<CompletableFuture<Summary>> {

    private final ProcessorFactory<CompletableFuture<Summary>> files;
    private final LinkPolicy linkPolicy;
    private final Executor executor;
    private final Function<Collection<URI>, Summary> onExpires;

    /**
     * Constructor of the processor factory
     * @param files processor factory of the files
     * @param linkPolicy policy of the links to follow
     * @param executor executor of the resource processing
     * @param onExpires handler of the expired resources with the summary of their removal
     */
    public ComplexProcessorFactory(@NonNull ProcessorFactory<CompletableFuture<Summary>> files,
                                   @NonNull LinkPolicy linkPolicy,
                                   @NonNull Executor executor,
                                   @Nullable Function<Collection<URI>, Summary> onExpires) {
        this.files = Objects.requireNonNull(files, "ComplexProcessorFactory::new - files is null");
        this.linkPolicy = Objects.requireNonNull(linkPolicy, "ComplexProcessorFactory::new - linkPolicy is null");
        this.executor = Objects.requireNonNull(executor, "ComplexProcessorFactory::new - executor is null");
        this.onExpires = onExpires;
    }

    /**
     * Instantiate text/html Processor from the page reference
     * @param page the page reference
     * @return Processor to load text/html, parse and initialize child files loading
     */
    private Processor<CompletableFuture<Summary>> textHtmlProcessor(@NonNull Page page) {
        return new TextHtmlProcessor(page, this, linkPolicy, executor, onExpires);
    }

    /**
     * Get processor for page
     * @param page page to transform
     * @return processor for age
     */
    @Override
    public @NonNull Processor<CompletableFuture<Summary>> processor(@NonNull Page page) {
        return ! page.getHref().isRegularFile() ? this.textHtmlProcessor(page)
                : page.fileName().endsWith(".log") || page.fileName().endsWith(".tmp") || page.fileName().endsWith(".temp") ? EmptyProcessor.DEFAULT
                : this.files.processor(page);
    }

}
