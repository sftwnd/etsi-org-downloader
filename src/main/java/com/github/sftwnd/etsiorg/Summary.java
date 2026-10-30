package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Objects;

/**
 * Result of the processing of a resource tree: the numbers of the downloaded and up to date files and of the failures
 */
@Getter
@EqualsAndHashCode
@ToString
public final class Summary {

    /**
     * Nothing has been processed
     */
    public static final Summary EMPTY = new Summary(0L, 0L, 0L);

    /**
     * One file has been downloaded
     */
    public static final Summary DOWNLOADED = new Summary(1L, 0L, 0L);

    /**
     * One file is up to date
     */
    public static final Summary UP_TO_DATE = new Summary(0L, 1L, 0L);

    /**
     * One resource has failed
     */
    public static final Summary FAILED = new Summary(0L, 0L, 1L);

    /**
     * Number of the downloaded files
     */
    private final long downloaded;

    /**
     * Number of the files that are up to date
     */
    private final long upToDate;

    /**
     * Number of the resources that have failed: files, listings or their entries
     */
    private final long failed;

    private Summary(long downloaded, long upToDate, long failed) {
        this.downloaded = downloaded;
        this.upToDate = upToDate;
        this.failed = failed;
    }

    /**
     * Summary of the failed resources
     * @param count number of the failed resources
     * @return summary with the failures only
     */
    public static @NonNull Summary failed(long count) {
        if (count < 0) {
            throw new IllegalArgumentException("Summary::failed - negative count: " + count);
        }
        return count == 0 ? EMPTY : new Summary(0L, 0L, count);
    }

    /**
     * Sum of the summaries
     * @param summary summary to add
     * @return summary with the sums of the numbers
     */
    public @NonNull Summary plus(@NonNull Summary summary) {
        Objects.requireNonNull(summary, "Summary::plus - summary is null");
        return new Summary(this.downloaded + summary.downloaded, this.upToDate + summary.upToDate, this.failed + summary.failed);
    }

}
