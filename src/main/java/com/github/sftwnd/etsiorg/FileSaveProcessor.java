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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Save file if one doesn't exist
 */
@Getter
@Slf4j
public class FileSaveProcessor implements Processor<CompletableFuture<Stream<Path>>> {

    private final String root;
    private final Page page;

    FileSaveProcessor(@NonNull Path root, @NonNull Page page) {
        this.root = Objects.requireNonNull(root, "FileSaveProcessor::new - path is null").toString();
        this.page = Objects.requireNonNull(page, "FileSaveProcessor::new - page is null");
    }

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
        if (checkFile() && checkFolder()) {
            try (var inputStream = this.page.getInputStream();
                 var outputStream = Files.newOutputStream(filePath, WRITE, CREATE, TRUNCATE_EXISTING)) {
                byte[] buff = new byte[8192];
                for (int readed = 0; readed < page.getContentLength(); ) {
                    int bytes = inputStream.read(buff, 0, buff.length);
                    readed += bytes;
                    outputStream.write(buff, 0, bytes);
                }
                outputStream.flush();
                logger.info("File: {} has been saved", filePath);
                return filePath;
            } catch (IOException ioex) {
                logger.error("Unable to write file: {} by cause: {}", filePath, ioex.getMessage());
                if (Files.isRegularFile(filePath)) {
                    try {
                        Files.delete(filePath);
                    } catch (IOException ignore) {
                    }
                }
            }
        }
        return null;
    }

    /**
     * Try to check file for existence
     * @return false if file already exists or unable to create file
     */
    private synchronized boolean checkFile() {
        Path filePath = Path.of(this.root, this.page.path().toString());
        if (Files.exists(filePath)) {
            if (Files.isRegularFile(filePath)) {
                logger.trace("File: {} already exists.", filePath);
            } else {
                logger.trace("Unable to create file: {}. Folder with such name already exists.", filePath);
            }
            return false;
        }
        return true;
    }

    /**
     * Try to find the folder and create it if it is not found
     * @return false if unable to create folder
     */
    private synchronized boolean checkFolder() {
        Path folderPath = Path.of(this.root, this.page.path().toString()).getParent();
        if (Files.exists(folderPath)) {
            if (Files.isRegularFile(folderPath)) {
                logger.error("Unable to create folder: {}. File with such name exists.", folderPath);
                return false;
            }
        } else {
            try {
                Files.createDirectories(folderPath);
            } catch (FileAlreadyExistsException ignore) {
            } catch (IOException ioex) {
                logger.error("Unable to create folder: {}. Cause: {}", folderPath, ioex.getLocalizedMessage());
                return false;
            }
        }
        return true;
    }

}