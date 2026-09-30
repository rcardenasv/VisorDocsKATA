package com.visordocs.interfaces;

import com.visordocs.domain.AppException;
import com.visordocs.domain.Document;
import com.visordocs.domain.DocumentStatus;
import com.visordocs.infrastructure.persistence.DocumentRepository;
import com.visordocs.interfaces.dto.DocumentDetailResponse;
import com.visordocs.interfaces.dto.UploadResponse;
import io.vertx.core.eventbus.EventBus;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@jakarta.ws.rs.Path("/api/documents")
@Produces(MediaType.APPLICATION_JSON)
public class DocumentResource {

    @Inject
    DocumentRepository documentRepository;

    @Inject
    EventBus eventBus;

    @ConfigProperty(name = "app.upload.dir")
    String uploadDir;

    @ConfigProperty(name = "app.upload.max-size-mb")
    long maxSizeMb;

    @ConfigProperty(name = "app.upload.allowed-extensions")
    List<String> allowedExtensions;

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public Response uploadDocument(
            @RestForm("file") FileUpload file,
            @RestForm("title") String title,
            @RestForm("author") String author,
            @RestForm("category") String category,
            @RestForm("tags") String tags,
            @RestForm("version") String version) {

        if (file == null) throw AppException.validationError("File is required");
        if (title == null || title.isBlank()) throw AppException.validationError("Title is required");
        if (author == null || author.isBlank()) throw AppException.validationError("Author is required");
        if (category == null || category.isBlank()) throw AppException.validationError("Category is required");
        if (version == null || version.isBlank()) throw AppException.validationError("Version is required");

        String originalName = file.fileName();
        String extension = getExtension(originalName);

        if (!allowedExtensions.contains(extension.toLowerCase())) {
            throw AppException.unsupportedFileType(extension);
        }

        long sizeBytes = file.size();
        if (sizeBytes > maxSizeMb * 1024 * 1024) {
            throw AppException.fileSizeExceeded(maxSizeMb);
        }

        Document doc = new Document();
        doc.title = title;
        doc.author = author;
        doc.category = category;
        doc.tags = (tags != null && !tags.isBlank()) ? tags.split(",") : new String[0];
        doc.version = version;
        doc.originalFileName = originalName;
        doc.fileType = extension;
        doc.fileSize = sizeBytes;
        doc.status = DocumentStatus.PROCESSING;

        documentRepository.persist(doc);
        documentRepository.flush();

        // Save file to disk
        try {
            Path uploadPath = Path.of(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            Path destination = uploadPath.resolve(doc.id);
            Files.copy(file.filePath(), destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw AppException.internalError("Failed to save file", e);
        }

        // Trigger async processing
        eventBus.publish("document.process", doc.id);

        return Response.accepted(new UploadResponse(doc.id, doc.status.name())).build();
    }

    @GET
    @jakarta.ws.rs.Path("/{id}")
    public DocumentDetailResponse getDocument(@PathParam("id") String id) {
        Document doc = documentRepository.findById(id);
        if (doc == null) {
            throw AppException.documentNotFound(id);
        }

        return new DocumentDetailResponse(
                doc.id,
                doc.status.name(),
                new DocumentDetailResponse.DocumentMetadata(
                        doc.title, doc.author, doc.category, doc.tags, doc.version
                ),
                doc.content,
                doc.originalFileName,
                doc.fileType,
                doc.createdAt,
                doc.updatedAt,
                doc.errorMessage
        );
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "";
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }
}
