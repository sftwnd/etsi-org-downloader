package com.github.sftwnd.etsiorg;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DestinationTest {

    private static final Path ROOT = Path.of("target", "destination-test");

    @Test
    void rootIsAbsoluteAndNormalizedTest() {
        assertEquals(ROOT.toAbsolutePath().normalize(), new Destination(Path.of("target", "..", "target", "destination-test")).getRoot());
    }

    @Test
    void nullRootTest() {
        assertThrows(NullPointerException.class, () -> new Destination(null));
    }

    @Test
    void filePathTest() {
        assertEquals(this.root.resolve(Path.of("deliver", "etsi_ts", "129000_129099", "ts_129078v170000p.pdf")),
                this.destination.pathOf(URI.create("https://www.etsi.org/deliver/etsi_ts/129000_129099/ts_129078v170000p.pdf")));
    }

    @Test
    void folderPathTest() {
        assertEquals(this.root.resolve(Path.of("deliver", "etsi_ts", "129000_129099", "17.00.00_60")),
                this.destination.pathOf(URI.create("https://www.etsi.org/deliver/etsi_ts/129000_129099/17.00.00_60/")));
    }

    @Test
    void siteRootTest() {
        assertEquals(this.root, this.destination.pathOf(URI.create("https://www.etsi.org/")));
    }

    @Test
    void encodedCharactersTest() {
        assertEquals(this.root.resolve(Path.of("deliver", "ts 1 & 2.pdf")),
                this.destination.pathOf(URI.create("https://www.etsi.org/deliver/ts%201%20&%202.pdf")));
    }

    @Test
    void nonAsciiTest() {
        assertEquals(this.root.resolve(Path.of("deliver", "документ.pdf")),
                this.destination.pathOf(URI.create("https://www.etsi.org/deliver/%D0%B4%D0%BE%D0%BA%D1%83%D0%BC%D0%B5%D0%BD%D1%82.pdf")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.etsi.org/../../tmp/pwn/01.00.00_00/",
            "https://www.etsi.org/deliver/../../x",
            "https://www.etsi.org/deliver/%2E%2E/%2E%2E/%2E%2E/tmp/pwn.txt",
            "https://www.etsi.org/deliver/%2e%2e/x",
            "https://www.etsi.org/deliver/./x",
            "https://www.etsi.org/deliver/%2E/x",
            "https://www.etsi.org/deliver//x",
            "https://www.etsi.org/deliver/a%2Fb",
            "https://www.etsi.org/deliver/a%2f..%2f..%2fb",
            "https://www.etsi.org/deliver/a%5Cb",
            "https://www.etsi.org/deliver/a%00b",
            "https://www.etsi.org/deliver/a%0Ab",
            "https://www.etsi.org/deliver/a%1B%5B2Jb",
            "https://www.etsi.org/deliver/a%E2%80%AEb",
            "mailto:user@www.etsi.org",
            "deliver/relative.pdf"
    })
    void unsafePathTest(String uri) {
        assertThrows(IllegalArgumentException.class, () -> this.destination.pathOf(URI.create(uri)));
    }

    @Test
    void unsafePathMessageIsPrintableTest() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> this.destination.pathOf(URI.create("https://www.etsi.org/deliver/a%0A%1Bb")));
        assertTrue(Printable.isPrintable(exception.getMessage()), exception.getMessage());
    }

    @BeforeEach
    void startUp() {
        this.destination = new Destination(ROOT);
        this.root = ROOT.toAbsolutePath().normalize();
    }

    private Destination destination;
    private Path root;

}
