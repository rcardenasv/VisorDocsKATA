package com.visordocs.application;

import com.visordocs.domain.Document;
import com.visordocs.domain.DocumentStatus;
import com.visordocs.infrastructure.extraction.TextExtractorService;
import com.visordocs.infrastructure.persistence.DocumentRepository;
import com.visordocs.infrastructure.search.ElasticsearchService;
import com.visordocs.infrastructure.sse.SseService;
import com.visordocs.interfaces.dto.DocumentStatusEvent;
import io.quarkus.vertx.ConsumeEvent;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.file.Path;

@ApplicationScoped
public class DocumentProcessingJob {

    private static final Logger LOG = Logger.getLogger(DocumentProcessingJob.class);

    @Inject
    DocumentRepository documentRepository;

    @Inject
    TextExtractorService textExtractor;

    @Inject
    ElasticsearchService elasticsearchService;

    @Inject
    SseService sseService;

    @ConfigProperty(name = "app.upload.dir")
    String uploadDir;

    @ConsumeEvent("document.process")
    @Blocking
    @Transactional
    public void processDocument(String documentId) {
        LOG.infof("Starting async processing for document: %s", documentId);
        
        Document document = documentRepository.findById(documentId);
        if (document == null) {
            LOG.errorf("Document %s not found for processing", documentId);
            return;
        }

        try {
            // Extract text
            Path filePath = Path.of(uploadDir, document.id);
            String content = textExtractor.extract(filePath, document.fileType);
            
            // Sanitize content to remove null bytes that PostgreSQL doesn't allow in UTF8
            content = content.replace("\u0000", "");
            
            // Save content to DB
            document.content = content;
            
            // Index in ES
            // Ensure index exists first (idempotent, safe to call multiple times)
            elasticsearchService.ensureIndexExists();
            
            elasticsearchService.indexDocument(
                    document.id, document.title, document.author, document.category, document.tags, document.version, content
            );
            
            // Update status
            document.status = DocumentStatus.INDEXED;
            documentRepository.persist(document);
            
            // Emit event
            sseService.emitEvent(DocumentStatusEvent.indexed(document.id));
            LOG.infof("Successfully processed document: %s", documentId);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to process document: %s", documentId);
            document.status = DocumentStatus.ERROR;
            // Sanitize error message to remove null bytes that PostgreSQL doesn't allow in UTF8
            String sanitizedMessage = e.getMessage() != null ? e.getMessage().replace("\u0000", "") : "Unknown error";
            document.errorMessage = sanitizedMessage;
            documentRepository.persist(document);
            
            sseService.emitEvent(DocumentStatusEvent.error(document.id, sanitizedMessage));
        }
    }
}
