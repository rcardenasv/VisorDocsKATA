package com.visordocs.interfaces;

import com.visordocs.infrastructure.sse.SseService;
import com.visordocs.interfaces.dto.DocumentStatusEvent;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SseResourceTest {

    @Mock
    SseService sseService;

    private SseResource resource;

    @Test
    void streamEvents_returnsMultiFromSseService() {
        resource = new SseResource();
        resource.sseService = sseService;

        Multi<DocumentStatusEvent> expectedMulti = Multi.createFrom().empty();
        when(sseService.subscribe("doc-123")).thenReturn(expectedMulti);

        Multi<DocumentStatusEvent> result = resource.streamEvents("doc-123");

        assertThat(result).isSameAs(expectedMulti);
        verify(sseService).subscribe("doc-123");
    }

    @Test
    void streamEvents_withNullDocumentId_callsSubscribeWithNull() {
        resource = new SseResource();
        resource.sseService = sseService;

        Multi<DocumentStatusEvent> expectedMulti = Multi.createFrom().empty();
        when(sseService.subscribe(null)).thenReturn(expectedMulti);

        Multi<DocumentStatusEvent> result = resource.streamEvents(null);

        assertThat(result).isSameAs(expectedMulti);
        verify(sseService).subscribe(null);
    }
}