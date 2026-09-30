package com.visordocs.infrastructure.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.visordocs.domain.AppException;
import com.visordocs.interfaces.dto.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Elasticsearch service for indexing and searching documents.
 * Uses high-level client for indexing, low-level REST client for search and index operations
 * to avoid media_type_header_exception with Elasticsearch 8.x.
 */
@ApplicationScoped
public class ElasticsearchService {

    private static final Logger LOG = Logger.getLogger(ElasticsearchService.class);

    @Inject
    ElasticsearchClient client;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "app.elasticsearch.index", defaultValue = "documents")
    String indexName;

    @ConfigProperty(name = "quarkus.elasticsearch.hosts")
    String hostsConfig;

    // Low-level REST client for search and index operations (avoids media_type_header_exception with ES 8.x)
    private RestClient lowLevelClient;

    /**
     * Creates the Elasticsearch index with proper mapping on application startup
     * if it does not already exist. Retries a few times in case ES is not yet ready.
     * Does NOT fail the application startup if ES is unavailable; index will be
     * created on first document processing.
     */
    void onStartup(@Observes StartupEvent event) {
        // Initialize low-level client for search and index operations
        initLowLevelClient();

        int maxRetries = 3;
        int retryDelayMs = 2000;
        boolean created = false;

        for (int attempt = 1; attempt <= maxRetries && !created; attempt++) {
            try {
                if (indexExists()) {
                    LOG.infof("Index '%s' already exists", indexName);
                    created = true;
                } else {
                    LOG.infof("Attempt %d: Creating Elasticsearch index: %s", attempt, indexName);
                    createIndex();
                    LOG.infof("Index '%s' created successfully", indexName);
                    created = true;
                }
            } catch (IOException e) {
                LOG.warnf(e, "Elasticsearch not yet available on attempt %d/%d", attempt, maxRetries);
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        LOG.errorf(ie, "Interrupted while waiting to retry ES index creation");
                        return;
                    }
                }
            }
        }

        if (!created) {
            LOG.warnf("Could not create Elasticsearch index '%s' after %d attempts; " +
                    "index will be created on first document processing", indexName, maxRetries);
        }
    }

    private void initLowLevelClient() {
        List<HttpHost> hosts = parseHosts(hostsConfig);
        RestClientBuilder builder = RestClient.builder(hosts.toArray(new HttpHost[0]));
        lowLevelClient = builder.build();
        LOG.info("Low-level Elasticsearch REST client initialized for search and index operations");
    }

    /**
     * Ensures the Elasticsearch index exists, creating it if necessary.
     * Meant to be called before the first document indexing operation.
     * Thread-safe; idempotent – safe to call multiple times.
     */
    public void ensureIndexExists() {
        try {
            if (!indexExists()) {
                LOG.infof("Ensuring Elasticsearch index creation: %s", indexName);
                createIndex();
                LOG.infof("Index '%s' created successfully", indexName);
            } else {
                LOG.infof("Index '%s' already exists", indexName);
            }
        } catch (IOException e) {
            throw AppException.indexingError(
                    "Failed to ensure Elasticsearch index: " + e.getMessage(), e);
        }
    }

    private boolean indexExists() throws IOException {
        Request request = new Request("HEAD", "/" + indexName);
        Response response = lowLevelClient.performRequest(request);
        return response.getStatusLine().getStatusCode() == 200;
    }

    private void createIndex() throws IOException {
        ObjectNode mapping = objectMapper.createObjectNode();
        ObjectNode properties = mapping.putObject("properties");
        
        properties.putObject("documentId").put("type", "keyword");
        properties.putObject("title").put("type", "text").put("analyzer", "standard");
        properties.putObject("author").put("type", "text").put("analyzer", "standard");
        properties.putObject("category").put("type", "keyword");
        properties.putObject("tags").put("type", "keyword");
        properties.putObject("version").put("type", "keyword");
        properties.putObject("content").put("type", "text").put("analyzer", "standard");

        ObjectNode body = objectMapper.createObjectNode();
        body.set("mappings", mapping);

        Request request = new Request("PUT", "/" + indexName);
        request.setJsonEntity(objectMapper.writeValueAsString(body));
        lowLevelClient.performRequest(request);
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
            ObjectNode doc = objectMapper.createObjectNode();
            doc.put("documentId", documentId);
            doc.put("title", title);
            doc.put("author", author);
            doc.put("category", category);
            doc.put("tags", objectMapper.valueToTree(tags != null ? Arrays.asList(tags) : List.of()));
            doc.put("version", version);
            doc.put("content", content);

            Request request = new Request("PUT", "/" + indexName + "/_doc/" + documentId);
            request.setJsonEntity(objectMapper.writeValueAsString(doc));

            Response response = lowLevelClient.performRequest(request);
            
            if (response.getStatusLine().getStatusCode() >= 400) {
                throw new IOException("Failed to index document: " + response.getStatusLine().getReasonPhrase());
            }

            LOG.infof("Document '%s' indexed successfully", documentId);
        } catch (IOException e) {
            throw AppException.indexingError(e.getMessage(), e);
        }
    }

    /**
     * Performs a Full-Text Search with highlighting across title, author, content,
     * and tags using the low-level REST client to avoid media_type_header_exception.
     *
     * @param queryText search query
     * @param page      zero-based page number
     * @param pageSize  results per page
     * @return search response with paginated results and highlights
     */
    public SearchResponse search(String queryText, int page, int pageSize) {
        try {
            // Build the search request body
            ObjectNode searchBody = objectMapper.createObjectNode();
            
            ObjectNode multiMatch = objectMapper.createObjectNode();
            multiMatch.put("query", queryText);
            multiMatch.set("fields", objectMapper.valueToTree(List.of("title^3", "author^2", "content", "tags")));
            
            ObjectNode query = objectMapper.createObjectNode();
            query.set("multi_match", multiMatch);
            searchBody.set("query", query);

            ObjectNode highlight = objectMapper.createObjectNode();
            highlight.set("pre_tags", objectMapper.valueToTree(List.of("<mark>")));
            highlight.set("post_tags", objectMapper.valueToTree(List.of("</mark>")));
            
            ObjectNode fields = objectMapper.createObjectNode();
            fields.set("title", objectMapper.createObjectNode().put("number_of_fragments", 1));
            fields.set("content", objectMapper.createObjectNode()
                    .put("fragment_size", 150)
                    .put("number_of_fragments", 3));
            highlight.set("fields", fields);
            searchBody.set("highlight", highlight);

            searchBody.put("from", page * pageSize);
            searchBody.put("size", pageSize);

            // Execute search using low-level REST client
            Request request = new Request("POST", "/" + indexName + "/_search");
            request.setJsonEntity(objectMapper.writeValueAsString(searchBody));
            
            Response response = lowLevelClient.performRequest(request);
            
            // Parse response
            JsonNode responseJson = objectMapper.readTree(response.getEntity().getContent());
            
            return parseSearchResponse(responseJson, page, pageSize);
        } catch (IOException e) {
            throw AppException.searchError(e.getMessage(), e);
        }
    }

    private SearchResponse parseSearchResponse(JsonNode responseJson, int page, int pageSize) {
        List<SearchResponse.SearchResultItem> items = new ArrayList<>();
        
        JsonNode hits = responseJson.path("hits");
        long total = hits.path("total").path("value").asLong(0);
        
        for (JsonNode hit : hits.path("hits")) {
            JsonNode source = hit.path("_source");
            if (source.isMissingNode()) continue;

            // Collect highlight fragments
            List<String> highlights = new ArrayList<>();
            JsonNode highlight = hit.path("highlight");
            if (!highlight.isMissingNode()) {
                highlight.fields().forEachRemaining(entry -> {
                    entry.getValue().forEach(fragment -> highlights.add(fragment.asText()));
                });
            }

            String docId = source.path("documentId").asText();
            String title = source.path("title").asText();
            String author = source.path("author").asText();
            String category = source.path("category").asText();
            String version = source.path("version").asText();

            Object tagsObj = source.path("tags");
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

        return new SearchResponse(items, page, pageSize, total);
    }

    private List<HttpHost> parseHosts(String hostsConfig) {
        return Arrays.stream(hostsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::parseHost)
                .collect(Collectors.toList());
    }

    private HttpHost parseHost(String host) {
        if (host.startsWith("http://")) {
            host = host.substring(7);
        } else if (host.startsWith("https://")) {
            host = host.substring(8);
        }
        String[] parts = host.split(":");
        String hostname = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9200;
        String scheme = host.startsWith("https://") || host.contains("https") ? "https" : "http";
        return new HttpHost(hostname, port, scheme);
    }
}