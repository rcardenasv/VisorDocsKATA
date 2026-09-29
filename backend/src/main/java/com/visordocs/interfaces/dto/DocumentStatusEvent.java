package com.visordocs.interfaces.dto;

/**
 * SSE event payload sent when a document's status changes.
 */
public record DocumentStatusEvent(
        String documentId,
        String status,
        String errorMessage
) {
    public static DocumentStatusEvent indexed(String documentId) {
        return new DocumentStatusEvent(documentId, "INDEXED", null);
    }

    public static DocumentStatusEvent error(String documentId, String errorMessage) {
        return new DocumentStatusEvent(documentId, "ERROR", errorMessage);
    }
}
