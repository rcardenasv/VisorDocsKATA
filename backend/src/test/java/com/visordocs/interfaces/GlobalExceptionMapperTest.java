package com.visordocs.interfaces;

import com.visordocs.domain.AppException;
import com.visordocs.interfaces.dto.ErrorResponse;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionMapperTest {

    private final GlobalExceptionMapper mapper = new GlobalExceptionMapper();

    @Test
    void toResponse_mapsValidationErrorTo400WithErrorBody() {
        AppException exception = AppException.validationError("Title is required");

        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(400);
        ErrorResponse body = (ErrorResponse) response.getEntity();
        assertThat(body).isNotNull();
        assertThat(body.error().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.error().message()).isEqualTo("Title is required");
        assertThat(body.error().correlationId()).isNotNull().matches(id -> {
            try { UUID.fromString(id); return true; } catch (Exception e) { return false; }
        });
    }

    @Test
    void toResponse_mapsUnsupportedFileTypeTo400() {
        AppException exception = AppException.unsupportedFileType("xyz");

        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(400);
        ErrorResponse body = (ErrorResponse) response.getEntity();
        assertThat(body.error().code()).isEqualTo("UNSUPPORTED_FILE_TYPE");
    }

    @Test
    void toResponse_mapsDocumentNotFoundTo404() {
        AppException exception = AppException.documentNotFound("missing-id");

        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(404);
        ErrorResponse body = (ErrorResponse) response.getEntity();
        assertThat(body.error().code()).isEqualTo("DOCUMENT_NOT_FOUND");
    }

    @Test
    void toResponse_mapsInternalErrorTo500() {
        AppException exception = AppException.internalError("boom", new RuntimeException("cause"));

        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(500);
        ErrorResponse body = (ErrorResponse) response.getEntity();
        assertThat(body.error().code()).isEqualTo("INTERNAL_SERVER_ERROR");
    }

    @Test
    void toResponse_generatesUniqueCorrelationIdPerCall() {
        AppException exception = AppException.validationError("x");

        Response r1 = mapper.toResponse(exception);
        Response r2 = mapper.toResponse(exception);

        String id1 = ((ErrorResponse) r1.getEntity()).error().correlationId();
        String id2 = ((ErrorResponse) r2.getEntity()).error().correlationId();

        assertThat(id1).isNotEqualTo(id2);
    }
}