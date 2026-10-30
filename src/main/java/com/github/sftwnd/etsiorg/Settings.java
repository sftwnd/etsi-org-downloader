package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.Getter;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Settings of a run. A setting is taken from its system property, otherwise from its environment variable, otherwise
 * it has the default value; a blank value is not set
 */
@Getter
public final class Settings {

    /**
     * Default site folder to mirror
     */
    static final String DEFAULT_URI = "https://www.etsi.org/deliver/";
        // "https://www.etsi.org/deliver/etsi_ts/129000_129099/";
        // "https://www.etsi.org/deliver/etsi_ts/129000_129099/129013/";
        // "https://www.etsi.org/deliver/etsi_ts/129000_129099/129002/";
        // "https://www.etsi.org/deliver/etsi_ts/129000_129099/129079";
        // "https://www.etsi.org/deliver/etsi_ts/";
        // "https://www.etsi.org/deliver/etsi_ts/129000_129099/129079/10.07.00_60";
        // "https://www.etsi.org/deliver/etsi_ts/129000_129099/129078/17.00.00_60";
        // "https://www.etsi.org/deliver/etsi_ts/136500_136599/13652103/17.01.00_60/";

    /**
     * Default number of the threads, i.e. of the simultaneous requests to the site: the load on the site does not
     * depend on the number of processors of the machine
     */
    static final int DEFAULT_THREADS = 4;

    private static final String VARIABLE_PREFIX = "ETSIORG_";

    /**
     * Setting: the names of its system property and environment variable, its meaning and default value
     */
    enum Key {

        URI("uri", "site folder to mirror; only links inside it are followed", DEFAULT_URI),
        DEST("dest", "destination folder; files are stored under their path on the site", "the current folder"),
        THREADS("threads", "threads that process listings and files, i.e. simultaneous requests", String.valueOf(DEFAULT_THREADS)),
        ZONE("zone", "time zone of the listing time; www.etsi.org lists it in UTC", "UTC"),
        DRY_RUN("dry-run", "true: walk the listings and report what a run would download and remove, changing nothing", "false");

        private final String property;
        private final String meaning;
        private final String defaultValue;

        Key(@NonNull String property, @NonNull String meaning, @NonNull String defaultValue) {
            this.property = property;
            this.meaning = meaning;
            this.defaultValue = defaultValue;
        }

        /**
         * @return name of the system property
         */
        @NonNull String property() {
            return this.property;
        }

        /**
         * @return name of the environment variable
         */
        @NonNull String variable() {
            return VARIABLE_PREFIX + name();
        }

    }

    /**
     * Site folder to mirror
     */
    private final @NonNull URI uri;

    /**
     * Destination folder
     */
    private final @NonNull Path dest;

    /**
     * Number of the threads that process the resources, which is also the limit of the simultaneous requests
     */
    private final int threads;

    /**
     * Time zone of the listing time
     */
    private final @NonNull ZoneId zone;

    /**
     * Dry run: the listings are walked, nothing is downloaded, removed or created
     */
    private final boolean dryRun;

    private Settings(@NonNull URI uri, @NonNull Path dest, int threads, @NonNull ZoneId zone, boolean dryRun) {
        this.uri = uri;
        this.dest = dest;
        this.threads = threads;
        this.zone = zone;
        this.dryRun = dryRun;
    }

    /**
     * Settings from the system properties and the environment variables
     * @param properties value of a system property by its name, e.g. {@code System::getProperty}
     * @param environment value of an environment variable by its name, e.g. {@code System::getenv}
     * @return settings of a run
     * @throws IllegalArgumentException if a value is invalid
     */
    public static @NonNull Settings of(@NonNull UnaryOperator<String> properties, @NonNull UnaryOperator<String> environment) {
        Objects.requireNonNull(properties, "Settings::of - properties is null");
        Objects.requireNonNull(environment, "Settings::of - environment is null");
        return new Settings(
                uri(value(Key.URI, properties, environment)),
                dest(value(Key.DEST, properties, environment)),
                threads(value(Key.THREADS, properties, environment)),
                zone(value(Key.ZONE, properties, environment)),
                dryRun(value(Key.DRY_RUN, properties, environment)));
    }

    /**
     * Help on the settings: a line per setting with its property, variable, meaning and default value
     * @return lines of the help
     */
    static @NonNull String help() {
        return Arrays.stream(Key.values())
                .map(key -> String.format("  -D%-8s %-16s %s (default: %s)", key.property(), key.variable(), key.meaning, key.defaultValue))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    /**
     * Value of the setting: its system property, otherwise its environment variable
     * @return trimmed value or null if neither is set or both are blank
     */
    private static @Nullable String value(@NonNull Key key, @NonNull UnaryOperator<String> properties, @NonNull UnaryOperator<String> environment) {
        return Stream.of(properties.apply(key.property()), environment.apply(key.variable()))
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .findFirst()
                .orElse(null);
    }

    /**
     * Site folder to mirror
     * @param value trimmed value of the setting or null
     * @return uri, {@link #DEFAULT_URI} by default
     * @throws IllegalArgumentException if the value is not a uri
     */
    private static @NonNull URI uri(@Nullable String value) {
        try {
            return new URI(value == null ? DEFAULT_URI : value);
        } catch (URISyntaxException usex) {
            throw new IllegalArgumentException("Invalid uri: " + Printable.of(usex.getMessage()), usex);
        }
    }

    /**
     * Destination folder
     * @param value trimmed value of the setting or null
     * @return path, the current folder by default
     * @throws IllegalArgumentException if the value is not a path
     */
    private static @NonNull Path dest(@Nullable String value) {
        try {
            return Path.of(value == null ? "." : value);
        } catch (InvalidPathException ipex) {
            throw new IllegalArgumentException("Invalid destination folder: " + Printable.of(ipex.getMessage()), ipex);
        }
    }

    /**
     * Number of the threads that process the resources, which is also the limit of the simultaneous requests
     * @param value trimmed value of the setting or null
     * @return positive number of the threads, {@link #DEFAULT_THREADS} by default
     * @throws IllegalArgumentException if the value is not a positive integer
     */
    private static int threads(@Nullable String value) {
        if (value == null) {
            return DEFAULT_THREADS;
        }
        try {
            int threads = Integer.parseInt(value);
            if (threads > 0) {
                return threads;
            }
        } catch (NumberFormatException ignore) {
            // reported below
        }
        throw new IllegalArgumentException("Number of threads has to be a positive integer: " + Printable.of(value));
    }

    /**
     * Time zone of the listing time: www.etsi.org lists the time in UTC
     * @param value trimmed value of the setting or null
     * @return time zone, UTC by default
     * @throws IllegalArgumentException if the value is not a time zone id
     */
    private static @NonNull ZoneId zone(@Nullable String value) {
        if (value == null) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(value);
        } catch (DateTimeException dtex) {
            throw new IllegalArgumentException("Unknown time zone: " + Printable.of(value), dtex);
        }
    }

    /**
     * Dry run
     * @param value trimmed value of the setting or null
     * @return true for {@code true} in any case, false by default
     * @throws IllegalArgumentException if the value is neither {@code true} nor {@code false}
     */
    private static boolean dryRun(@Nullable String value) {
        if (value == null || Boolean.FALSE.toString().equalsIgnoreCase(value)) {
            return false;
        }
        if (Boolean.TRUE.toString().equalsIgnoreCase(value)) {
            return true;
        }
        throw new IllegalArgumentException("Dry run has to be true or false: " + Printable.of(value));
    }

}
