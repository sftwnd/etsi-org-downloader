package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * page processor
 * @param <R> Type of data result
 */
public interface Processor<R> {

    /**
     * Process loaded data
     * @return result of process
     */
    @NonNull R process();

}