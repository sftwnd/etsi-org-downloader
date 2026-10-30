package com.github.sftwnd.etsiorg;

import org.junit.jupiter.api.Test;

import java.lang.module.ModuleDescriptor;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tests run on the module path, patched into the module of the downloader
 */
class ModuleTest {

    @Test
    void namedModuleTest() {
        Module module = Main.class.getModule();
        assertTrue(module.isNamed());
        assertEquals("com.github.sftwnd.etsiorg", module.getName());
    }

    @Test
    void requiresTest() {
        ModuleDescriptor descriptor = Main.class.getModule().getDescriptor();
        assertEquals(Map.of(
                        "java.base", Set.of(ModuleDescriptor.Requires.Modifier.MANDATED),
                        "org.slf4j", Set.of(),
                        "lombok", Set.of(ModuleDescriptor.Requires.Modifier.STATIC),
                        "com.github.spotbugs.annotations", Set.of(ModuleDescriptor.Requires.Modifier.STATIC)),
                descriptor.requires().stream().collect(Collectors.toMap(ModuleDescriptor.Requires::name, ModuleDescriptor.Requires::modifiers)));
    }

    @Test
    void nothingIsExportedTest() {
        assertTrue(Main.class.getModule().getDescriptor().exports().isEmpty());
    }

}
