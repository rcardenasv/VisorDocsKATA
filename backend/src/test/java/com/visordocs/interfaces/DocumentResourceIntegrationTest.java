package com.visordocs.interfaces;

import com.visordocs.domain.Document;
import com.visordocs.domain.DocumentStatus;
import com.visordocs.infrastructure.persistence.DocumentRepository;
import com.visordocs.infrastructure.search.ElasticsearchService;
import com.visordocs.interfaces.dto.DocumentDetailResponse;
import com.visordocs.interfaces.dto.UploadResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class DocumentResourceIntegrationTest {

    @InjectMock
    DocumentRepository documentRepository;

    @InjectMock
    ElasticsearchService elasticsearchService;

    @Test
    void uploadDocument_happyPath_returns202(@TempDir Path tempDir) throws Exception {
        Path filePath = tempDir.resolve("source.txt");
        Files.writeString(filePath, "file content", StandardCharsets.UTF_8);

        doAnswer(inv -> {
            Document d = inv.getArgument(0);
            d.id = "generated-uuid-123";
            return null;
        }).when(documentRepository).persist(any(Document.class));

        UploadResponse response = given()
                .multiPart("file", filePath.toFile(), "text/plain")
                .multiPart("title", "My Title")
                .multiPart("author", "My Author")
                .multiPart("category", "My Category")
                .multiPart("tags", "tag1,tag2")
                .multiPart("version", "1.0")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .post("/api/documents")
                .then()
                .statusCode(202)
                .extract().as(UploadResponse.class);

        assertThat(response.documentId()).isEqualTo("generated-uuid-123");
        assertThat(response.status()).isEqualTo("PROCESSING");

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).persist(docCaptor.capture());
        Document saved = docCaptor.getValue();
        assertThat(saved.title).isEqualTo("My Title");
        assertThat(saved.author).isEqualTo("My Author");
        assertThat(saved.category).isEqualTo("My Category");
        assertThat(saved.tags).containsExactly("tag1", "tag2");
        assertThat(saved.version).isEqualTo("1.0");
        assertThat(saved.status).isEqualTo(DocumentStatus.PROCESSING);
    }

    @Test
    void getDocument_found_returnsDetail() {
        Document doc = new Document();
        doc.id = "doc-123";
        doc.title = "Test Doc";
        doc.author = "Test Author";
        doc.category = "Test Cat";
        doc.tags = new String[] { "tag1" };
        doc.version = "2.0";
        doc.originalFileName = "orig.txt";
        doc.fileType = "txt";
        doc.content = "extracted content";
        doc.status = DocumentStatus.INDEXED;
        doc.createdAt = java.time.Instant.now();
        doc.updatedAt = java.time.Instant.now();

        when(documentRepository.findById("doc-123")).thenReturn(doc);

        DocumentDetailResponse response = given()
                .get("/api/documents/doc-123")
                .then()
                .statusCode(200)
                .extract().as(DocumentDetailResponse.class);

        assertThat(response.documentId()).isEqualTo("doc-123");
        assertThat(response.status()).isEqualTo("INDEXED");
        assertThat(response.metadata().title()).isEqualTo("Test Doc");
        assertThat(response.metadata().author()).isEqualTo("Test Author");
    }

    @Test
    void getDocument_notFound_returns404WithErrorResponse() {
        when(documentRepository.findById("missing")).thenReturn(null);

        given()
                .get("/api/documents/missing")
                .then()
                .statusCode(404)
                .body("error.code", org.hamcrest.CoreMatchers.equalTo("DOCUMENT_NOT_FOUND"))
                .body("error.message", org.hamcrest.CoreMatchers.containsString("missing"))
                .body("error.correlationId", org.hamcrest.CoreMatchers.notNullValue());
    }

    @Test
    void uploadDocument_missingFile_returns400WithErrorResponse() {
        given()
                .multiPart("title", "Title")
                .multiPart("author", "Author")
                .multiPart("category", "Category")
                .multiPart("version", "1.0")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .post("/api/documents")
                .then()
                .statusCode(400)
                .body("error.code", org.hamcrest.CoreMatchers.equalTo("VALIDATION_ERROR"))
                .body("error.message", org.hamcrest.CoreMatchers.containsString("File is required"))
                .body("error.correlationId", org.hamcrest.CoreMatchers.notNullValue());
    }

    @Test
    void uploadDocument_unsupportedExtension_returns400WithErrorResponse(@TempDir Path tempDir) throws Exception {
        Path filePath = tempDir.resolve("test.xyz");
        Files.writeString(filePath, "content", StandardCharsets.UTF_8);

        given()
                .multiPart("file", filePath.toFile(), "application/octet-stream")
                .multiPart("title", "Title")
                .multiPart("author", "Author")
                .multiPart("category", "Category")
                .multiPart("version", "1.0")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .post("/api/documents")
                .then()
                .statusCode(400)
                .body("error.code", org.hamcrest.CoreMatchers.equalTo("UNSUPPORTED_FILE_TYPE"))
                .body("error.correlationId", org.hamcrest.CoreMatchers.notNullValue());
    }

    @Test
    void getDocumentContent_found_returnsFileContent() throws Exception {
        Document doc = new Document();
        doc.id = "doc-456";
        doc.title = "PDF Doc";
        doc.author = "PDF Author";
        doc.category = "PDF Cat";
        doc.tags = new String[] { "pdf" };
        doc.version = "1.0";
        doc.originalFileName = "test.pdf";
        doc.fileType = "pdf";
        doc.content = "extracted text";
        doc.status = DocumentStatus.INDEXED;

        Path uploadDir = Path.of("test-uploads");
        Files.createDirectories(uploadDir);
        Path filePath = uploadDir.resolve(doc.id);
        Files.writeString(filePath, "%PDF-1.4 fake pdf content", StandardCharsets.UTF_8);

        when(documentRepository.findById("doc-456")).thenReturn(doc);

        given()
                .get("/api/documents/doc-456/content")
                .then()
                .statusCode(200)
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", org.hamcrest.CoreMatchers.containsString("inline"))
                .header("Content-Disposition", org.hamcrest.CoreMatchers.containsString("test.pdf"))
                .body(org.hamcrest.CoreMatchers.containsString("%PDF-1.4 fake pdf content"));

        Files.deleteIfExists(filePath);
    }

    @Test
    void getDocumentContent_txt_returnsTextPlain() throws Exception {
        Document doc = new Document();
        doc.id = "doc-txt";
        doc.originalFileName = "readme.txt";
        doc.fileType = "txt";

        Path uploadDir = Path.of("test-uploads");
        Files.createDirectories(uploadDir);
        Path filePath = uploadDir.resolve(doc.id);
        Files.writeString(filePath, "Plain text content", StandardCharsets.UTF_8);

        when(documentRepository.findById("doc-txt")).thenReturn(doc);

        given()
                .get("/api/documents/doc-txt/content")
                .then()
                .statusCode(200)
                .header("Content-Type", "text/plain")
                .body(org.hamcrest.CoreMatchers.equalTo("Plain text content"));

        Files.deleteIfExists(filePath);
    }

    @Test
    void getDocumentContent_md_returnsTextMarkdown() throws Exception {
        Document doc = new Document();
        doc.id = "doc-md";
        doc.originalFileName = "guide.md";
        doc.fileType = "md";

        Path uploadDir = Path.of("test-uploads");
        Files.createDirectories(uploadDir);
        Path filePath = uploadDir.resolve(doc.id);
        Files.writeString(filePath, "# Header\nContent", StandardCharsets.UTF_8);

        when(documentRepository.findById("doc-md")).thenReturn(doc);

        given()
                .get("/api/documents/doc-md/content")
                .then()
                .statusCode(200)
                .header("Content-Type", "text/markdown")
                .body(org.hamcrest.CoreMatchers.equalTo("# Header\nContent"));

        Files.deleteIfExists(filePath);
    }

    @Test
    void getDocumentContent_notFound_returns404() {
        when(documentRepository.findById("missing")).thenReturn(null);

        given()
                .accept(MediaType.APPLICATION_JSON)
                .get("/api/documents/missing/content")
                .then()
                .statusCode(404)
                .contentType(MediaType.APPLICATION_JSON)
                .body("error.code", org.hamcrest.CoreMatchers.equalTo("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void getDocumentContent_fileMissingOnDisk_returns500() {
        Document doc = new Document();
        doc.id = "doc-no-file";
        doc.fileType = "pdf";

        when(documentRepository.findById("doc-no-file")).thenReturn(doc);

        given()
                .accept(MediaType.APPLICATION_JSON)
                .get("/api/documents/doc-no-file/content")
                .then()
                .statusCode(500)
                .contentType(MediaType.APPLICATION_JSON)
                .body("error.code", org.hamcrest.CoreMatchers.equalTo("INTERNAL_SERVER_ERROR"))
                .body("error.message", org.hamcrest.CoreMatchers.containsString("File not found on disk"));
    }
}