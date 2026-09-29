package com.visordocs.infrastructure.persistence;

import com.visordocs.domain.Document;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Panache repository for Document entity.
 * Provides standard CRUD + custom query methods.
 */
@ApplicationScoped
public class DocumentRepository implements PanacheRepositoryBase<Document, String> {

    // Inherits: persist, findById, listAll, delete, count, etc.
    // Custom queries can be added here as needed.
}
