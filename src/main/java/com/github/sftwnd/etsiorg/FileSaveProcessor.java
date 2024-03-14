package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
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
 * Save file if one doesn't exist
 */
@Getter
@Slf4j
public class FileSaveProcessor implements Processor<CompletableFuture<Stream<Path>>> {

    private static final int BUFFER_SIZE = 64 << 10;

    private final String root;
    private final Page page;

    FileSaveProcessor(@NonNull Path root, @NonNull Page page) {
        this.root = Objects.requireNonNull(root, "FileSaveProcessor::new - path is null").toString();
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
     * File will be loaded and Path to file ill be returned
     * @return path to saved file
     */
    @SneakyThrows
    private @Nullable Path saveFile() {
        Path filePath = Path.of(this.root, this.page.path().toString());
        try {
            if (checkFolder()) {
                long readed = checkFile();
                if (readed != -1) {
                    try (var outputStream = Files.newOutputStream(filePath, WRITE, CREATE, readed == 0 ? TRUNCATE_EXISTING : APPEND)) {
                        byte[] buff = new byte[BUFFER_SIZE];
                        long contentLength = page.contentLength();
                        while (readed < contentLength) {
                            try (var inputStream = page.inputStream()) {
                                int bufferOffset = 0;
                                while (readed < contentLength) {
                                    int bytes = inputStream.read(buff, bufferOffset, buff.length - bufferOffset);
                                    if (bytes < 0) {
                                        if (bufferOffset > 0) {
                                            outputStream.write(buff, 0, bufferOffset);
                                        }
                                        page.connect(readed);
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
                        }
                        outputStream.flush();
                    }
                    syncFileTime(filePath, false);
                    logger.info("File: '{}' has been saved", filePath);
                }
                return filePath;
            }
        } catch (IOException ioex) {
            logger.error("Unable to write file: '{}' by cause: {}", filePath, ioex.getMessage());
            if (Files.isRegularFile(filePath)) {
                try {
                    Files.delete(filePath);
                } catch (IOException ignore) {
                }
            }
        }
        return null;
    }

    /**
     * Try to check file for existence
     * @return 0 if the file needs to be loaded from the very beginning, a positive offset if it is necessary to continue loading and -1 if loading is not required or impossible
     */
    private synchronized long checkFile() throws IOException {
        Path filePath = Path.of(this.root, this.page.path().toString());
        long contentLength = this.page.contentLength();
        if (Files.exists(filePath)) {
            if (Files.isRegularFile(filePath)) {
                long fileSize = Files.size(filePath);
                if (fileSize < contentLength) {
                    logger.warn("Continue loading from offset {} of the file: '{}'", fileSize, filePath);
                    return fileSize;
                } else if (fileSize > contentLength) {
                    logger.warn("Actual size: {} is larger than expected: {} for the file: '{}'", fileSize, this.page.getHref().getBytes(), filePath);
                } else {
                    logger.debug("File: '{}' already exists.", filePath);
                }
                syncFileTime(filePath, true);
            } else {
                logger.trace("Unable to create file: '{}'. Folder with such name already exists.", filePath);
            }
            return -1L;
        }
        return 0;
    }

    private void syncFileTime(@NonNull Path filePath, boolean checkForChange) throws IOException {
        LocalDateTime dateTime = this.page.getHref().getDateTime();
        if (dateTime != null) {
            BasicFileAttributes attr = Files.readAttributes(filePath, BasicFileAttributes.class);
            Instant creationInstant = attr.creationTime().toInstant().truncatedTo(ChronoUnit.SECONDS);
            Instant creationDateTime = this.page.dateTime();
            if (! creationInstant.equals(creationDateTime)) {
                FileTime creationFileTime = FileTime.from(creationDateTime);
                Files.setAttribute(filePath, "creationTime", creationFileTime);
                Files.setLastModifiedTime(filePath, creationFileTime);
                if (checkForChange) {
                    logger.warn("File: '{}' already exists. Creation time has been reset to: {}", filePath, dateTime);
                }
            }
        }
    }

    /**
     * Try to find the folder and create it if it is not found
     * @return false if unable to create folder
     */
    private synchronized boolean checkFolder() {
        Path folderPath = Path.of(this.root, this.page.path().toString()).getParent();
        if (folderPath != null) {
            if (Files.exists(folderPath)) {
                if (Files.isRegularFile(folderPath)) {
                    logger.error("Unable to create folder: '{}'. File with such name exists.", folderPath);
                    return false;
                }
            } else {
                try {
                    Files.createDirectories(folderPath);
                } catch (FileAlreadyExistsException ignore) {
                } catch (IOException ioex) {
                    logger.error("Unable to create folder: '{}'. Cause: {}", folderPath, ioex.getMessage());
                    return false;
                }
            }
        }
        return true;
    }

}