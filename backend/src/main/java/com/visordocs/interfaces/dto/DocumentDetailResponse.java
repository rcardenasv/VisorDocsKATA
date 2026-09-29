package com.visordocs.interfaces.dto;

import java.time.Instant;

/**
 * Response DTO for document detail (GET /api/documents/{id}).
 */
public record DocumentDetailResponse(
        String documentId,
        String status,
        DocumentMetadata metadata,
        String content,
        String originalFileName,
        String fileType,
        Instant createdAt,
        Instant updatedAt,
        String errorMessage
) {

    public record DocumentMetadata(
            String title,
            String author,
            String category,
            String[] tags,
            String version
    ) {
    }
}
