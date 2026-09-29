package com.visordocs.interfaces;

import com.visordocs.domain.AppException;
import com.visordocs.interfaces.dto.ErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.util.UUID;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<AppException> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    @Override
    public Response toResponse(AppException exception) {
        String correlationId = UUID.randomUUID().toString();
        LOG.errorf(exception, "AppException [%s]: %s (CorrelationId: %s)", 
                exception.getCode(), exception.getMessage(), correlationId);

        return Response.status(exception.getHttpStatus())
                .entity(ErrorResponse.of(exception.getCode(), exception.getMessage(), correlationId))
                .build();
    }
}
