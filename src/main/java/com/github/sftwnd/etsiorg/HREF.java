package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Global resource reference
 */
@Slf4j
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class HREF {

    private static final long NO_VERSION = 0L;

    /**
     * URI to the site resource
     */
    private final URI uri;

    /**
     * Version for versioned file or 0
     */
    private final long version;

    /**
     * File size description
     */
    @Setter
    private Long bytes;

    /**
     * File creation time description
     */
    @Setter
    private LocalDateTime dateTime;

    /**
     * Has to be the file regular or not
     */
    @Setter
    private boolean regularFile;

    /**
     * File path on the site request
     * @return File path on the site
     */
    public @NonNull Path path() {
        return Path.of(getUri().getPath());
    }

    /**
     * File name request
     * @return file name
     */
    public @NonNull Path name() {
        return Optional.ofNullable(path().getFileName()).orElseGet(() -> Path.of(""));
    }

    /**
     * Check that resource is versioned
     * @return true for the versioned resources
     */
    public boolean isVersioned() {
        return this.getVersion() != NO_VERSION;
    }

    /**
     * Global resource reference constructor
     * @param uri uri to resource
     * @param version version of resource (if versioned)
     * @param bytes size of file (if regular file)
     * @param dateTime time of creation
     * @param regularFile true for regular file
     */
    private HREF(@NonNull URI uri, @Nullable Long version, @Nullable Long bytes, @Nullable LocalDateTime dateTime, @Nullable Boolean regularFile) {
        if (bytes != null && bytes < 0) {
            throw new IllegalArgumentException("HREF::new - Unable to set negative file size: " + bytes);
        }
        this.uri = Objects.requireNonNull(uri, "HREF::new - uri is null");
        this.version = Optional.ofNullable(version).orElseGet(() -> versionOfUri(uri));
        this.regularFile = Optional.ofNullable(regularFile).orElseGet(() -> bytes != null || dateTime != null);
        this.bytes = this.regularFile == Boolean.TRUE
                ? Objects.requireNonNull(bytes, "HREF::new - bytes has to be defined for regular file")
                : bytes;
        this.dateTime = this.regularFile == Boolean.TRUE
                ? Objects.requireNonNull(dateTime, "HREF::new - dateTime has to be defined for regular file")
                : dateTime;
        if (!this.regularFile) {
            if (this.bytes != null) {
                throw new IllegalArgumentException("HREF::new - Unable to set size for the folder");
            }
        }
    }

    /**
     * String representation of the global resource reference
     * @return String representation
     */
    @Override
    public String toString() {
        return "HREF: [ uri '" + getUri().toString() +'\'' +
                (isVersioned() ? ", version: " + versionOfUri(getUri()) : "") +
                (getBytes() == null ? "" : ", bytes: " + getBytes()) +
                (getDateTime() == null ? "" : ", dateTime: " + getDateTime()) +
                ", isRegular: " + isRegularFile() +
                "]";
    }

    /**
     * Pattern of file/resource description on site text/html page
     */
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)_(\\d+)");

    /**
     * Version from uri of versioned resource
     * @param uri uri of versioned resource
     * @return version of versioned resource
     */
    private static long versionOfUri(@NonNull URI uri) {
        return Optional.ofNullable(Path.of(uri.getPath()).getFileName())
                .map(Path::toString)
                .map(HREF::versionOfFile)
                .orElse(NO_VERSION);
    }

    /**
     * Version from fileName of versioned resource
     * @param fileName versioned resource file name
     * @return version of versioned resource
     */
    public static long versionOfFile(@NonNull String fileName) {
        Matcher matcher = VERSION_PATTERN.matcher(fileName);
        if (matcher.matches()) {
            return IntStream.range(0, 4)
                    .mapToLong(i -> Long.parseLong(matcher.group(i+1)) << (12 * (3 - i)) )
                    .sum();
        }
        return NO_VERSION;
    }

    /**
     * Name of versioned resource from version
     * @param version version of resource
     * @return name of versioned resource or null
     */
    public static @Nullable String versionName(long version) {
        if (version > NO_VERSION) {
            return versionNum(version, 1) + '.' +
                   versionNum(version, 2) + '.' +
                   versionNum(version, 3) + '_' +
                   versionNum(version, 4);

        }
        return null;
    }

    /**
     * Version mask of n-th element of the version
     * @param version number of n-th element of the version
     * @param position version element position
     * @return version mask
     */
    private static String  versionNum(long version, int position) {
        long num = (version >> (12 * (4 - position))) & 0xFFFL;
        return (num < 10 ? "0" : "") + num;
    }

    /**
     * HREF builder factory
     * @return HREF builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private URI uri;
        private Long version;
        private Long bytes;
        private LocalDateTime dateTime;
        private Boolean regularFile;
        private Builder() {
        }

        public HREF build() {
            return new HREF(uri, version, bytes, dateTime, regularFile);
        }

        public Builder uri(@NonNull URI uri) {
            this.uri = uri;
            return this;
        }

        public Builder version(Long version) {
            this.version = version;
            return this;
        }

        public Builder bytes(Long bytes) {
            this.bytes = bytes;
            return this;
        }

        public Builder dateTime(@NonNull LocalDateTime dateTime) {
            this.dateTime = dateTime;
            return this;
        }

        public Builder regularFile(boolean isRegularFile) {
            this.regularFile = isRegularFile;
            return this;
        }
    }

}
