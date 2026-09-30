package com.visordocs.infrastructure.search;

import com.visordocs.domain.AppException;
import com.visordocs.interfaces.dto.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Elasticsearch service for indexing and searching documents.
 * Uses low-level REST client for search and index operations to avoid
 * media_type_header_exception with Elasticsearch 8.x.
 */
@ApplicationScoped
public class ElasticsearchService {

    private static final Logger LOG = Logger.getLogger(ElasticsearchService.class);

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "app.elasticsearch.index", defaultValue = "documents")
    String indexName;

    @ConfigProperty(name = "quarkus.elasticsearch.hosts")
    String hostsConfig;

    @ConfigProperty(name = "app.elasticsearch.index.shards", defaultValue = "1")
    int indexShards;

    @ConfigProperty(name = "app.elasticsearch.index.replicas", defaultValue = "0")
    int indexReplicas;

    @ConfigProperty(name = "app.elasticsearch.connection.max-connections", defaultValue = "20")
    int maxConnections;

    @ConfigProperty(name = "app.elasticsearch.connection.timeout-ms", defaultValue = "5000")
    int connectionTimeoutMs;

    @ConfigProperty(name = "app.elasticsearch.connection.socket-timeout-ms", defaultValue = "30000")
    int socketTimeoutMs;

    @ConfigProperty(name = "app.elasticsearch.connection.connection-request-timeout-ms", defaultValue = "5000")
    int connectionRequestTimeoutMs;

    @ConfigProperty(name = "app.elasticsearch.retry.max-attempts", defaultValue = "3")
    int maxRetryAttempts;

    @ConfigProperty(name = "app.elasticsearch.retry.delay-ms", defaultValue = "1000")
    int retryDelayMs;

    private RestClient lowLevelClient;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * Creates the Elasticsearch index with proper mapping on application startup
     * if it does not already exist. Retries a few times in case ES is not yet
     * ready.
     * Does NOT fail the application startup if ES is unavailable; index will be
     * created on first document processing.
     */
    void onStartup(@Observes StartupEvent event) {
        initLowLevelClient();
        scheduleIndexCreationWithRetry();
    }

    private void initLowLevelClient() {
        List<HttpHost> hosts = parseHosts(hostsConfig);
        RestClientBuilder builder = RestClient.builder(hosts.toArray(new HttpHost[0]))
                .setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                        .setConnectTimeout(connectionTimeoutMs)
                        .setSocketTimeout(socketTimeoutMs)
                        .setConnectionRequestTimeout(connectionRequestTimeoutMs))
                .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                        .setMaxConnTotal(maxConnections)
                        .setMaxConnPerRoute(maxConnections));
        lowLevelClient = builder.build();
        LOG.info("Low-level Elasticsearch REST client initialized for search and index operations");
    }

    private void scheduleIndexCreationWithRetry() {
        CompletableFuture.runAsync(() -> {
            try {
                ensureIndexExistsWithRetry();
            } catch (Exception e) {
                LOG.warnf("Could not create Elasticsearch index '%s' after %d attempts; " +
                        "index will be created on first document processing: %s", indexName, maxRetryAttempts,
                        e.getMessage());
            }
        }, executor);
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

    void ensureIndexExistsWithRetry() throws IOException {
        int attempts = 0;
        IOException lastException = null;

        while (attempts < maxRetryAttempts) {
            attempts++;
            try {
                if (indexExists()) {
                    LOG.infof("Index '%s' already exists", indexName);
                    return;
                } else {
                    LOG.infof("Attempt %d: Creating Elasticsearch index: %s", attempts, indexName);
                    createIndex();
                    LOG.infof("Index '%s' created successfully", indexName);
                    return;
                }
            } catch (IOException e) {
                lastException = e;
                LOG.warnf(e, "Elasticsearch not yet available on attempt %d/%d", attempts, maxRetryAttempts);
                if (attempts < maxRetryAttempts) {
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

        if (lastException != null) {
            throw lastException;
        }
    }

    private boolean indexExists() throws IOException {
        Request request = new Request("HEAD", "/" + indexName);
        Response response = lowLevelClient.performRequest(request);
        return response.getStatusLine().getStatusCode() == 200;
    }

    void createIndex() throws IOException {
        ObjectNode mapping = objectMapper.createObjectNode();
        ObjectNode properties = mapping.putObject("properties");

        properties.putObject("documentId").put("type", "keyword");
        properties.putObject("title").put("type", "text").put("analyzer", "standard");
        properties.putObject("author").put("type", "text").put("analyzer", "standard");
        properties.putObject("category").put("type", "keyword");
        properties.putObject("tags").put("type", "keyword");
        properties.putObject("version").put("type", "keyword");
        properties.putObject("content").put("type", "text").put("analyzer", "standard");

        ObjectNode indexSettings = objectMapper.createObjectNode();
        indexSettings.put("number_of_shards", indexShards);
        indexSettings.put("number_of_replicas", indexReplicas);

        ObjectNode body = objectMapper.createObjectNode();
        body.set("mappings", mapping);
        body.set("settings", indexSettings);

        Request request = new Request("PUT", "/" + indexName);
        request.setJsonEntity(objectMapper.writeValueAsString(body));
        Response response = lowLevelClient.performRequest(request);
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode >= 400) {
            throw new IOException("Failed to create index: " + response.getStatusLine().getReasonPhrase());
        }
    }

    /**
     * Indexes a document in Elasticsearch with retry logic.
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
        executeWithRetry(() -> {
            try {
                ObjectNode doc = objectMapper.createObjectNode();
                doc.put("documentId", documentId);
                doc.put("title", title);
                doc.put("author", author);
                doc.put("category", category);
                doc.set("tags", objectMapper.valueToTree(tags != null ? Arrays.asList(tags) : List.of()));
                doc.put("version", version);
                doc.put("content", content);

                Request request = new Request("PUT", "/" + indexName + "/_doc/" + documentId);
                request.setJsonEntity(objectMapper.writeValueAsString(doc));

                Response response = lowLevelClient.performRequest(request);

                if (response.getStatusLine().getStatusCode() >= 400) {
                    throw new IOException("Failed to index document: " + response.getStatusLine().getReasonPhrase());
                }

                LOG.infof("Document '%s' indexed successfully", documentId);
                return null;
            } catch (IOException e) {
                throw AppException.indexingError(e.getMessage(), e);
            }
        });
    }

    /**
     * Performs a Full-Text Search with highlighting across title, author, content,
     * and tags using the low-level REST client to avoid
     * media_type_header_exception.
     *
     * @param queryText search query
     * @param page      zero-based page number
     * @param pageSize  results per page
     * @return search response with paginated results and highlights
     */
    public SearchResponse search(String queryText, int page, int pageSize) {
        return executeWithRetry(() -> {
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

                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode >= 400) {
                    throw AppException.searchError(
                            "Search failed with status " + statusCode + ": "
                                    + response.getStatusLine().getReasonPhrase(),
                            statusCode);
                }

                // Parse response
                JsonNode responseJson;
                try {
                    responseJson = objectMapper.readTree(response.getEntity().getContent());
                } catch (com.fasterxml.jackson.core.JsonParseException
                        | com.fasterxml.jackson.databind.JsonMappingException e) {
                    throw AppException.searchError("Failed to parse ES response: " + e.getMessage(), 400, e);
                } catch (IOException e) {
                    throw AppException.searchError(e.getMessage(), e);
                }

                return parseSearchResponse(responseJson, page, pageSize);
            } catch (IOException e) {
                throw AppException.searchError(e.getMessage(), e);
            }
        });
    }

    <T> T executeWithRetry(Supplier<T> operation) {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < maxRetryAttempts) {
            attempts++;
            try {
                return operation.get();
            } catch (AppException e) {
                // Don't retry on client errors (4xx), only server errors (5xx) and IO
                if (e.getHttpStatus() >= 500 || e.getHttpStatus() == 0) {
                    lastException = e;
                    LOG.warnf(e, "ES operation failed on attempt %d/%d, retrying...", attempts, maxRetryAttempts);
                    if (attempts < maxRetryAttempts) {
                        sleepUninterruptibly(retryDelayMs);
                    }
                } else {
                    throw e;
                }
            } catch (Exception e) {
                // Don't retry on JSON parsing errors - these are not transient
                if (e instanceof com.fasterxml.jackson.core.JsonParseException
                        || e instanceof com.fasterxml.jackson.databind.JsonMappingException) {
                    throw AppException.searchError("Failed to parse ES response: " + e.getMessage(), e);
                }
                lastException = e;
                LOG.warnf(e, "ES operation failed on attempt %d/%d, retrying...", attempts, maxRetryAttempts);
                if (attempts < maxRetryAttempts) {
                    sleepUninterruptibly(retryDelayMs);
                }
            }
        }

        if (lastException instanceof AppException ae) {
            throw ae;
        }
        if (lastException == null) {
            throw AppException.searchError(
                    "Operation was not attempted because maxRetryAttempts is not positive", 500);
        }
        throw AppException.searchError(
                "Operation failed after " + maxRetryAttempts + " attempts: " + lastException.getMessage(),
                lastException);
    }

    private void sleepUninterruptibly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    SearchResponse parseSearchResponse(JsonNode responseJson, int page, int pageSize) {
        List<SearchResponse.SearchResultItem> items = new ArrayList<>();

        JsonNode hits = responseJson.path("hits");
        long total;
        JsonNode totalNode = hits.path("total");
        if (totalNode.isObject()) {
            total = totalNode.path("value").asLong(0);
        } else {
            total = totalNode.asLong(0);
        }

        for (JsonNode hit : hits.path("hits")) {
            JsonNode source = hit.path("_source");
            if (source.isMissingNode())
                continue;

            // Collect highlight fragments
            List<String> highlights = new ArrayList<>();
            JsonNode highlight = hit.path("highlight");
            if (!highlight.isMissingNode()) {
                highlight.properties().forEach(entry -> {
                    entry.getValue().forEach(fragment -> highlights.add(fragment.asText()));
                });
            }

            String docId = source.path("documentId").asText();
            String title = source.path("title").asText();
            String author = source.path("author").asText();
            String category = source.path("category").asText();
            String version = source.path("version").asText();

            JsonNode tagsNode = source.path("tags");
            String[] tagsArr;
            if (tagsNode.isArray()) {
                List<String> tagsList = new ArrayList<>();
                tagsNode.forEach(tagNode -> {
                    if (!tagNode.isNull()) {
                        tagsList.add(tagNode.asText());
                    }
                });
                tagsArr = tagsList.toArray(String[]::new);
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
                .map(host -> host.trim())
                .filter(s -> !s.isEmpty())
                .map(this::parseHost)
                .collect(java.util.stream.Collectors.toList());
    }

    private HttpHost parseHost(String host) {
        String h = host.trim();

        // Handle full URI format: scheme://host:port/path
        if (h.contains("://")) {
            try {
                java.net.URI uri = new java.net.URI(h);
                String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
                String hostname = uri.getHost();
                int port = uri.getPort() != -1 ? uri.getPort() : 9200;
                return new HttpHost(hostname, port, scheme);
            } catch (java.net.URISyntaxException e) {
                // Fall through to simple parsing
            }
        }

        // Handle host:port or host format (no scheme)
        String scheme = "http";
        if (h.startsWith("https://")) {
            scheme = "https";
            h = h.substring(8);
        } else if (h.startsWith("http://")) {
            h = h.substring(7);
        }
        String[] parts = h.split(":");
        String hostname = parts[0];
        int port = 9200;
        if (parts.length > 1) {
            try {
                port = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ex) {
                // keep default
            }
        }
        return new HttpHost(hostname, port, scheme);
    }

    @PreDestroy
    void close() {
        try {
            if (lowLevelClient != null) {
                lowLevelClient.close();
                LOG.info("Low-level Elasticsearch REST client closed");
            }
        } catch (IOException e) {
            LOG.errorf(e, "Error closing Elasticsearch low-level client");
        }
        executor.shutdown();
        scheduler.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}