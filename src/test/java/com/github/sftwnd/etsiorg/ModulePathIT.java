package com.github.sftwnd.etsiorg;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The packaged jars: the downloader runs as a named module on the module path, and the fat jar is a jar for the class path
 */
class ModulePathIT {

    private static final String MODULE = "com.github.sftwnd.etsiorg";
    private static final String INVALID_URI = "ftp://www.etsi.org/deliver/";

    @Test
    void moduleDescriptorTest() {
        ModuleDescriptor descriptor = ModuleFinder.of(path("etsiorg.moduleJar")).find(MODULE).orElseThrow().descriptor();
        assertFalse(descriptor.isAutomatic());
        assertEquals(Optional.of(Main.class.getName()), descriptor.mainClass());
    }

    @Test
    void logbackIsBoundAsServiceProviderTest() {
        Configuration configuration = ModuleLayer.boot().configuration()
                .resolveAndBind(ModuleFinder.of(path("etsiorg.moduleJar"), path("etsiorg.modulePath")), ModuleFinder.of(), Set.of(MODULE));
        assertTrue(configuration.findModule("org.slf4j").isPresent());
        assertTrue(configuration.findModule("ch.qos.logback.classic").isPresent());
        assertTrue(configuration.findModule("ch.qos.logback.core").isPresent());
    }

    @Test
    void runOnModulePathTest() throws IOException, InterruptedException {
        assertInvalidConfiguration("--module-path", path("etsiorg.moduleJar") + File.pathSeparator + path("etsiorg.modulePath"),
                "--module", MODULE);
    }

    @Test
    void fatJarHasNoModuleDescriptorsTest() throws IOException {
        try (ZipFile fatJar = new ZipFile(path("etsiorg.fatJar").toFile())) {
            assertEquals(List.of(), fatJar.stream()
                    .map(ZipEntry::getName)
                    .filter(name -> name.endsWith("module-info.class"))
                    .collect(Collectors.toList()));
        }
        ModuleReference reference = ModuleFinder.of(path("etsiorg.fatJar")).findAll().iterator().next();
        assertTrue(reference.descriptor().isAutomatic());
    }

    @Test
    void runOnClassPathTest() throws IOException, InterruptedException {
        assertInvalidConfiguration("-jar", path("etsiorg.fatJar").toString());
    }

    /**
     * Start the downloader with an invalid start uri: it has to log the error with logback and exit with status 2
     * @param launch arguments that launch the downloader
     */
    private static void assertInvalidConfiguration(String... launch) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dlogback.configurationFile=" + path("etsiorg.logbackConfiguration"),
                "-Duri=" + INVALID_URI));
        command.addAll(List.of(launch));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), output);
        assertEquals(Main.INVALID_CONFIGURATION, process.exitValue(), output);
        assertTrue(output.contains("Invalid configuration: Only http and https are supported: " + INVALID_URI), output);
    }

    private static Path path(String property) {
        return Path.of(Objects.requireNonNull(System.getProperty(property), property));
    }

}
