package com.github.sftwnd.etsiorg;

import edu.umd.cs.findbugs.annotations.NonNull;
import lombok.Getter;

import java.time.Duration;
import java.util.Objects;

/**
 * Settings of the HTTP requests
 */
@Getter
public final class HttpSettings {

    /**
     * Default settings: 30 seconds to connect, 60 seconds to wait for data, 3 attempts with the delay of 1 second
     * doubled before every next attempt
     */
    public static final HttpSettings DEFAULT = new HttpSettings(Duration.ofSeconds(30), Duration.ofSeconds(60), 3, Duration.ofSeconds(1));

    /**
     * Timeout to establish a connection
     */
    private final Duration connectTimeout;

    /**
     * Timeout to wait for the data of an established connection
     */
    private final Duration readTimeout;

    /**
     * Number of attempts of a request, at least one
     */
    private final int attempts;

    /**
     * Delay before the second attempt, doubled before every next one
     */
    private final Duration retryDelay;

    /**
     * Constructor of the HTTP settings
     * @param connectTimeout positive timeout to establish a connection
     * @param readTimeout positive timeout to wait for the data
     * @param attempts number of attempts of a request, at least one
     * @param retryDelay non-negative delay before the second attempt
     */
    public HttpSettings(@NonNull Duration connectTimeout, @NonNull Duration readTimeout, int attempts, @NonNull Duration retryDelay) {
        this.connectTimeout = positive(Objects.requireNonNull(connectTimeout, "HttpSettings::new - connectTimeout is null"), "connectTimeout");
        this.readTimeout = positive(Objects.requireNonNull(readTimeout, "HttpSettings::new - readTimeout is null"), "readTimeout");
        if (attempts < 1) {
            throw new IllegalArgumentException("HttpSettings::new - attempts has to be positive: " + attempts);
        }
        this.attempts = attempts;
        this.retryDelay = Objects.requireNonNull(retryDelay, "HttpSettings::new - retryDelay is null");
        if (retryDelay.isNegative()) {
            throw new IllegalArgumentException("HttpSettings::new - retryDelay is negative: " + retryDelay);
        }
    }

    private static @NonNull Duration positive(@NonNull Duration duration, @NonNull String name) {
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("HttpSettings::new - " + name + " has to be positive: " + duration);
        }
        return duration;
    }

}
