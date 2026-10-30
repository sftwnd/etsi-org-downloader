package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Set;
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

    public static void main(String[] args) {
        System.exit(run(args, System::getProperty, System::getenv));
    }

    /**
     * Mirror the site folder with the settings from the system properties and the environment variables,
     * or print the help
     * @param args command line arguments: none, or {@code --help}
     * @param properties value of a system property by its name
     * @param environment value of an environment variable by its name
     * @return exit status: {@link #SUCCESS}, {@link #FAILURE} or {@link #INVALID_CONFIGURATION}
     */
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
        try {
            settings = Settings.of(properties, environment);
            linkPolicy = new LinkPolicy(settings.getUri());
        } catch (IllegalArgumentException exception) {
            logger.error("Invalid configuration: {}", Printable.of(exception.getMessage()));
            return INVALID_CONFIGURATION;
        }
        return mirror(settings, linkPolicy);
    }

    /**
     * Mirror the site folder
     * @param settings settings of the run
     * @param linkPolicy policy of the links inside the site folder
     * @return exit status: {@link #SUCCESS} or {@link #FAILURE}
     */
    private static int mirror(@NonNull Settings settings, @NonNull LinkPolicy linkPolicy) {
        ExecutorService executor = Executors.newFixedThreadPool(settings.getThreads());
        try {
            Destination destination = new Destination(settings.getDest());
            logger.info("Mirroring '{}' to '{}' with {} threads, listing time zone: {}",
                    Printable.of(linkPolicy.getRoot()), Printable.of(destination.getRoot()), settings.getThreads(), settings.getZone());
            var processorFactory = new ComplexProcessorFactory(destination, settings.getZone(), linkPolicy, executor, new ExpiredRemover(destination));
            linkPolicy.visit(linkPolicy.getRoot());
            Summary summary = processorFactory
                    .processor(Page.of(HREF.builder().uri(linkPolicy.getRoot()).build()))
                    .process()
                    .join();
            logger.info("Files downloaded: {}, up to date: {}, failed: {}", summary.getDownloaded(), summary.getUpToDate(), summary.getFailed());
            return summary.getFailed() == 0 ? SUCCESS : FAILURE;
        } catch (RuntimeException rex) {
            logger.error("Mirroring has failed. Cause: {} {}", rex.getClass().getSimpleName(), Printable.of(rex.getMessage()));
            return FAILURE;
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Help: the usage, the settings and the exit statuses
     * @return text of the help
     */
    static @NonNull String help() {
        String lineSeparator = System.lineSeparator();
        return "Usage: etsiorg-downloader [-h | --help]" + lineSeparator
                + "Mirrors a folder of " + Settings.DEFAULT_URI + " to a local folder." + lineSeparator
                + lineSeparator
                + "Settings: a system property -D<name>=<value>, otherwise an environment variable, otherwise the default:" + lineSeparator
                + Settings.help() + lineSeparator
                + lineSeparator
                + "Exit status: " + SUCCESS + " - no failures, " + FAILURE + " - some resources have failed, "
                + INVALID_CONFIGURATION + " - invalid configuration" + lineSeparator;
    }

}
