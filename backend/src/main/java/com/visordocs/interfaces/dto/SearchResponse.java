package com.visordocs.interfaces.dto;

import java.util.List;

/**
 * Response DTO for search results (GET /api/documents/search).
 */
public record SearchResponse(
        List<SearchResultItem> items,
        int page,
        int pageSize,
        long total
) {

    public record SearchResultItem(
            String documentId,
            String title,
            SearchMetadata metadata,
            List<String> highlight
    ) {
    }

    public record SearchMetadata(
            String author,
            String category,
            String[] tags,
            String version
    ) {
    }
}
