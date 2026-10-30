package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Objects;

/**
 * Result of the processing of a resource tree: the numbers of the downloaded and up to date files, of the removed
 * expired versions and of the failures. A dry run counts what a run would download and remove
 */
@Getter
@EqualsAndHashCode
@ToString
public final class Summary {

    /**
     * Nothing has been processed
     */
    public static final Summary EMPTY = new Summary(0L, 0L, 0L, 0L, 0L);

    /**
     * One file is up to date
     */
    public static final Summary UP_TO_DATE = new Summary(0L, 0L, 1L, 0L, 0L);

    /**
     * One resource has failed
     */
    public static final Summary FAILED = new Summary(0L, 0L, 0L, 0L, 1L);

    /**
     * Number of the downloaded files
     */
    private final long downloaded;

    /**
     * Size of the downloaded files in bytes
     */
    private final long bytes;

    /**
     * Number of the files that are up to date
     */
    private final long upToDate;

    /**
     * Number of the expired versions whose local copies have been removed
     */
    private final long removed;

    /**
     * Number of the resources that have failed: files, listings or their entries
     */
    private final long failed;

    private Summary(long downloaded, long bytes, long upToDate, long removed, long failed) {
        this.downloaded = downloaded;
        this.bytes = bytes;
        this.upToDate = upToDate;
        this.removed = removed;
        this.failed = failed;
    }

    /**
     * Summary of a downloaded file
     * @param bytes size of the file
     * @return summary of one downloaded file
     */
    public static @NonNull Summary downloaded(long bytes) {
        return new Summary(1L, notNegative(bytes, "bytes"), 0L, 0L, 0L);
    }

    /**
     * Summary of the removed expired versions
     * @param count number of the expired versions whose local copies have been removed
     * @return summary with the removed versions only
     */
    public static @NonNull Summary removed(long count) {
        return notNegative(count, "count") == 0 ? EMPTY : new Summary(0L, 0L, 0L, count, 0L);
    }

    /**
     * Summary of the failed resources
     * @param count number of the failed resources
     * @return summary with the failures only
     */
    public static @NonNull Summary failed(long count) {
        return notNegative(count, "count") == 0 ? EMPTY : new Summary(0L, 0L, 0L, 0L, count);
    }

    /**
     * Sum of the summaries
     * @param summary summary to add
     * @return summary with the sums of the numbers
     */
    public @NonNull Summary plus(@NonNull Summary summary) {
        Objects.requireNonNull(summary, "Summary::plus - summary is null");
        return new Summary(this.downloaded + summary.downloaded, this.bytes + summary.bytes, this.upToDate + summary.upToDate,
                this.removed + summary.removed, this.failed + summary.failed);
    }

    private static long notNegative(long value, @NonNull String name) {
        if (value < 0) {
            throw new IllegalArgumentException("Summary - negative " + name + ": " + value);
        }
        return value;
    }

}
