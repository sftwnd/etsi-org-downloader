package com.github.sftwnd.etsiorg;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkPolicyTest {

    private static final URI ROOT = URI.create("https://www.etsi.org/deliver/");

    @Test
    void rootIsNormalizedTest() {
        assertEquals(ROOT, new LinkPolicy(URI.create("HTTPS://WWW.ETSI.ORG/deliver")).getRoot());
    }

    @Test
    void siteRootTest() {
        assertEquals(URI.create("https://www.etsi.org/"), new LinkPolicy(URI.create("https://www.etsi.org")).getRoot());
    }

    @Test
    void rootPortIsKeptTest() {
        assertEquals(URI.create("http://localhost:8080/deliver/"), new LinkPolicy(URI.create("http://localhost:8080/deliver/")).getRoot());
    }

    @Test
    void nullRootTest() {
        assertThrows(NullPointerException.class, () -> new LinkPolicy(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://www.etsi.org/deliver/",
            "file:///tmp/deliver/",
            "jar:file:/tmp/x.jar!/deliver/",
            "mailto:user@www.etsi.org",
            "deliver/",
            "https:///deliver/",
            "https://user@www.etsi.org/deliver/",
            "https://www.etsi.org/deliver/?page=1",
            "https://www.etsi.org/deliver/#top",
            "https://www.etsi.org/deliver/../",
            "https://www.etsi.org/deliver/%2E%2E/"
    })
    void invalidRootTest(String uri) {
        assertThrows(IllegalArgumentException.class, () -> new LinkPolicy(URI.create(uri)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.etsi.org/deliver/",
            "https://www.etsi.org/deliver",
            "https://www.etsi.org/deliver/etsi_ts/",
            "https://www.etsi.org/deliver/etsi_ts/129000_129099/129078/17.00.00_60/ts_129078v170000p.pdf",
            "https://www.etsi.org:443/deliver/etsi_ts/",
            "HTTPS://WWW.ETSI.ORG/deliver/etsi_ts/"
    })
    void acceptedTest(String uri) {
        assertTrue(this.linkPolicy.accepts(URI.create(uri)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.example/deliver/etsi_ts/",
            "http://www.etsi.org/deliver/etsi_ts/",
            "https://www.etsi.org:8443/deliver/etsi_ts/",
            "ftp://www.etsi.org/deliver/etsi_ts/",
            "file:///deliver/etsi_ts/",
            "mailto:user@www.etsi.org",
            "https://www.etsi.org/run.sh",
            "https://www.etsi.org/",
            "https://www.etsi.org/deliverx/",
            "https://www.etsi.org/deliver/../run.sh",
            "https://www.etsi.org/deliver/%2E%2E/run.sh",
            "https://www.etsi.org/deliver/a%2F..%2F..%2Frun.sh",
            "https://www.etsi.org/deliver/a%0Ab",
            "https://user@www.etsi.org/deliver/etsi_ts/",
            "https://www.etsi.org/deliver/etsi_ts/?x=1",
            "https://www.etsi.org/deliver/etsi_ts/#x"
    })
    void rejectedTest(String uri) {
        assertFalse(this.linkPolicy.accepts(URI.create(uri)));
    }

    @Test
    void maxDepthTest() {
        assertTrue(this.linkPolicy.accepts(ROOT.resolve("a/".repeat(LinkPolicy.MAX_DEPTH))));
        assertFalse(this.linkPolicy.accepts(ROOT.resolve("a/".repeat(LinkPolicy.MAX_DEPTH + 1))));
    }

    @Test
    void visitOnceTest() {
        URI uri = ROOT.resolve("etsi_ts/129000_129099/");
        assertTrue(this.linkPolicy.visit(uri));
        assertFalse(this.linkPolicy.visit(uri));
        assertFalse(this.linkPolicy.visit(ROOT.resolve("etsi_ts/129000_129099")));
        assertTrue(this.linkPolicy.visit(ROOT.resolve("etsi_ts/129000_129199/")));
    }

    @BeforeEach
    void startUp() {
        this.linkPolicy = new LinkPolicy(ROOT);
    }

    private LinkPolicy linkPolicy;

}
