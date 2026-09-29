package com.visordocs.domain;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class AppExceptionTest {

    @Test
    void validationError_createsExceptionWithCorrectCodeAndStatus() {
        AppException e = AppException.validationError("Title is required");

        assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(e.getMessage()).isEqualTo("Title is required");
        assertThat(e.getHttpStatus()).isEqualTo(400);
        assertThat(e.getCause()).isNull();
    }

    @Test
    void unsupportedFileType_createsExceptionWithCorrectCodeAndMessage() {
        AppException e = AppException.unsupportedFileType("xyz");

        assertThat(e.getCode()).isEqualTo("UNSUPPORTED_FILE_TYPE");
        assertThat(e.getMessage()).contains("xyz").contains("txt, pdf, md");
        assertThat(e.getHttpStatus()).isEqualTo(400);
    }

    @Test
    void fileSizeExceeded_createsExceptionWithCorrectCodeAndMessage() {
        AppException e = AppException.fileSizeExceeded(10);

        assertThat(e.getCode()).isEqualTo("FILE_SIZE_EXCEEDED");
        assertThat(e.getMessage()).contains("10 MB");
        assertThat(e.getHttpStatus()).isEqualTo(400);
    }

    @Test
    void documentNotFound_createsExceptionWithCorrectCodeAndMessage() {
        AppException e = AppException.documentNotFound("doc-123");

        assertThat(e.getCode()).isEqualTo("DOCUMENT_NOT_FOUND");
        assertThat(e.getMessage()).contains("doc-123");
        assertThat(e.getHttpStatus()).isEqualTo(404);
    }

    @Test
    void textExtractionError_createsExceptionWithCause() {
        Throwable cause = new IOException("read failed");
        AppException e = AppException.textExtractionError("detail", cause);

        assertThat(e.getCode()).isEqualTo("TEXT_EXTRACTION_ERROR");
        assertThat(e.getMessage()).contains("detail");
        assertThat(e.getHttpStatus()).isEqualTo(500);
        assertThat(e.getCause()).isSameAs(cause);
    }

    @Test
    void indexingError_createsExceptionWithCause() {
        Throwable cause = new RuntimeException("es down");
        AppException e = AppException.indexingError("detail", cause);

        assertThat(e.getCode()).isEqualTo("INDEXING_ERROR");
        assertThat(e.getHttpStatus()).isEqualTo(500);
        assertThat(e.getCause()).isSameAs(cause);
    }

    @Test
    void searchError_createsExceptionWithCause() {
        AppException e = AppException.searchError("detail", new Exception("cause"));

        assertThat(e.getCode()).isEqualTo("SEARCH_ERROR");
        assertThat(e.getHttpStatus()).isEqualTo(500);
    }

    @Test
    void internalError_createsExceptionWithCause() {
        AppException e = AppException.internalError("detail", new Exception("cause"));

        assertThat(e.getCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(e.getHttpStatus()).isEqualTo(500);
    }

    @Test
    void constructor_withMessage_only_setsFieldsCorrectly() {
        AppException e = new AppException("TEST_CODE", "test message", 418);

        assertThat(e.getCode()).isEqualTo("TEST_CODE");
        assertThat(e.getMessage()).isEqualTo("test message");
        assertThat(e.getHttpStatus()).isEqualTo(418);
        assertThat(e.getCause()).isNull();
    }

    @Test
    void constructor_withMessageAndCause_setsFieldsCorrectly() {
        Throwable cause = new IllegalStateException("root cause");
        AppException e = new AppException("TEST_CODE", "test message", 418, cause);

        assertThat(e.getCode()).isEqualTo("TEST_CODE");
        assertThat(e.getMessage()).isEqualTo("test message");
        assertThat(e.getHttpStatus()).isEqualTo(418);
        assertThat(e.getCause()).isSameAs(cause);
    }
}