package com.visordocs.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * Core domain entity representing a technical document in the system.
 * <p>
 * Persisted in PostgreSQL via Hibernate Panache. Text content is extracted
 * asynchronously after upload and then indexed in Elasticsearch.
 */
@Entity
@Table(name = "documents")
public class Document extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public String id;

    @Column(nullable = false)
    public String title;

    @Column(nullable = false)
    public String author;

    @Column(nullable = false)
    public String category;

    @Column(name = "tags", columnDefinition = "text")
    public String tags; // comma-separated for simplicity with PostgreSQL

    @Column(nullable = false)
    public String version;

    @Column(name = "original_file_name")
    public String originalFileName;

    @Column(name = "file_type")
    public String fileType; // "txt" | "pdf" | "md"

    @Column(name = "file_size")
    public Long fileSize;

    @Column(columnDefinition = "text")
    public String content; // extracted text content

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    public DocumentStatus status = DocumentStatus.PROCESSING;

    @Column(name = "error_message")
    public String errorMessage;

    @Column(name = "created_at")
    public Instant createdAt;

    @Column(name = "updated_at")
    public Instant updatedAt;

    @PrePersist
    void onPrePersist() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onPreUpdate() {
        updatedAt = Instant.now();
    }
}
