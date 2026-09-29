package com.visordocs.interfaces.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentStatusEventTest {

    @Test
    void indexed_createsEventWithCorrectValues() {
        DocumentStatusEvent event = DocumentStatusEvent.indexed("doc-123");

        assertThat(event.documentId()).isEqualTo("doc-123");
        assertThat(event.status()).isEqualTo("INDEXED");
        assertThat(event.errorMessage()).isNull();
    }

    @Test
    void error_createsEventWithCorrectValues() {
        DocumentStatusEvent event = DocumentStatusEvent.error("doc-456", "Something went wrong");

        assertThat(event.documentId()).isEqualTo("doc-456");
        assertThat(event.status()).isEqualTo("ERROR");
        assertThat(event.errorMessage()).isEqualTo("Something went wrong");
    }

    @Test
    void recordEquality_worksCorrectly() {
        DocumentStatusEvent e1 = DocumentStatusEvent.indexed("doc-1");
        DocumentStatusEvent e2 = DocumentStatusEvent.indexed("doc-1");
        DocumentStatusEvent e3 = DocumentStatusEvent.error("doc-1", "err");

        assertThat(e1).isEqualTo(e2);
        assertThat(e1).hasSameHashCodeAs(e2);
        assertThat(e1).isNotEqualTo(e3);
    }

    @Test
    void toString_containsAllFields() {
        DocumentStatusEvent event = DocumentStatusEvent.error("doc-789", "failed");

        String str = event.toString();

        assertThat(str).contains("doc-789");
        assertThat(str).contains("ERROR");
        assertThat(str).contains("failed");
    }
}