package com.github.sftwnd.etsiorg;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.status.Status;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Production logging configuration is loaded with {@code -Dlogback.configurationFile} and is not packed into the jar
 */
class LogbackConfigurationTest {

    private static final Path CONFIGURATION = Path.of("src/main/resources/logback.xml");
    private static final String NO_COLOR = "NO_COLOR";
    private static final String ESCAPE = "\u001b";
    private static final String MESSAGE = "Message";

    @ParameterizedTest
    @ValueSource(strings = {"", "1"})
    void configurationWithoutWarningsTest(String noColor) throws JoranException {
        configure(noColor);
        assertEquals("", this.context.getStatusManager().getCopyOfStatusList().stream()
                .filter(status -> status.getLevel() > Status.INFO)
                .map(Status::getMessage)
                .collect(Collectors.joining("; ")));
    }

    @Test
    void shutdownHookTest() throws JoranException {
        configure("");
        assertInstanceOf(Thread.class, this.context.getObject(CoreConstants.SHUTDOWN_HOOK_THREAD));
    }

    @Test
    @DisabledIfEnvironmentVariable(named = NO_COLOR, matches = ".*")
    @DisabledIfSystemProperty(named = NO_COLOR, matches = ".*")
    void colorsWithoutNoColorTest() throws JoranException {
        configure(null);
        assertTrue(line().contains(ESCAPE));
    }

    @Test
    void colorsForEmptyNoColorTest() throws JoranException {
        configure("");
        assertTrue(line().contains(ESCAPE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "true", "false"})
    void noColorTest(String noColor) throws JoranException {
        configure(noColor);
        String line = line();
        assertFalse(line.contains(ESCAPE));
        assertTrue(line.endsWith(" INFO  " + MESSAGE + System.lineSeparator()), line);
    }

    private void configure(@Nullable String noColor) throws JoranException {
        if (noColor != null) {
            this.context.putProperty(NO_COLOR, noColor);
        }
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(this.context);
        configurator.doConfigure(CONFIGURATION.toFile());
    }

    /**
     * The line the console appender writes for an INFO message
     */
    private String line() {
        Logger root = this.context.getLogger(Logger.ROOT_LOGGER_NAME);
        AsyncAppender async = assertInstanceOf(AsyncAppender.class, root.getAppender("ASYNC"));
        ConsoleAppender<ILoggingEvent> console = (ConsoleAppender<ILoggingEvent>) async.getAppender("STDOUT");
        ILoggingEvent event = new LoggingEvent(Logger.FQCN, root, Level.INFO, MESSAGE, null, null);
        return new String(console.getEncoder().encode(event), StandardCharsets.UTF_8);
    }

    @BeforeEach
    void startUp() {
        this.context = new LoggerContext();
    }

    @AfterEach
    void tearDown() {
        try {
            Object shutdownHook = this.context.getObject(CoreConstants.SHUTDOWN_HOOK_THREAD);
            if (shutdownHook instanceof Thread) {
                Runtime.getRuntime().removeShutdownHook((Thread) shutdownHook);
            }
        } finally {
            this.context.stop();
            this.context = null;
        }
    }

    private LoggerContext context;

}
