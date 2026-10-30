package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.UnaryOperator;

@Slf4j
public class Main {

    /**
     * Arguments that request the help
     */
    private static final Set<String> HELP = Set.of("-h", "--help");

    /**
     * Exit status of a run without failures
     */
    static final int SUCCESS = 0;

    /**
     * Exit status of a run in which some resources have failed
     */
    static final int FAILURE = 1;

    /**
     * Exit status of a run with an invalid configuration
     */
    static final int INVALID_CONFIGURATION = 2;

    /**
     * Exit status of the JVM on an {@code OutOfMemoryError} with {@code -XX:+ExitOnOutOfMemoryError}, which {@code run.sh}
     * and the image set: no run returns it itself
     */
    static final int OUT_OF_MEMORY = 3;

    /**
     * Exit status of a run that has not started because another run mirrors to the destination folder
     */
    static final int LOCKED = 4;

    public static void main(String[] args) {
        System.exit(run(args, System::getProperty, System::getenv));
    }

    /**
     * Mirror the site folder with the settings from the system properties and the environment variables,
     * or print the help
     * @param args command line arguments: none, or {@code --help}
     * @param properties value of a system property by its name
     * @param environment value of an environment variable by its name
     * @return exit status: {@link #SUCCESS}, {@link #FAILURE}, {@link #INVALID_CONFIGURATION} or {@link #LOCKED}
     */
    // the lock is held while the run lasts and is not referenced
    @SuppressWarnings("try")
    static int run(@NonNull String[] args, @NonNull UnaryOperator<String> properties, @NonNull UnaryOperator<String> environment) {
        Objects.requireNonNull(args, "Main::run - args is null");
        if (args.length == 1 && HELP.contains(args[0])) {
            System.out.print(help());
            return SUCCESS;
        }
        if (args.length > 0) {
            logger.error("Invalid configuration: unknown arguments '{}', --help prints the usage", Printable.of(String.join(" ", args)));
            return INVALID_CONFIGURATION;
        }
        Settings settings;
        LinkPolicy linkPolicy;
        Destination destination;
        try {
            settings = Settings.of(properties, environment);
            linkPolicy = new LinkPolicy(settings.getUri());
            destination = new Destination(settings.getDest());
        } catch (IllegalArgumentException exception) {
            logger.error("Invalid configuration: {}", Printable.of(exception.getMessage()));
            return INVALID_CONFIGURATION;
        }
        if (settings.isDryRun()) {
            // nothing is written, so neither the write access nor the exclusion of other runs is needed
            return mirror(settings, linkPolicy, destination);
        }
        Optional<RunLock> lock;
        try {
            destination.prepare();
            lock = RunLock.tryAcquire(destination.getLockFile());
        } catch (IOException ioex) {
            logger.error("Invalid configuration: destination folder '{}' cannot be written. Cause: {} {}",
                    Printable.of(destination.getRoot()), ioex.getClass().getSimpleName(), Printable.of(ioex.getMessage()));
            return INVALID_CONFIGURATION;
        }
        if (lock.isEmpty()) {
            logger.error("Another run is mirroring to '{}': '{}' is locked",
                    Printable.of(destination.getRoot()), Printable.of(destination.getLockFile()));
            return LOCKED;
        }
        try (RunLock ignored = lock.get()) {
            return mirror(settings, linkPolicy, destination);
        }
    }

