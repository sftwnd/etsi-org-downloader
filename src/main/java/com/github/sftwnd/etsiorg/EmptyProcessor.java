package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Empty Path loader
 */
public class EmptyProcessor implements Processor<CompletableFuture<Stream<Path>>> {

    public static final Processor<CompletableFuture<Stream<Path>>> DEFAULT = new EmptyProcessor();

    private EmptyProcessor() {
    }

    @NonNull
    @Override
    public CompletableFuture<Stream<Path>> process() {
        return CompletableFuture.completedFuture(Stream.empty());
    }

}