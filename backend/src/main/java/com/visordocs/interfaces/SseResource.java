package com.visordocs.interfaces;

import com.visordocs.infrastructure.sse.SseService;
import com.visordocs.interfaces.dto.DocumentStatusEvent;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestStreamElementType;

@Path("/api/events")
public class SseResource {

    @Inject
    SseService sseService;

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<DocumentStatusEvent> streamEvents(@QueryParam("documentId") String documentId) {
        return sseService.subscribe(documentId);
    }
}