    /**
     * Mirror the site folder, or only plan it in a dry run
     * @param settings settings of the run
     * @param linkPolicy policy of the links inside the site folder
     * @param destination destination folder, prepared unless it is a dry run
     * @return exit status: {@link #SUCCESS} or {@link #FAILURE}
     */
    private static int mirror(@NonNull Settings settings, @NonNull LinkPolicy linkPolicy, @NonNull Destination destination) {
        boolean dryRun = settings.isDryRun();
        ExecutorService executor = Executors.newFixedThreadPool(settings.getThreads());
        try {
            logger.info("etsiorg-downloader {}, Java {}", version(), Runtime.version());
            logger.info("{} '{}' to '{}' with {} threads, listing time zone: {}", dryRun ? "Dry run: planning the mirror of" : "Mirroring",
                    Printable.of(linkPolicy.getRoot()), Printable.of(destination.getRoot()), settings.getThreads(), settings.getZone());
            ExpiredRemover remover = new ExpiredRemover(destination);
            ProcessorFactory<CompletableFuture<Summary>> files = dryRun
                    ? page -> () -> CompletableFuture.completedFuture(new FileSaveProcessor(destination, settings.getZone(), page).plan())
                    : page -> new FileSaveProcessor(destination, settings.getZone(), page);
            var processorFactory = new ComplexProcessorFactory(files, linkPolicy, executor, dryRun ? remover::plan : remover);
            linkPolicy.visit(linkPolicy.getRoot());
            Summary summary = processorFactory
                    .processor(Page.of(HREF.builder().uri(linkPolicy.getRoot()).build()))
                    .process()
                    .join();
            if (dryRun) {
                logger.info("Files to download: {} ({}), up to date: {}, expired versions to remove: {}, failed: {}",
                        summary.getDownloaded(), size(summary.getBytes()), summary.getUpToDate(), summary.getRemoved(), summary.getFailed());
                reportFreeSpace(destination, summary.getBytes());
            } else {
                logger.info("Files downloaded: {} ({}), up to date: {}, expired versions removed: {}, failed: {}",
                        summary.getDownloaded(), size(summary.getBytes()), summary.getUpToDate(), summary.getRemoved(), summary.getFailed());
            }
            return summary.getFailed() == 0 ? SUCCESS : FAILURE;
        } catch (RuntimeException rex) {
            logger.error("Mirroring has failed. Cause: {} {}", rex.getClass().getSimpleName(), Printable.of(rex.getMessage()));
            return FAILURE;
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Report the usable space of the file system of the destination folder, or of its nearest existing parent folder,
     * and whether the files to download fit into it
     * @param destination destination folder
     * @param bytes size of the files to download
     */
    private static void reportFreeSpace(@NonNull Destination destination, long bytes) {
        Path folder = destination.getRoot();
        while (folder.getParent() != null && Files.notExists(folder)) {
            folder = folder.getParent();
        }
        try {
            long usable = Files.getFileStore(folder).getUsableSpace();
            logger.info("Free space for '{}': {}", Printable.of(destination.getRoot()), size(usable));
            if (usable < bytes) {
                logger.warn("Free space is {} less than the size of the files to download", size(bytes - usable));
            }
        } catch (IOException ioex) {
            logger.warn("Unable to get the free space of '{}'. Cause: {} {}", Printable.of(folder), ioex.getClass().getSimpleName(), Printable.of(ioex.getMessage()));
        }
    }

    /**
     * Size in bytes or in binary units
     * @param bytes number of the bytes
     * @return size with one decimal place, e.g. {@code 1.5 GiB}, or the number of the bytes below a kibibyte
     */
    static @NonNull String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int unit = (Long.SIZE - 1 - Long.numberOfLeadingZeros(bytes)) / 10;
        return String.format(Locale.ROOT, "%.1f %ciB", bytes / (double) (1L << unit * 10), "KMGTPE".charAt(unit - 1));
    }

    /**
     * Version of the downloader: of its module on the module path, from the manifest of its jar on the class path
     * @return version or {@code unknown} if neither has it
     */
    static @NonNull String version() {
        return Optional.ofNullable(Main.class.getModule().getDescriptor())
                .flatMap(ModuleDescriptor::rawVersion)
                .or(() -> Optional.ofNullable(Main.class.getPackage().getImplementationVersion()))
                .orElse("unknown");
    }

    /**
     * Help: the usage, the settings and the exit statuses
     * @return text of the help
     */
    static @NonNull String help() {
        String lineSeparator = System.lineSeparator();
        return "Usage: etsiorg-downloader [-h | --help]" + lineSeparator
                + "etsiorg-downloader " + version() + " mirrors a folder of " + Settings.DEFAULT_URI + " to a local folder." + lineSeparator
                + lineSeparator
                + "Settings: a system property -D<name>=<value>, otherwise an environment variable, otherwise the default:" + lineSeparator
                + Settings.help() + lineSeparator
                + lineSeparator
                + "Exit status: " + SUCCESS + " - no failures, " + FAILURE + " - some resources have failed, "
                + INVALID_CONFIGURATION + " - invalid configuration, " + OUT_OF_MEMORY + " - out of memory, "
                + LOCKED + " - another run mirrors to the destination folder" + lineSeparator;
    }

}
