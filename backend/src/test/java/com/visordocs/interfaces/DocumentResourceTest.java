package com.visordocs.interfaces;

import com.visordocs.domain.AppException;
import com.visordocs.domain.Document;
import com.visordocs.domain.DocumentStatus;
import com.visordocs.infrastructure.persistence.DocumentRepository;
import com.visordocs.interfaces.dto.DocumentDetailResponse;
import com.visordocs.interfaces.dto.UploadResponse;
import io.vertx.core.eventbus.EventBus;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentResourceTest {

    @Mock
    DocumentRepository documentRepository;

    @Mock
    EventBus eventBus;

    @Mock
    FileUpload fileUpload;

    private DocumentResource resource;
    @TempDir
    Path tempDir;

    private void setUpResource() {
        resource = new DocumentResource();
        resource.documentRepository = documentRepository;
        resource.eventBus = eventBus;
        resource.uploadDir = tempDir.toString();
        resource.maxSizeMb = 10;
        resource.allowedExtensions = java.util.List.of("txt", "pdf", "md");
    }

    private FileUpload mockFile(String name, long size, Path path) {
        FileUpload file = Mockito.mock(FileUpload.class);
        lenient().when(file.fileName()).thenReturn(name);
        lenient().when(file.size()).thenReturn(size);
        if (path != null) lenient().when(file.filePath()).thenReturn(path);
        return file;
    }

    @Test
    void uploadDocument_nullFile_throwsValidationError() {
        setUpResource();

        assertThatThrownBy(() -> resource.uploadDocument(null, "Title", "Author", "Category", "tags", "1.0"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR")
                .hasMessageContaining("File is required");

        verifyNoInteractions(documentRepository, eventBus);
    }

    @Test
    void uploadDocument_blankTitle_throwsValidationError() {
        setUpResource();
        FileUpload file = mockFile("test.txt", 100L, tempDir.resolve("test.txt"));

        assertThatThrownBy(() -> resource.uploadDocument(file, "  ", "Author", "Category", "tags", "1.0"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR")
                .hasMessageContaining("Title is required");

        verifyNoInteractions(documentRepository, eventBus);
    }

    @Test
    void uploadDocument_blankAuthor_throwsValidationError() {
        setUpResource();
        FileUpload file = mockFile("test.txt", 100L, tempDir.resolve("test.txt"));

        assertThatThrownBy(() -> resource.uploadDocument(file, "Title", "", "Category", "tags", "1.0"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR")
                .hasMessageContaining("Author is required");
    }

    @Test
    void uploadDocument_blankCategory_throwsValidationError() {
        setUpResource();
        FileUpload file = mockFile("test.txt", 100L, tempDir.resolve("test.txt"));

        assertThatThrownBy(() -> resource.uploadDocument(file, "Title", "Author", "   ", "tags", "1.0"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR")
                .hasMessageContaining("Category is required");
    }

    @Test
    void uploadDocument_blankVersion_throwsValidationError() {
        setUpResource();
        FileUpload file = mockFile("test.txt", 100L, tempDir.resolve("test.txt"));

        assertThatThrownBy(() -> resource.uploadDocument(file, "Title", "Author", "Category", "tags", ""))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR")
                .hasMessageContaining("Version is required");
    }

    @Test
    void uploadDocument_unsupportedExtension_throwsUnsupportedFileType() {
        setUpResource();
        FileUpload file = mockFile("test.xyz", 100L, tempDir.resolve("test.xyz"));

        assertThatThrownBy(() -> resource.uploadDocument(file, "Title", "Author", "Category", "tags", "1.0"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "UNSUPPORTED_FILE_TYPE")
                .hasMessageContaining("xyz");

        verifyNoInteractions(documentRepository, eventBus);
    }

    @Test
    void uploadDocument_fileSizeExceeded_throwsFileSizeExceeded(@TempDir Path tempDir2) throws java.io.IOException {
        setUpResource();
        Path largeFile = tempDir2.resolve("large.txt");
        Files.writeString(largeFile, "x".repeat(15 * 1024 * 1024), StandardCharsets.UTF_8);
        FileUpload file = mockFile("large.txt", 15 * 1024 * 1024L, largeFile);

        assertThatThrownBy(() -> resource.uploadDocument(file, "Title", "Author", "Category", "tags", "1.0"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "FILE_SIZE_EXCEEDED")
                .hasMessageContaining("10 MB");

        verifyNoInteractions(documentRepository, eventBus);
    }

    @Test
    void uploadDocument_happyPath_returns202AndPersistsDocument(@TempDir Path tempDir2) throws java.io.IOException {
        setUpResource();
        Path filePath = tempDir2.resolve("source.txt");
        Files.writeString(filePath, "file content", StandardCharsets.UTF_8);
        FileUpload file = mockFile("source.txt", 100L, filePath);

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        doAnswer(inv -> {
            Document d = inv.getArgument(0);
            d.id = "generated-uuid-123";
            return null;
        }).when(documentRepository).persist(any(Document.class));

        Response response = resource.uploadDocument(file, "My Title", "My Author", "My Category", "tag1,tag2", "1.0");

        assertThat(response.getStatus()).isEqualTo(202);
        UploadResponse body = (UploadResponse) response.getEntity();
        assertThat(body.documentId()).isEqualTo("generated-uuid-123");
        assertThat(body.status()).isEqualTo("PROCESSING");

        verify(documentRepository).persist(docCaptor.capture());
        Document saved = docCaptor.getValue();
        assertThat(saved.title).isEqualTo("My Title");
        assertThat(saved.author).isEqualTo("My Author");
        assertThat(saved.category).isEqualTo("My Category");
        assertThat(saved.tags).containsExactly("tag1", "tag2");
        assertThat(saved.version).isEqualTo("1.0");
        assertThat(saved.originalFileName).isEqualTo("source.txt");
        assertThat(saved.fileType).isEqualTo("txt");
        assertThat(saved.fileSize).isEqualTo(100L);
        assertThat(saved.status).isEqualTo(DocumentStatus.PROCESSING);

        verify(eventBus).publish("document.process", "generated-uuid-123");

        // File copied to uploadDir
        assertThat(Files.exists(tempDir.resolve("generated-uuid-123"))).isTrue();
    }

    @Test
    void uploadDocument_emptyTags_createsEmptyTagsArray(@TempDir Path tempDir2) throws java.io.IOException {
        setUpResource();
        Path filePath = tempDir2.resolve("test.txt");
        Files.writeString(filePath, "content", StandardCharsets.UTF_8);
        FileUpload file = mockFile("test.txt", 100L, filePath);
        doAnswer(inv -> {
            Document d = inv.getArgument(0);
            d.id = "uuid";
            return null;
        }).when(documentRepository).persist(any(Document.class));

        resource.uploadDocument(file, "Title", "Author", "Category", "", "1.0");

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).persist(docCaptor.capture());
        assertThat(docCaptor.getValue().tags).isEmpty();
    }

    @Test
    void getDocument_found_returnsDetailResponse() {
        setUpResource();
        Document doc = new Document();
        doc.id = "doc-123";
        doc.title = "Test Doc";
        doc.author = "Test Author";
        doc.category = "Test Cat";
        doc.tags = new String[]{"tag1", "tag2"};
        doc.version = "2.0";
        doc.originalFileName = "orig.txt";
        doc.fileType = "txt";
        doc.content = "extracted content";
        doc.status = DocumentStatus.INDEXED;
        doc.createdAt = java.time.Instant.now();
        doc.updatedAt = java.time.Instant.now();
        doc.errorMessage = null;

        when(documentRepository.findById("doc-123")).thenReturn(doc);

        DocumentDetailResponse response = resource.getDocument("doc-123");

        assertThat(response.documentId()).isEqualTo("doc-123");
        assertThat(response.status()).isEqualTo("INDEXED");
        assertThat(response.metadata().title()).isEqualTo("Test Doc");
        assertThat(response.metadata().author()).isEqualTo("Test Author");
        assertThat(response.metadata().category()).isEqualTo("Test Cat");
        assertThat(response.metadata().tags()).containsExactly("tag1", "tag2");
        assertThat(response.metadata().version()).isEqualTo("2.0");
        assertThat(response.content()).isEqualTo("extracted content");
        assertThat(response.originalFileName()).isEqualTo("orig.txt");
        assertThat(response.fileType()).isEqualTo("txt");
        assertThat(response.errorMessage()).isNull();
    }

    @Test
    void getDocument_notFound_throwsDocumentNotFound() {
        setUpResource();
        when(documentRepository.findById("missing")).thenReturn(null);

        assertThatThrownBy(() -> resource.getDocument("missing"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "DOCUMENT_NOT_FOUND")
                .hasMessageContaining("missing");
    }
}