package com.visordocs.infrastructure.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.visordocs.domain.AppException;
import com.visordocs.interfaces.dto.SearchResponse;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.*;

/**
 * Elasticsearch service for indexing and searching documents.
 * Manages index lifecycle and provides Full-Text Search with highlighting.
 */
@ApplicationScoped
public class ElasticsearchService {

    private static final Logger LOG = Logger.getLogger(ElasticsearchService.class);

    @Inject
    ElasticsearchClient client;

    @ConfigProperty(name = "app.elasticsearch.index", defaultValue = "documents")
    String indexName;

    /**
     * Creates the Elasticsearch index with proper mapping on application startup
     * if it does not already exist.
     */
    void onStartup(@Observes StartupEvent event) {
        try {
            boolean exists = client.indices().exists(
                    ExistsRequest.of(e -> e.index(indexName))).value();

            if (!exists) {
                LOG.infof("Creating Elasticsearch index: %s", indexName);
                client.indices().create(CreateIndexRequest.of(c -> c
                        .index(indexName)
                        .mappings(m -> m
                                .properties("documentId", p -> p.keyword(k -> k))
                                .properties("title", p -> p.text(t -> t.analyzer("standard")))
                                .properties("author", p -> p.text(t -> t.analyzer("standard")))
                                .properties("category", p -> p.keyword(k -> k))
                                .properties("tags", p -> p.keyword(k -> k))
                                .properties("version", p -> p.keyword(k -> k))
                                .properties("content", p -> p.text(t -> t.analyzer("standard"))))));
                LOG.infof("Index '%s' created successfully", indexName);
            } else {
                LOG.infof("Index '%s' already exists", indexName);
            }
        } catch (IOException e) {
            LOG.errorf(e, "Failed to initialize Elasticsearch index '%s'", indexName);
        }
    }

    /**
     * Indexes a document in Elasticsearch.
     *
     * @param documentId unique identifier
     * @param title      document title
     * @param author     document author
     * @param category   document category
     * @param tags       array of tags
     * @param version    document version
     * @param content    extracted text content
     */
    public void indexDocument(String documentId, String title, String author,
            String category, String[] tags, String version, String content) {
        try {
            Map<String, Object> doc = new HashMap<>();
            doc.put("documentId", documentId);
            doc.put("title", title);
            doc.put("author", author);
            doc.put("category", category);
            doc.put("tags", tags != null ? Arrays.asList(tags) : List.of());
            doc.put("version", version);
            doc.put("content", content);

            client.index(IndexRequest.of(i -> i
                    .index(indexName)
                    .id(documentId)
                    .document(doc)));

            LOG.infof("Document '%s' indexed successfully", documentId);
        } catch (IOException e) {
            throw AppException.indexingError(e.getMessage(), e);
        }
    }

    /**
     * Performs a Full-Text Search with highlighting across title, author, content,
     * and tags.
     *
     * @param queryText search query
     * @param page      zero-based page number
     * @param pageSize  results per page
     * @return search response with paginated results and highlights
     */
    public SearchResponse search(String queryText, int page, int pageSize) {
        try {
            var response = client.search(s -> s
                    .index(indexName)
                    .query(q -> q
                            .multiMatch(mm -> mm
                                    .query(queryText)
                                    .fields("title^3", "author^2", "content", "tags")))
                    .highlight(h -> h
                            .preTags("<mark>")
                            .postTags("</mark>")
                            .fields(List.of(
                                    co.elastic.clients.util.NamedValue.of("title",
                                            HighlightField.of(hf -> hf.numberOfFragments(1))),
                                    co.elastic.clients.util.NamedValue.of("content",
                                            HighlightField.of(hf -> hf.fragmentSize(150).numberOfFragments(3))))))
                    .from(page * pageSize)
                    .size(pageSize),
                    Map.class);

            List<SearchResponse.SearchResultItem> items = new ArrayList<>();

            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source == null)
                    continue;

                // Collect highlight fragments
                List<String> highlights = new ArrayList<>();
                if (hit.highlight() != null) {
                    hit.highlight().values().forEach(highlights::addAll);
                }

                String docId = (String) source.get("documentId");
                String title = (String) source.get("title");
                String author = (String) source.get("author");
                String category = (String) source.get("category");
                String version = (String) source.get("version");

                Object tagsObj = source.get("tags");
                String[] tagsArr;
                if (tagsObj instanceof List<?> tagsList) {
                    tagsArr = tagsList.stream()
                            .map(Object::toString)
                            .toArray(String[]::new);
                } else {
                    tagsArr = new String[0];
                }

                items.add(new SearchResponse.SearchResultItem(
                        docId,
                        title,
                        new SearchResponse.SearchMetadata(author, category, tagsArr, version),
                        highlights));
            }

            long total = response.hits().total() != null ? response.hits().total().value() : 0;

            return new SearchResponse(items, page, pageSize, total);
        } catch (IOException e) {
            throw AppException.searchError(e.getMessage(), e);
        }
    }
}
