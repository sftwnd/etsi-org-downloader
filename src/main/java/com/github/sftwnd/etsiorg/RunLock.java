package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Exclusive lock of a destination folder for a run: while a run holds it, another run of this or another process
 * does not get it. The lock is a lock of the operating system on the lock file, so it is released when the process
 * exits however it exits; the file itself is kept
 */
@Slf4j
public final class RunLock implements AutoCloseable {

    /**
     * Lock files held by the runs of this process: closing another channel of a locked file would release the lock
     * of the process on some systems, so a held file is not opened again
     */
    private static final Set<Path> HELD = ConcurrentHashMap.newKeySet();

    private final Path file;
    private final FileChannel channel;

    private RunLock(@NonNull Path file, @NonNull FileChannel channel) {
        this.file = file;
        this.channel = channel;
    }

    /**
     * Lock the file for a run
     * @param file lock file, created if it does not exist
     * @return the lock, or empty if another run holds it
     * @throws IOException if the file cannot be opened or locked
     */
    public static @NonNull Optional<RunLock> tryAcquire(@NonNull Path file) throws IOException {
        Path key = Objects.requireNonNull(file, "RunLock::tryAcquire - file is null").toAbsolutePath().normalize();
        if (!HELD.add(key)) {
            return Optional.empty();
        }
        boolean acquired = false;
        try {
            FileChannel channel = FileChannel.open(key, CREATE, WRITE);
            try {
                acquired = channel.tryLock() != null;
                return acquired ? Optional.of(new RunLock(key, channel)) : Optional.empty();
            } finally {
                if (!acquired) {
                    channel.close();
                }
            }
        } finally {
            if (!acquired) {
                HELD.remove(key);
            }
        }
    }

    /**
     * Release the lock; a failure is only logged, since the lock is released at the latest when the process exits
     */
    @Override
    public void close() {
        try {
            this.channel.close();
        } catch (IOException ioex) {
            logger.warn("Unable to release the lock '{}'. Cause: {} {}", Printable.of(this.file), ioex.getClass().getSimpleName(), Printable.of(ioex.getMessage()));
        } finally {
            HELD.remove(this.file);
        }
    }

}
