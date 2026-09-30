package com.visordocs.infrastructure.sse;

import com.visordocs.interfaces.dto.DocumentStatusEvent;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SseServiceTest {

    private SseService sseService;

    @BeforeEach
    void setUp() {
        sseService = new SseService();
    }

    @AfterEach
    void tearDown() {
        // No cleanup needed, BroadcastProcessor doesn't need explicit close
    }

    @Test
    void subscribe_filtersByDocumentId() throws Exception {
        Multi<DocumentStatusEvent> multi = sseService.subscribe("doc-1");

        CompletableFuture<DocumentStatusEvent> future = new CompletableFuture<>();
        multi.subscribe().with(future::complete, failure -> {
            throw new AssertionError("Unexpected stream failure", failure);
        });

        sseService.emitEvent(new DocumentStatusEvent("doc-1", "INDEXED", null));

        DocumentStatusEvent received = future.get(1, TimeUnit.SECONDS);
        assertThat(received.documentId()).isEqualTo("doc-1");
        assertThat(received.status()).isEqualTo("INDEXED");
        assertThat(received.errorMessage()).isNull();
    }

    @Test
    void subscribe_ignoresEventsForOtherDocumentId() throws Exception {
        Multi<DocumentStatusEvent> multi = sseService.subscribe("doc-1");

        CompletableFuture<DocumentStatusEvent> future = new CompletableFuture<>();
        multi.subscribe().with(future::complete, failure -> {
            throw new AssertionError("Unexpected stream failure", failure);
        });

        // Emit for different documentId - should be filtered out
        sseService.emitEvent(new DocumentStatusEvent("doc-2", "INDEXED", null));
        // Emit for correct documentId
        sseService.emitEvent(new DocumentStatusEvent("doc-1", "INDEXED", null));

        DocumentStatusEvent received = future.get(1, TimeUnit.SECONDS);
        assertThat(received.documentId()).isEqualTo("doc-1");
    }

    @Test
    void subscribe_receivesMultipleEventsForSameDocumentId() throws Exception {
        Multi<DocumentStatusEvent> multi = sseService.subscribe("doc-1");
        java.util.concurrent.ConcurrentLinkedQueue<DocumentStatusEvent> received = new java.util.concurrent.ConcurrentLinkedQueue<>();

        multi.subscribe().with(received::add, failure -> {
            throw new AssertionError("Unexpected stream failure", failure);
        });

        sseService.emitEvent(new DocumentStatusEvent("doc-1", "INDEXED", null));
        sseService.emitEvent(new DocumentStatusEvent("doc-1", "ERROR", "failed"));
        sseService.emitEvent(new DocumentStatusEvent("doc-2", "INDEXED", null));

        // Wait for events
        awaitUntil(() -> received.size() >= 2, 1000);

        assertThat(received).hasSize(2);
        assertThat(received).extracting((DocumentStatusEvent event) -> event.documentId()).containsOnly("doc-1");
        assertThat(received).extracting((DocumentStatusEvent event) -> event.status()).containsExactly("INDEXED", "ERROR");
    }

    @Test
    void emitEvent_beforeSubscription_doesNotCauseError() {
        // Emit first, then subscribe - events before subscription are dropped (BroadcastProcessor behavior)
        sseService.emitEvent(new DocumentStatusEvent("doc-1", "INDEXED", null));

        Multi<DocumentStatusEvent> multi = sseService.subscribe("doc-1");
        CompletableFuture<DocumentStatusEvent> future = new CompletableFuture<>();
        multi.subscribe().with(future::complete, failure -> {
            throw new AssertionError("Unexpected stream failure", failure);
        });

        // Emit new event after subscription
        sseService.emitEvent(new DocumentStatusEvent("doc-1", "ERROR", "oops"));

        DocumentStatusEvent received = future.join();
        assertThat(received.status()).isEqualTo("ERROR");
        assertThat(received.errorMessage()).isEqualTo("oops");
    }

    @Test
    void multipleSubscribers_receiveSameEvents() throws Exception {
        Multi<DocumentStatusEvent> multi1 = sseService.subscribe("doc-1");
        Multi<DocumentStatusEvent> multi2 = sseService.subscribe("doc-1");

        CompletableFuture<DocumentStatusEvent> future1 = new CompletableFuture<>();
        CompletableFuture<DocumentStatusEvent> future2 = new CompletableFuture<>();

        multi1.subscribe().with(future1::complete, failure -> {
            throw new AssertionError("Unexpected stream failure", failure);
        });
        multi2.subscribe().with(future2::complete, failure -> {
            throw new AssertionError("Unexpected stream failure", failure);
        });

        sseService.emitEvent(new DocumentStatusEvent("doc-1", "INDEXED", null));

        DocumentStatusEvent received1 = future1.get(1, TimeUnit.SECONDS);
        DocumentStatusEvent received2 = future2.get(1, TimeUnit.SECONDS);

        assertThat(received1.status()).isEqualTo("INDEXED");
        assertThat(received2.status()).isEqualTo("INDEXED");
    }

    @Test
    void emitEvent_withErrorStatus_preservesErrorMessage() throws Exception {
        Multi<DocumentStatusEvent> multi = sseService.subscribe("doc-error");

        CompletableFuture<DocumentStatusEvent> future = new CompletableFuture<>();
        multi.subscribe().with(future::complete, failure -> {
            throw new AssertionError("Unexpected stream failure", failure);
        });

        sseService.emitEvent(new DocumentStatusEvent("doc-error", "ERROR", "Connection timeout"));

        DocumentStatusEvent received = future.get(1, TimeUnit.SECONDS);
        assertThat(received.status()).isEqualTo("ERROR");
        assertThat(received.errorMessage()).isEqualTo("Connection timeout");
    }

    @Test
    void subscribe_returnsNewMultiEachCall() {
        Multi<DocumentStatusEvent> multi1 = sseService.subscribe("doc-1");
        Multi<DocumentStatusEvent> multi2 = sseService.subscribe("doc-1");

        assertThat(multi1).isNotSameAs(multi2);
    }

    private void awaitUntil(java.util.function.Supplier<Boolean> condition, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.get()) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new AssertionError("Condition not met within " + timeoutMs + "ms");
    }
}