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

@Slf4j
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class HREF {

    private static final long NO_VERSION = 0L;

    private final URI uri;
    private final long version;
    @Setter
    private Long bytes;
    @Setter
    private LocalDateTime dateTime;
    @Setter
    private boolean regularFile;

    public @NonNull Path path() {
        return Path.of(uri.getPath());
    }

    public @NonNull Path name() {
        return Optional.ofNullable(path().getFileName()).orElseGet(() -> Path.of(""));
    }

    public boolean isVersioned() {
        return version != NO_VERSION;
    }

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

    @Override
    public String toString() {
        return "HREF: [ uri '" + uri.toString() +'\'' +
                (isVersioned() ? ", version: " + versionOfUri(uri) : "") +
                (bytes == null ? "" : ", bytes: " + bytes) +
                (dateTime == null ? "" : ", dateTime: " + dateTime) +
                ", isRegular: " + regularFile +
                "]";
    }

    //

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)_(\\d+)");

    private static long versionOfUri(@NonNull URI uri) {
        return Optional.ofNullable(Path.of(uri.getPath()).getFileName())
                .map(Path::toString)
                .map(HREF::versionOfFile)
                .orElse(NO_VERSION);
    }

    private static long versionOfFile(@NonNull String fileName) {
        Matcher matcher = VERSION_PATTERN.matcher(fileName);
        if (matcher.matches()) {
            return IntStream.range(0, 4)
                    .mapToLong(i -> Long.parseLong(matcher.group(i+1)) << (12 * (3 - i)) )
                    .sum();
        }
        return NO_VERSION;
    }

    public static @Nullable String versionName(long version) {
        if (version > NO_VERSION) {
            return versionNum(version, 1) + '.' +
                   versionNum(version, 2) + '.' +
                   versionNum(version, 3) + '_' +
                   versionNum(version, 4);

        }
        return null;
    }

    private static String  versionNum(long version, int position) {
        long num = (version >> (12 * (4 - position))) & 0xFFFL;
        return (num < 10 ? "0" : "") + num;
    }

    // Builder

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
