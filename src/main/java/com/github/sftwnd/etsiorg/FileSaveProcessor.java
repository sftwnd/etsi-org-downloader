package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Saves a file of the site to the destination folder. The file is downloaded to a sibling {@code .part} file bound to
 * the listing time of the file, which is resumed after an interruption and replaces the local copy only when it is complete
 */
@Getter
@Slf4j
public class FileSaveProcessor implements Processor<CompletableFuture<Summary>> {

    private static final int BUFFER_SIZE = 64 << 10;

    /**
     * Suffix of the file that is being downloaded
     */
    static final String PART_SUFFIX = ".part";

    /**
     * Listing time in the name of the {@code .part} file: a part of another version of the file is never resumed
     */
    static final DateTimeFormatter PART_STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    /**
     * Stamp of the listing time in the name of a {@code .part} file
     */
    private static final Pattern PART_STAMP_PATTERN = Pattern.compile("\\.\\d{12}" + Pattern.quote(PART_SUFFIX));

    /**
     * Largest difference between two time zones: a difference of the file time up to it in whole quarters of an hour
     * is a shift of the time zone, not a change of the file
     */
    private static final Duration MAX_ZONE_SHIFT = Duration.ofHours(26);
    private static final Duration ZONE_SHIFT_STEP = Duration.ofMinutes(15);

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
     * @return completed future with the summary of the file
     */
    @NonNull
    @Override
    public CompletableFuture<Summary> process() {
        return CompletableFuture.completedFuture(this.saveFile());
    }

    /**
     * Save the file unless the local copy is up to date
     * @return summary of the file: downloaded, up to date or empty if it has not been saved
     */
    private @NonNull Summary saveFile() {
        final Path filePath;
        try {
            filePath = this.getDestination().pathOf(this.getPage().getUri());
        } catch (IllegalArgumentException iaex) {
            logger.error("Unable to save file. {}", iaex.getMessage());
            return Summary.EMPTY;
        }
        try {
            Files.createDirectories(filePath.getParent());
            long contentLength = this.getPage().contentLength();
            if (isUpToDate(filePath, contentLength)) {
                return Summary.UP_TO_DATE;
            }
            if (download(filePath, contentLength)) {
                logger.info("File: '{}' has been saved", Printable.of(filePath));
                return Summary.DOWNLOADED;
            }
            return Summary.EMPTY;
        } catch (IOException ioex) {
            logger.error("Unable to save file: '{}'. Cause: {} {}", Printable.of(filePath), ioex.getClass().getSimpleName(), Printable.of(ioex.getMessage()));
            return Summary.EMPTY;
        }
    }

    /**
     * Check the local copy of the file: it is up to date if it has the size and the time from the listing.
     * A time that differs by a shift of the time zone is reset to the listing time
     * @param filePath local path of the file
     * @param contentLength expected length of the file
     * @return true if the local copy is up to date, false if it is missing or has to be downloaded again
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
        Instant listingTime = listingTime();
        if (listingTime != null) {
            Instant fileTime = Files.getLastModifiedTime(filePath).toInstant().truncatedTo(ChronoUnit.SECONDS);
            Duration difference = Duration.between(fileTime, listingTime);
            if (!difference.isZero() && !isZoneShift(difference)) {
                logger.warn("File: '{}' has time {} instead of {} and will be downloaded again", Printable.of(filePath), fileTime, listingTime);
                return false;
            }
        }
        logger.debug("File: '{}' already exists.", Printable.of(filePath));
        syncFileTime(filePath, true);
        return true;
    }

    /**
     * Check that the difference of the file time is a shift of the time zone the listing time was interpreted in
     * @param difference non-zero difference of the file time and the listing time
     * @return true for a difference up to {@link #MAX_ZONE_SHIFT} in whole quarters of an hour
     */
    private static boolean isZoneShift(@NonNull Duration difference) {
        Duration shift = difference.abs();
        return shift.compareTo(MAX_ZONE_SHIFT) <= 0 && shift.toSeconds() % ZONE_SHIFT_STEP.toSeconds() == 0;
    }

