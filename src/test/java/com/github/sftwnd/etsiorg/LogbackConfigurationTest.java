package com.github.sftwnd.etsiorg;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.status.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Production logging configuration is loaded with {@code -Dlogback.configurationFile} and is not packed into the jar
 */
class LogbackConfigurationTest {

    private static final Path CONFIGURATION = Path.of("src/main/resources/logback.xml");

    @Test
    void configurationWithoutWarningsTest() {
        assertEquals("", this.context.getStatusManager().getCopyOfStatusList().stream()
                .filter(status -> status.getLevel() > Status.INFO)
                .map(Status::getMessage)
                .collect(Collectors.joining("; ")));
    }

    @Test
    void shutdownHookTest() {
        this.shutdownHook = assertInstanceOf(Thread.class, this.context.getObject(CoreConstants.SHUTDOWN_HOOK_THREAD));
    }

    @BeforeEach
    void startUp() throws JoranException {
        this.context = new LoggerContext();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(this.context);
        configurator.doConfigure(CONFIGURATION.toFile());
    }

    @AfterEach
    void tearDown() {
        try {
            if (this.shutdownHook != null) {
                Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
            }
        } finally {
            this.context.stop();
            this.shutdownHook = null;
            this.context = null;
        }
    }

    private LoggerContext context;
    private Thread shutdownHook;

}
