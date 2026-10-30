package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.concurrent.CompletableFuture;

/**
 * Empty Path loader
 */
public class EmptyProcessor implements Processor<CompletableFuture<Summary>> {

    public static final Processor<CompletableFuture<Summary>> DEFAULT = new EmptyProcessor();

    private EmptyProcessor() {
    }

    /**
     * Do nothing processor
     * @return completed future with the empty summary
     */
    @NonNull
    @Override
    public CompletableFuture<Summary> process() {
        return CompletableFuture.completedFuture(Summary.EMPTY);
    }

}