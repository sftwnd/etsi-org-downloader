package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Factory to get transformer by name
 * @param <R> Type of data result
 */
@FunctionalInterface
public interface ProcessorFactory<R> {

    @NonNull
     Processor<R> processor(@NonNull Page page);

}