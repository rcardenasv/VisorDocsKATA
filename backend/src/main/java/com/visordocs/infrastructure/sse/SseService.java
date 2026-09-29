package com.visordocs.infrastructure.sse;

import com.visordocs.interfaces.dto.DocumentStatusEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SseService {
    private static final Logger LOG = Logger.getLogger(SseService.class);
    
    private final BroadcastProcessor<DocumentStatusEvent> processor = BroadcastProcessor.create();

    public void emitEvent(DocumentStatusEvent event) {
        LOG.infof("Emitting SSE event for document %s, status: %s", event.documentId(), event.status());
        processor.onNext(event);
    }

    public Multi<DocumentStatusEvent> subscribe(String documentId) {
        return processor.filter(event -> event.documentId().equals(documentId));
    }
}