    /**
     * Download the file to the locked {@code .part} file, resuming it if it exists, and replace the local copy with it.
     * The lock is bound to the file itself, so it excludes other tasks writing the same file under another name
     * (e.g. on a case-insensitive file system) and other processes mirroring to the same folder
     * @param filePath local path of the file
     * @param contentLength expected length of the file
     * @return true if the file has been downloaded, false if it is being downloaded by another task or process
     *         or has been downloaded meanwhile
     * @throws IOException in the case of error; the {@code .part} file is kept to be resumed later
     */
    private boolean download(@NonNull Path filePath, long contentLength) throws IOException {
        Path partPath = partPath(filePath);
        boolean upToDate;
        try (FileChannel channel = FileChannel.open(partPath, CREATE, WRITE); FileLock lock = tryLock(channel)) {
            if (lock == null) {
                logger.warn("File: '{}' is being downloaded by another task or process", Printable.of(filePath));
                return false;
            }
            upToDate = isUpToDate(filePath, contentLength);
            if (!upToDate) {
                removeStaleParts(filePath, partPath);
                transfer(channel, filePath, contentLength);
            }
        } catch (ChangedFileException cfex) {
            Files.deleteIfExists(partPath);
            throw cfex;
        }
        if (upToDate) {
            Files.deleteIfExists(partPath);
            return false;
        }
        syncFileTime(partPath, false);
        replace(partPath, filePath);
        return true;
    }

    /**
     * Transfer the file to the {@code .part} file from its current length
     * @param channel locked channel of the {@code .part} file
     * @param filePath local path of the file
     * @param contentLength expected length of the file
     * @throws IOException in the case of error
     */
    private void transfer(@NonNull FileChannel channel, @NonNull Path filePath, long contentLength) throws IOException {
        long readed = channel.size();
        if (readed > contentLength) {
            logger.warn("Downloaded part of the file: '{}' is longer than the file and is discarded", Printable.of(filePath));
            readed = 0L;
        } else if (readed > 0) {
            logger.info("Continue loading from offset {} of the file: '{}'", readed, Printable.of(filePath));
        }
        channel.truncate(readed);
        channel.position(readed);
        byte[] buff = new byte[BUFFER_SIZE];
        while (readed < contentLength) {
            long offset = readed;
            try (InputStream inputStream = this.getPage().inputStreamFrom(offset)) {
                long remoteLength = this.getPage().contentLength();
                if (remoteLength >= 0 && remoteLength != contentLength) {
                    throw new ChangedFileException("File has " + remoteLength + " bytes on the site instead of " + contentLength + " from the listing");
                }
                int bufferOffset = 0;
                while (readed < contentLength) {
                    int bytes = read(inputStream, buff, bufferOffset, readed, contentLength);
                    if (bytes < 0) {
                        write(channel, buff, bufferOffset);
                        break;
                    }
                    bufferOffset += bytes;
                    readed += bytes;
                    if (bufferOffset == buff.length || readed == contentLength) {
                        write(channel, buff, bufferOffset);
                        bufferOffset = 0;
                    }
                }
            }
            if (readed == offset) {
                throw new EOFException("No data has been received from offset " + offset);
            }
        }
    }

    /**
     * Lock the whole file of the channel without waiting
     * @param channel channel of the file
     * @return lock or null if the file is locked by this or another process
     * @throws IOException in the case of error
     */
    private static @Nullable FileLock tryLock(@NonNull FileChannel channel) throws IOException {
        try {
            return channel.tryLock();
        } catch (OverlappingFileLockException oflex) {
            return null;
        }
    }

