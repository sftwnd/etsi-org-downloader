package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Saves a file of the site to the destination folder. The file is downloaded to a sibling {@code .part} file,
 * which is resumed after an interruption and replaces the local copy only when it is complete
 */
@Getter
@Slf4j
public class FileSaveProcessor implements Processor<CompletableFuture<Stream<Path>>> {

    private static final int BUFFER_SIZE = 64 << 10;

    /**
     * Suffix of the file that is being downloaded
     */
    static final String PART_SUFFIX = ".part";

    private final Destination destination;
    private final ZoneId zone;
    private final Page page;

    /**
     * Constructor of the file save processor
     * @param destination destination folder of the mirror
     * @param zone time zone of the listing time
     * @param page page of the file
     */
    FileSaveProcessor(@NonNull Destination destination, @NonNull ZoneId zone, @NonNull Page page) {
        this.destination = Objects.requireNonNull(destination, "FileSaveProcessor::new - destination is null");
        this.zone = Objects.requireNonNull(zone, "FileSaveProcessor::new - zone is null");
        this.page = Objects.requireNonNull(page, "FileSaveProcessor::new - page is null");
    }

    /**
     * Save file and return completion future (in the caller thread)
     * @return Completed future with list with one path to the loaded file
     */
    @NonNull
    @Override
    public CompletableFuture<Stream<Path>> process() {
        return CompletableFuture.completedFuture(Optional.ofNullable(this.saveFile()).stream());
    }

    /**
     * Save the file unless the local copy is up to date
     * @return path to the local copy of the file or null if it cannot be saved
     */
    private @Nullable Path saveFile() {
        final Path filePath;
        try {
            filePath = this.getDestination().pathOf(this.getPage().getUri());
        } catch (IllegalArgumentException iaex) {
            logger.error("Unable to save file. {}", iaex.getMessage());
            return null;
        }
        try {
            Files.createDirectories(filePath.getParent());
            long contentLength = this.getPage().contentLength();
            if (!isUpToDate(filePath, contentLength)) {
                download(filePath, contentLength);
                logger.info("File: '{}' has been saved", Printable.of(filePath));
            }
            return filePath;
        } catch (IOException ioex) {
            logger.error("Unable to save file: '{}'. Cause: {} {}", Printable.of(filePath), ioex.getClass().getSimpleName(), Printable.of(ioex.getMessage()));
            return null;
        }
    }

