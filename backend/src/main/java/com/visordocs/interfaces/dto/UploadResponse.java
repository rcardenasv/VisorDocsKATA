package com.visordocs.interfaces.dto;

/**
 * Response DTO for document upload.
 * Returned immediately with HTTP 202 after accepting the upload.
 */
public record UploadResponse(
        String documentId,
        String status
) {
}
