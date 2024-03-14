package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
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

    private final Path root;
    private final Executor executor;
    private final Consumer<Collection<Path>> onExpires;

    public ComplexProcessorFactory(@Nullable Path root, @Nullable Executor executor, @Nullable Consumer<Collection<Path>> onExpires) {
        this.root = Objects.requireNonNull(root, "ComplexProcessorFactory::new - root path is null");
        this.executor = executor;
        this.onExpires = onExpires;
    }

    /**
     * Instantiate File Save Processor from the page reference
     * @param page the page reference
     * @return Processor to load file from the page reference
     */
    private Processor<CompletableFuture<Stream<Path>>> fileSaveProcessor(@NonNull Page page) {
        return new FileSaveProcessor(root, page);
    }

    /**
     * Instantiate text/html Processor from the page reference
     * @param page the page reference
     * @return Processor to load text/html, parse and initialize child files loading
     */
    private Processor<CompletableFuture<Stream<Path>>> textHtmlProcessor(@NonNull Page page) {
        return new TextHtmlProcessor(page, this, executor, onExpires);
    }

    /**
     * Get processor for page
     * @param page page to transform
     * @return processor for age
     */
    @Override
    public @NonNull Processor<CompletableFuture<Stream<Path>>> processor(@NonNull Page page) {
        return ! page.getHref().isRegularFile() ? this.textHtmlProcessor(page)
                : page.fileName().endsWith(".log") || page.fileName().endsWith(".tmp") ? EmptyProcessor.DEFAULT
                : this.fileSaveProcessor(page);

    }

}