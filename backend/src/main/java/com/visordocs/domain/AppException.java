package com.visordocs.domain;

/**
 * Base application exception with error code and HTTP status.
 * All domain-specific errors extend this class.
 */
public class AppException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public AppException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public AppException(String code, String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    // --- Concrete error factories ---

    public static AppException validationError(String message) {
        return new AppException("VALIDATION_ERROR", message, 400);
    }

    public static AppException unsupportedFileType(String extension) {
        return new AppException("UNSUPPORTED_FILE_TYPE",
                "File type '" + extension + "' is not supported. Allowed: txt, pdf, md", 400);
    }

    public static AppException fileSizeExceeded(long maxMb) {
        return new AppException("FILE_SIZE_EXCEEDED",
                "File size exceeds the maximum allowed (" + maxMb + " MB)", 400);
    }

    public static AppException documentNotFound(String id) {
        return new AppException("DOCUMENT_NOT_FOUND",
                "Document with id '" + id + "' was not found", 404);
    }

    public static AppException textExtractionError(String detail, Throwable cause) {
        return new AppException("TEXT_EXTRACTION_ERROR",
                "Failed to extract text from document: " + detail, 500, cause);
    }

    public static AppException indexingError(String detail, Throwable cause) {
        return new AppException("INDEXING_ERROR",
                "Failed to index document in Elasticsearch: " + detail, 500, cause);
    }

    public static AppException searchError(String detail, Throwable cause) {
        return new AppException("SEARCH_ERROR",
                "Search query failed: " + detail, 500, cause);
    }
}
