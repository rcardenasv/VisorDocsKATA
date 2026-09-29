package com.visordocs.application;

import com.visordocs.domain.Document;
import com.visordocs.domain.DocumentStatus;
import com.visordocs.infrastructure.extraction.TextExtractorService;
import com.visordocs.infrastructure.persistence.DocumentRepository;
import com.visordocs.infrastructure.search.ElasticsearchService;
import com.visordocs.infrastructure.sse.SseService;
import com.visordocs.interfaces.dto.DocumentStatusEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentProcessingJobTest {

    @Mock
    DocumentRepository documentRepository;

    @Mock
    TextExtractorService textExtractor;

    @Mock
    ElasticsearchService elasticsearchService;

    @Mock
    SseService sseService;

    private DocumentProcessingJob job;
    @TempDir
    Path tempDir;

    @Test
    void processDocument_success_updatesStatusAndEmitsIndexedEvent() throws Exception {
        job = new DocumentProcessingJob();
        job.documentRepository = documentRepository;
        job.textExtractor = textExtractor;
        job.elasticsearchService = elasticsearchService;
        job.sseService = sseService;
        job.uploadDir = tempDir.toString();

        Document doc = new Document();
        doc.id = "doc-123";
        doc.title = "Title";
        doc.author = "Author";
        doc.category = "Category";
        doc.tags = new String[]{"tag1"};
        doc.version = "1.0";
        doc.fileType = "txt";
        doc.status = DocumentStatus.PROCESSING;

        Path filePath = tempDir.resolve("doc-123");
        Files.writeString(filePath, "extracted content", StandardCharsets.UTF_8);

        when(documentRepository.findById("doc-123")).thenReturn(doc);
        when(textExtractor.extract(filePath, "txt")).thenReturn("extracted content");

        job.processDocument("doc-123");

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).persist(docCaptor.capture());
        Document saved = docCaptor.getValue();
        assertThat(saved.id).isEqualTo("doc-123");
        assertThat(saved.content).isEqualTo("extracted content");
        assertThat(saved.status).isEqualTo(DocumentStatus.INDEXED);

        verify(elasticsearchService).indexDocument(
                eq("doc-123"), eq("Title"), eq("Author"), eq("Category"),
                eq(new String[]{"tag1"}), eq("1.0"), eq("extracted content")
        );

        ArgumentCaptor<DocumentStatusEvent> eventCaptor = ArgumentCaptor.forClass(DocumentStatusEvent.class);
        verify(sseService).emitEvent(eventCaptor.capture());
        DocumentStatusEvent event = eventCaptor.getValue();
        assertThat(event.documentId()).isEqualTo("doc-123");
        assertThat(event.status()).isEqualTo("INDEXED");
        assertThat(event.errorMessage()).isNull();
    }

    @Test
    void processDocument_notFound_returnsSilentlyWithoutError() {
        job = new DocumentProcessingJob();
        job.documentRepository = documentRepository;
        job.textExtractor = textExtractor;
        job.elasticsearchService = elasticsearchService;
        job.sseService = sseService;
        job.uploadDir = tempDir.toString();

        when(documentRepository.findById("missing")).thenReturn(null);

        job.processDocument("missing");

        verifyNoInteractions(textExtractor, elasticsearchService, sseService);
        verify(documentRepository).findById("missing");
    }

    @Test
    void processDocument_extractionFails_updatesStatusErrorAndEmitsErrorEvent() throws Exception {
        job = new DocumentProcessingJob();
        job.documentRepository = documentRepository;
        job.textExtractor = textExtractor;
        job.elasticsearchService = elasticsearchService;
        job.sseService = sseService;
        job.uploadDir = tempDir.toString();

        Document doc = new Document();
        doc.id = "doc-456";
        doc.title = "Title";
        doc.author = "Author";
        doc.category = "Category";
        doc.tags = new String[]{};
        doc.version = "1.0";
        doc.fileType = "pdf";
        doc.status = DocumentStatus.PROCESSING;

        Path filePath = tempDir.resolve("doc-456");
        Files.writeString(filePath, "", StandardCharsets.UTF_8);

        when(documentRepository.findById("doc-456")).thenReturn(doc);
        when(textExtractor.extract(filePath, "pdf"))
                .thenThrow(new RuntimeException("PDF parse failed"));

        job.processDocument("doc-456");

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).persist(docCaptor.capture());
        Document saved = docCaptor.getValue();
        assertThat(saved.status).isEqualTo(DocumentStatus.ERROR);
        assertThat(saved.errorMessage).contains("PDF parse failed");

        verifyNoInteractions(elasticsearchService);

        ArgumentCaptor<DocumentStatusEvent> eventCaptor = ArgumentCaptor.forClass(DocumentStatusEvent.class);
        verify(sseService).emitEvent(eventCaptor.capture());
        DocumentStatusEvent event = eventCaptor.getValue();
        assertThat(event.documentId()).isEqualTo("doc-456");
        assertThat(event.status()).isEqualTo("ERROR");
        assertThat(event.errorMessage()).contains("PDF parse failed");
    }

    @Test
    void processDocument_indexingFails_updatesStatusErrorAndEmitsErrorEvent() throws Exception {
        job = new DocumentProcessingJob();
        job.documentRepository = documentRepository;
        job.textExtractor = textExtractor;
        job.elasticsearchService = elasticsearchService;
        job.sseService = sseService;
        job.uploadDir = tempDir.toString();

        Document doc = new Document();
        doc.id = "doc-789";
        doc.title = "Title";
        doc.author = "Author";
        doc.category = "Category";
        doc.tags = new String[]{"tag"};
        doc.version = "1.0";
        doc.fileType = "md";
        doc.status = DocumentStatus.PROCESSING;

        Path filePath = tempDir.resolve("doc-789");
        Files.writeString(filePath, "content", StandardCharsets.UTF_8);

        when(documentRepository.findById("doc-789")).thenReturn(doc);
        when(textExtractor.extract(filePath, "md")).thenReturn("content");
        doAnswer(inv -> { throw new RuntimeException("ES connection failed"); })
                .when(elasticsearchService).indexDocument(anyString(), anyString(), anyString(), anyString(),
                        any(String[].class), anyString(), anyString());

        job.processDocument("doc-789");

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).persist(docCaptor.capture());
        Document saved = docCaptor.getValue();
        assertThat(saved.status).isEqualTo(DocumentStatus.ERROR);
        assertThat(saved.errorMessage).contains("ES connection failed");

        ArgumentCaptor<DocumentStatusEvent> eventCaptor = ArgumentCaptor.forClass(DocumentStatusEvent.class);
        verify(sseService).emitEvent(eventCaptor.capture());
        DocumentStatusEvent event = eventCaptor.getValue();
        assertThat(event.status()).isEqualTo("ERROR");
        assertThat(event.errorMessage()).contains("ES connection failed");
    }

    @Test
    void processDocument_fileNotFoundOnDisk_handlesGracefully() throws Exception {
        job = new DocumentProcessingJob();
        job.documentRepository = documentRepository;
        job.textExtractor = textExtractor;
        job.elasticsearchService = elasticsearchService;
        job.sseService = sseService;
        job.uploadDir = tempDir.toString();

        Document doc = new Document();
        doc.id = "doc-999";
        doc.title = "Title";
        doc.author = "Author";
        doc.category = "Category";
        doc.tags = new String[]{};
        doc.version = "1.0";
        doc.fileType = "txt";
        doc.status = DocumentStatus.PROCESSING;

        when(documentRepository.findById("doc-999")).thenReturn(doc);
        when(textExtractor.extract(any(Path.class), eq("txt")))
                .thenThrow(new RuntimeException("File not found on disk"));

        job.processDocument("doc-999");

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).persist(docCaptor.capture());
        Document saved = docCaptor.getValue();
        assertThat(saved.status).isEqualTo(DocumentStatus.ERROR);
        assertThat(saved.errorMessage).contains("File not found on disk");

        verifyNoInteractions(elasticsearchService);
        ArgumentCaptor<DocumentStatusEvent> eventCaptor = ArgumentCaptor.forClass(DocumentStatusEvent.class);
        verify(sseService).emitEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().status()).isEqualTo("ERROR");
    }
}