    /**
     * Check the local copy of the file; the time of an up to date copy is synchronized with the listing
     * @param filePath local path of the file
     * @param contentLength expected length of the file
     * @return true if the local copy has the expected length, false if it is missing or has to be downloaded again
     * @throws IOException if the path is occupied by something else than a regular file
     */
    private boolean isUpToDate(@NonNull Path filePath, long contentLength) throws IOException {
        if (Files.notExists(filePath, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (!Files.isRegularFile(filePath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Path exists and is not a regular file");
        }
        long fileSize = Files.size(filePath);
        if (fileSize != contentLength) {
            logger.warn("File: '{}' has size {} instead of {} and will be downloaded again", Printable.of(filePath), fileSize, contentLength);
            return false;
        }
        logger.debug("File: '{}' already exists.", Printable.of(filePath));
        syncFileTime(filePath, true);
        return true;
    }

    /**
     * Download the file to the {@code .part} file, resuming it if it exists, and replace the local copy with it
     * @param filePath local path of the file
     * @param contentLength expected length of the file
     * @throws IOException in the case of error; the {@code .part} file is kept to be resumed later
     */
    private void download(@NonNull Path filePath, long contentLength) throws IOException {
        Path partPath = filePath.resolveSibling(filePath.getFileName() + PART_SUFFIX);
        long readed = partLength(partPath, contentLength);
        if (readed > 0) {
            logger.info("Continue loading from offset {} of the file: '{}'", readed, Printable.of(filePath));
        }
        try (OutputStream outputStream = Files.newOutputStream(partPath, WRITE, CREATE, readed == 0 ? TRUNCATE_EXISTING : APPEND)) {
            byte[] buff = new byte[BUFFER_SIZE];
            while (readed < contentLength) {
                long offset = readed;
                try (InputStream inputStream = this.getPage().inputStreamFrom(offset)) {
                    int bufferOffset = 0;
                    while (readed < contentLength) {
                        int bytes = read(inputStream, buff, bufferOffset, readed, contentLength);
                        if (bytes < 0) {
                            if (bufferOffset > 0) {
                                outputStream.write(buff, 0, bufferOffset);
                            }
                            break;
                        }
                        bufferOffset += bytes;
                        readed += bytes;
                        if (bufferOffset == buff.length || readed == contentLength) {
                            outputStream.write(buff, 0, bufferOffset);
                            bufferOffset = 0;
                        }
                    }
                }
                if (readed == offset) {
                    throw new EOFException("No data has been received from offset " + offset);
                }
            }
        }
        syncFileTime(partPath, false);
        replace(partPath, filePath);
    }

    /**
     * Length of the downloaded part of the file
     * @param partPath path of the {@code .part} file
     * @param contentLength expected length of the file
     * @return length of the part to resume from or 0 if there is no part or it is longer than the file and is removed
     * @throws IOException if the part cannot be read or removed
     */
    private static long partLength(@NonNull Path partPath, long contentLength) throws IOException {
        if (!Files.isRegularFile(partPath, LinkOption.NOFOLLOW_LINKS)) {
            return 0L;
        }
        long partLength = Files.size(partPath);
        if (partLength > contentLength) {
            logger.warn("Downloaded part: '{}' is longer than the file and is removed", Printable.of(partPath));
            Files.delete(partPath);
            return 0L;
        }
        return partLength;
    }

    /**
     * Replace the local copy of the file with the downloaded one atomically if the file system supports it
     * @param partPath path of the downloaded file
     * @param filePath local path of the file
     * @throws IOException if the file cannot be replaced
     */
    private static void replace(@NonNull Path partPath, @NonNull Path filePath) throws IOException {
        try {
            Files.move(partPath, filePath, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException amnsex) {
            Files.move(partPath, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Read the next part of the file; a failed read ends the stream, so the download continues from the current offset
     * with a new connection
     * @param inputStream stream of the file
     * @param buff buffer to read to
     * @param bufferOffset offset in the buffer
     * @param readed number of the file bytes that have been read
     * @param contentLength length of the file
     * @return number of the read bytes or -1 if the stream has ended or failed
     */
    private int read(@NonNull InputStream inputStream, @NonNull byte[] buff, int bufferOffset, long readed, long contentLength) {
        try {
            return inputStream.read(buff, bufferOffset, buff.length - bufferOffset);
        } catch (IOException ioex) {
            logger.warn("Download has been interrupted at {} of {} bytes: '{}'. Cause: {}",
                    readed, contentLength, Printable.of(this.getPage().path()), Printable.of(ioex.getMessage()));
            return -1;
        }
    }

    /**
     * Set the time of the file from the listing. A failure is reported and does not affect the file
     * @param filePath path of the file
     * @param checkForChange report the change of the time of an existing file
     */
    private void syncFileTime(@NonNull Path filePath, boolean checkForChange) {
        LocalDateTime dateTime = this.getPage().getHref().getDateTime();
        if (dateTime == null) {
            return;
        }
        try {
            Instant listingTime = dateTime.atZone(this.getZone()).toInstant().truncatedTo(ChronoUnit.SECONDS);
            Instant fileTime = Files.getLastModifiedTime(filePath).toInstant().truncatedTo(ChronoUnit.SECONDS);
            if (!fileTime.equals(listingTime)) {
                FileTime listingFileTime = FileTime.from(listingTime);
                Files.setAttribute(filePath, "creationTime", listingFileTime);
                Files.setLastModifiedTime(filePath, listingFileTime);
                if (checkForChange) {
                    logger.warn("File: '{}' already exists. Creation time has been reset to: {}", Printable.of(filePath), dateTime);
                }
            }
        } catch (IOException ioex) {
            logger.warn("Unable to set the time of the file: '{}'. Cause: {} {}", Printable.of(filePath), ioex.getClass().getSimpleName(), Printable.of(ioex.getMessage()));
        }
    }

}