    /**
     * Write the bytes of the buffer to the channel
     * @param channel channel to write to
     * @param buff buffer
     * @param length number of the bytes from the beginning of the buffer
     * @throws IOException in the case of error
     */
    private static void write(@NonNull FileChannel channel, @NonNull byte[] buff, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(buff, 0, length);
        for (int written = 0; written < length; ) {
            written += channel.write(buffer);
        }
    }

    /**
     * Path of the {@code .part} file bound to the listing time of the file
     * @param filePath local path of the file
     * @return path of the {@code .part} file
     */
    private @NonNull Path partPath(@NonNull Path filePath) {
        String stamp = Optional.ofNullable(this.getPage().getHref().getDateTime()).map(PART_STAMP::format).orElse("000000000000");
        return filePath.resolveSibling(filePath.getFileName() + "." + stamp + PART_SUFFIX);
    }

    /**
     * Remove the {@code .part} files of the other versions of the file
     * @param filePath local path of the file
     * @param partPath {@code .part} file of the current version
     * @throws IOException if the folder cannot be listed or a stale part cannot be removed
     */
    private static void removeStaleParts(@NonNull Path filePath, @NonNull Path partPath) throws IOException {
        String prefix = filePath.getFileName().toString();
        DirectoryStream.Filter<Path> stalePart = path -> {
            String name = path.getFileName().toString();
            return !path.equals(partPath) && name.startsWith(prefix)
                    && PART_STAMP_PATTERN.matcher(name.substring(prefix.length())).matches();
        };
        try (DirectoryStream<Path> staleParts = Files.newDirectoryStream(filePath.getParent(), stalePart)) {
            for (Path stale : staleParts) {
                Files.deleteIfExists(stale);
                logger.info("Part of another version of the file has been removed: '{}'", Printable.of(stale));
            }
        }
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
            return inputStream.read(buff, bufferOffset, (int) Math.min(buff.length - bufferOffset, contentLength - readed));
        } catch (IOException ioex) {
            logger.warn("Download has been interrupted at {} of {} bytes: '{}'. Cause: {}",
                    readed, contentLength, Printable.of(this.getPage().path()), Printable.of(ioex.getMessage()));
            return -1;
        }
    }

    /**
     * Time of the file from the listing in the configured time zone
     * @return time of the file or null if the listing has no time
     */
    private @Nullable Instant listingTime() {
        return Optional.ofNullable(this.getPage().getHref().getDateTime())
                .map(dateTime -> dateTime.atZone(this.getZone()).toInstant().truncatedTo(ChronoUnit.SECONDS))
                .orElse(null);
    }

    /**
     * Set the time of the file from the listing. A failure is reported and does not affect the file
     * @param filePath path of the file
     * @param checkForChange report the change of the time of an existing file
     */
    private void syncFileTime(@NonNull Path filePath, boolean checkForChange) {
        Instant listingTime = listingTime();
        if (listingTime == null) {
            return;
        }
        try {
            Instant fileTime = Files.getLastModifiedTime(filePath).toInstant().truncatedTo(ChronoUnit.SECONDS);
            if (!fileTime.equals(listingTime)) {
                FileTime listingFileTime = FileTime.from(listingTime);
                Files.setAttribute(filePath, "creationTime", listingFileTime);
                Files.setLastModifiedTime(filePath, listingFileTime);
                if (checkForChange) {
                    logger.info("File: '{}' already exists. Its time has been reset to: {}", Printable.of(filePath), listingTime);
                }
            }
        } catch (IOException ioex) {
            logger.warn("Unable to set the time of the file: '{}'. Cause: {} {}", Printable.of(filePath), ioex.getClass().getSimpleName(), Printable.of(ioex.getMessage()));
        }
    }

    /**
     * The file on the site differs from the file in the listing: the downloaded part belongs to another version
     */
    private static final class ChangedFileException extends IOException {

        private static final long serialVersionUID = 1L;

        ChangedFileException(@NonNull String message) {
            super(message);
        }

    }

}
