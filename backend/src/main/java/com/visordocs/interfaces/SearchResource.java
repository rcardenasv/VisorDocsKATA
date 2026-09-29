package com.visordocs.interfaces;

import com.visordocs.infrastructure.search.ElasticsearchService;
import com.visordocs.interfaces.dto.SearchResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/documents/search")
@Produces(MediaType.APPLICATION_JSON)
public class SearchResource {

    @Inject
    ElasticsearchService elasticsearchService;

    @GET
    public SearchResponse search(
            @QueryParam("q") String query,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("pageSize") @DefaultValue("20") int pageSize) {
        
        if (query == null || query.isBlank()) {
            return new SearchResponse(java.util.Collections.emptyList(), page, pageSize, 0);
        }
        
        return elasticsearchService.search(query, page, pageSize);
    }
}
