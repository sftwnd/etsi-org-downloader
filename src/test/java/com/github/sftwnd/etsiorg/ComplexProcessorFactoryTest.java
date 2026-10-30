package com.github.sftwnd.etsiorg;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class ComplexProcessorFactoryTest {

    @Test
    void processorOfEmptyProcessor() {
        when(href.isRegularFile()).thenReturn(true);
        Stream.of("log", "tmp", "temp")
                .map(ext -> "file."+ext)
                .peek(when(page.fileName())::thenReturn)
                .map(unused -> factory.processor(page))
                .peek(Assertions::assertNotNull)
                .forEach(processor -> assertEquals(EmptyProcessor.class, processor.getClass()));
    }

    @Test
    void processorOfRegularFile() {
        when(href.isRegularFile()).thenReturn(true);
        when(page.fileName()).thenReturn("file.pdf");
        assertDoesNotThrow(() -> this.factory.processor(this.page));
        var processor = this.factory.processor(this.page);
        assertNotNull(processor);
        assertEquals(FileSaveProcessor.class, processor.getClass());
        FileSaveProcessor fileSaveProcessor = (FileSaveProcessor)processor;
        assertEquals(this.page, fileSaveProcessor.getPage());
        assertSame(this.destination, fileSaveProcessor.getDestination());
    }

    @Test
    void processorOfHtmlFile() {
        when(href.isRegularFile()).thenReturn(false);
        when(page.fileName()).thenReturn("file.html");
        assertDoesNotThrow(() -> this.factory.processor(this.page));
        var processor = this.factory.processor(this.page);
        assertNotNull(processor);
        assertEquals(TextHtmlProcessor.class, processor.getClass());
        TextHtmlProcessor textHtmlProcessor = (TextHtmlProcessor)processor;
        assertEquals(this.page, textHtmlProcessor.getPage());
        assertEquals(this.factory, textHtmlProcessor.getProcessorFactory());
        assertSame(this.linkPolicy, textHtmlProcessor.getLinkPolicy());
        assertEquals(this.executor, textHtmlProcessor.getExecutor());
        assertEquals(this.onExpires, textHtmlProcessor.getOnExpires());
    }

    @Test
    void nullDestinationConstructorTest() {
        assertThrows(NullPointerException.class, () -> new ComplexProcessorFactory(null, this.linkPolicy, this.executor, this.onExpires));
    }

    @Test
    void nullLinkPolicyConstructorTest() {
        assertThrows(NullPointerException.class, () -> new ComplexProcessorFactory(this.destination, null, this.executor, this.onExpires));
    }

    @Test
    void getLinkPolicyTest() {
        assertSame(this.linkPolicy, this.factory.getLinkPolicy());
    }

    @Test
    void getDestinationTest() {
        assertSame(this.destination, this.factory.getDestination());
    }

    @Test
    void getExecutorTest() {
        assertSame(this.executor, this.factory.getExecutor());
    }

    @Test
    void getOnExpiresTest() {
        assertSame(this.onExpires, this.factory.getOnExpires());
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void startUp() {
        this.href = mock(HREF.class);
        this.page = mock(Page.class);
        when(this.page.getHref()).thenReturn(this.href);
        this.destination = new Destination(Path.of("target"));
        this.linkPolicy = new LinkPolicy(URI.create("http://localhost/deliver/"));
        this.executor = mock(Executor.class);
        this.onExpires = (Consumer<Collection<URI>>)Mockito.mock(Consumer.class);
        this.factory = spy(new ComplexProcessorFactory(this.destination, this.linkPolicy, this.executor, this.onExpires));
    }

    @AfterEach
    void tearDown() {
        this.href = null;
        this.page = null;
        this.destination = null;
        this.linkPolicy = null;
        this.executor = null;
        this.onExpires = null;
        this.factory = null;
    }

    private HREF href;
    private Page page;
    private Destination destination;
    private LinkPolicy linkPolicy;
    private Executor executor;
    private Consumer<Collection<URI>> onExpires;
    private ComplexProcessorFactory factory;

}