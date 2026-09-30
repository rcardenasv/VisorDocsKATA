package com.visordocs.infrastructure.search;

import com.visordocs.domain.AppException;
import com.visordocs.interfaces.dto.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ElasticsearchServiceTest {

    @Mock
    RestClient lowLevelClient;

    private ElasticsearchService service;
    private final ObjectMapper realMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        service = new ElasticsearchService();

        java.lang.reflect.Field clientField = ElasticsearchService.class.getDeclaredField("lowLevelClient");
        clientField.setAccessible(true);
        clientField.set(service, lowLevelClient);

        java.lang.reflect.Field mapperField = ElasticsearchService.class.getDeclaredField("objectMapper");
        mapperField.setAccessible(true);
        mapperField.set(service, realMapper);

        java.lang.reflect.Field indexField = ElasticsearchService.class.getDeclaredField("indexName");
        indexField.setAccessible(true);
        indexField.set(service, "documents");

        java.lang.reflect.Field hostsField = ElasticsearchService.class.getDeclaredField("hostsConfig");
        hostsField.setAccessible(true);
        hostsField.set(service, "localhost:9200");

        // Set config properties for retry logic
        java.lang.reflect.Field maxRetryField = ElasticsearchService.class.getDeclaredField("maxRetryAttempts");
        maxRetryField.setAccessible(true);
        maxRetryField.set(service, 3);

        java.lang.reflect.Field retryDelayField = ElasticsearchService.class.getDeclaredField("retryDelayMs");
        retryDelayField.setAccessible(true);
        retryDelayField.set(service, 10); // Short delay for tests

        java.lang.reflect.Field shardsField = ElasticsearchService.class.getDeclaredField("indexShards");
        shardsField.setAccessible(true);
        shardsField.set(service, 1);

        java.lang.reflect.Field replicasField = ElasticsearchService.class.getDeclaredField("indexReplicas");
        replicasField.setAccessible(true);
        replicasField.set(service, 0);
    }

    @Test
    void parseHosts_parsesMultipleHosts() throws Exception {
        Method method = ElasticsearchService.class.getDeclaredMethod("parseHosts", String.class);
        method.setAccessible(true);

        List<org.apache.http.HttpHost> hosts = (List<org.apache.http.HttpHost>) method.invoke(service,
                "localhost:9200, localhost:9201");

        assertThat(hosts).hasSize(2);
        assertThat(hosts.get(0).getHostName()).isEqualTo("localhost");
        assertThat(hosts.get(0).getPort()).isEqualTo(9200);
        assertThat(hosts.get(1).getPort()).isEqualTo(9201);
    }

    @Test
    void parseHost_parsesHttpHost() throws Exception {
        Method method = ElasticsearchService.class.getDeclaredMethod("parseHost", String.class);
        method.setAccessible(true);

        org.apache.http.HttpHost host = (org.apache.http.HttpHost) method.invoke(service, "http://localhost:9200");

        assertThat(host.getHostName()).isEqualTo("localhost");
        assertThat(host.getPort()).isEqualTo(9200);
        assertThat(host.getSchemeName()).isEqualTo("http");
    }

    @Test
    void parseHost_parsesHttpsHost() throws Exception {
        Method method = ElasticsearchService.class.getDeclaredMethod("parseHost", String.class);
        method.setAccessible(true);

        org.apache.http.HttpHost host = (org.apache.http.HttpHost) method.invoke(service,
                "https://es.example.com:9243");

        assertThat(host.getHostName()).isEqualTo("es.example.com");
        assertThat(host.getPort()).isEqualTo(9243);
        assertThat(host.getSchemeName()).isEqualTo("https");
    }

    @Test
    void parseHost_handlesHostWithoutScheme() throws Exception {
        Method method = ElasticsearchService.class.getDeclaredMethod("parseHost", String.class);
        method.setAccessible(true);

        org.apache.http.HttpHost host = (org.apache.http.HttpHost) method.invoke(service, "es.example.com:9201");

        assertThat(host.getHostName()).isEqualTo("es.example.com");
        assertThat(host.getPort()).isEqualTo(9201);
        assertThat(host.getSchemeName()).isEqualTo("http");
    }

    @Test
    void indexExists_returnsTrueWhenIndexExists() throws Exception {
        Response response = mockResponse(200);
        lenient().when(lowLevelClient.performRequest(any())).thenReturn(response);

        Method method = ElasticsearchService.class.getDeclaredMethod("indexExists");
        method.setAccessible(true);

        boolean exists = (boolean) method.invoke(service);

        assertThat(exists).isTrue();
    }

    @Test
    void indexExists_returnsFalseWhenIndexNotFound() throws Exception {
        Response response = mockResponse(404);
        lenient().when(lowLevelClient.performRequest(any())).thenReturn(response);

        Method method = ElasticsearchService.class.getDeclaredMethod("indexExists");
        method.setAccessible(true);

        boolean exists = (boolean) method.invoke(service);

        assertThat(exists).isFalse();
    }

    @Test
    void createIndex_createsIndexWithCorrectMapping() throws Exception {
        Response response = mockResponse(200);
        lenient().when(lowLevelClient.performRequest(any())).thenReturn(response);

        Method method = ElasticsearchService.class.getDeclaredMethod("createIndex");
        method.setAccessible(true);
        method.invoke(service);

        verify(lowLevelClient).performRequest(any());
    }

    @Test
    void ensureIndexExists_createsIndexWhenNotExists() throws Exception {
        Response headResponse = mockResponse(404);
        Response putResponse = mockResponse(200);
        lenient().when(lowLevelClient.performRequest(any())).thenReturn(headResponse, putResponse);

        service.ensureIndexExists();

        verify(lowLevelClient, times(2)).performRequest(any());
    }

    @Test
    void ensureIndexExists_doesNothingWhenIndexExists() throws Exception {
        Response response = mockResponse(200);
        lenient().when(lowLevelClient.performRequest(any())).thenReturn(response);

        service.ensureIndexExists();

        verify(lowLevelClient).performRequest(any());
    }

    @Test
    void ensureIndexExists_throwsOnError() throws Exception {
        lenient().when(lowLevelClient.performRequest(any())).thenThrow(new IOException("Connection failed"));

        assertThatThrownBy(() -> service.ensureIndexExists())
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "INDEXING_ERROR");
    }

    @Test
    void indexDocument_indexesSuccessfully() throws Exception {
        Response response = mockResponse(201);
        lenient().when(lowLevelClient.performRequest(any())).thenReturn(response);

        service.indexDocument("doc-1", "Title", "Author", "Category", new String[] { "tag1" }, "1.0", "Content");

        verify(lowLevelClient).performRequest(any());
    }

    @Test
    void indexDocument_throwsOnHttpError() throws Exception {
        Response response = mockResponse(400, "Bad Request");
        lenient().when(lowLevelClient.performRequest(any())).thenReturn(response);

        assertThatThrownBy(() -> service.indexDocument("doc-1", "Title", "Author", "Category", new String[] { "tag1" },
                "1.0", "Content"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "INDEXING_ERROR");
    }

    @Test
    void indexDocument_handlesNullTags() throws Exception {
        Response response = mockResponse(201);
        lenient().when(lowLevelClient.performRequest(any())).thenReturn(response);

        service.indexDocument("doc-1", "Title", "Author", "Category", null, "1.0", "Content");

        verify(lowLevelClient).performRequest(any());
    }

    @Test
    void indexDocument_throwsOnIOException() throws Exception {
        lenient().when(lowLevelClient.performRequest(any())).thenThrow(new IOException("IO error"));
        assertThatThrownBy(() -> service.indexDocument("doc-1", "Title", "Author", "Category", new String[] { "tag1" },
                "1.0", "Content"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "INDEXING_ERROR");
    }

    @Test
    void search_returnsEmptyResultsForNoHits() throws IOException {
        String json = """
                {"hits":{"total":{"value":0},"hits":[]}}
                """;
        Response response = mockResponse(json);
        lenient().when(lowLevelClient.performRequest(any())).thenReturn(response);

        SearchResponse result = service.search("query", 0, 20);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
    }

    @Test
    void search_returnsResultsWithHighlights() throws IOException {
        String json = """
                {
                  "hits": {
                    "total": {"value": 1},
                    "hits": [{
                      "_source": {
                        "documentId": "doc-1",
                        "title": "Test Doc",
                        "author": "Author",
                        "category": "Cat",
                        "tags": ["tag1"],
                        "version": "1.0",
                        "content": "Some content"
                      },
                      "highlight": {
                        "title": ["Test <mark>Doc</mark>"],
                        "content": ["Some <mark>content</mark> here"]
                      }
                    }]
                  }
                }
                """;
        Response response = mockResponse(json);
        lenient().when(lowLevelClient.performRequest(any())).thenReturn(response);

        SearchResponse result = service.search("Doc", 0, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).documentId()).isEqualTo("doc-1");
        assertThat(result.items().get(0).highlight()).contains("Test <mark>Doc</mark>");
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void search_handlesPagination() throws IOException {
        String json = """
                {"hits":{"total":{"value":50},"hits":[]}}
                """;
        Response response = mockResponse(json);
        lenient().when(lowLevelClient.performRequest(any())).thenReturn(response);

        SearchResponse result = service.search("query", 2, 10);

        assertThat(result.page()).isEqualTo(2);
        assertThat(result.pageSize()).isEqualTo(10);
        assertThat(result.total()).isEqualTo(50);
    }

    @Test
    void search_throwsOnError() throws Exception {
        lenient().when(lowLevelClient.performRequest(any())).thenThrow(new IOException("ES down"));

        assertThatThrownBy(() -> service.search("query", 0, 20))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "SEARCH_ERROR");
    }

    @Test
    void parseSearchResponse_parsesTotalFromValue() throws Exception {
        ObjectNode root = realMapper.createObjectNode();
        ObjectNode hits = realMapper.createObjectNode();
        ObjectNode total = realMapper.createObjectNode();
        total.put("value", 42);
        hits.set("total", total);
        hits.putArray("hits");
        root.set("hits", hits);

        Method method = ElasticsearchService.class.getDeclaredMethod("parseSearchResponse", JsonNode.class, int.class,
                int.class);
        method.setAccessible(true);

        SearchResponse result = (SearchResponse) method.invoke(service, root, 0, 20);

        assertThat(result.total()).isEqualTo(42);
    }

    @Test
    void parseSearchResponse_collectsHighlights() throws Exception {
        ObjectNode root = realMapper.createObjectNode();
        ObjectNode hits = realMapper.createObjectNode();
        ObjectNode total = realMapper.createObjectNode();
        total.put("value", 1);
        hits.set("total", total);

        ObjectNode hit = realMapper.createObjectNode();
        ObjectNode source = realMapper.createObjectNode();
        source.put("documentId", "doc-1");
        source.put("title", "Title");
        source.put("author", "Author");
        source.put("category", "Cat");
        source.put("version", "1.0");
        source.putArray("tags").add("tag1");
        hit.set("_source", source);

        ObjectNode highlight = realMapper.createObjectNode();
        highlight.putArray("title").add("Test <mark>highlight</mark>");
        hit.set("highlight", highlight);

        hits.putArray("hits").add(hit);
        root.set("hits", hits);

        Method method = ElasticsearchService.class.getDeclaredMethod("parseSearchResponse", JsonNode.class, int.class,
                int.class);
        method.setAccessible(true);

        SearchResponse result = (SearchResponse) method.invoke(service, root, 0, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).highlight()).contains("Test <mark>highlight</mark>");
    }

    @Test
    void parseSearchResponse_handlesMissingHighlight() throws Exception {
        ObjectNode root = realMapper.createObjectNode();
        ObjectNode hits = realMapper.createObjectNode();
        ObjectNode total = realMapper.createObjectNode();
        total.put("value", 1);
        hits.set("total", total);

        ObjectNode hit = realMapper.createObjectNode();
        ObjectNode source = realMapper.createObjectNode();
        source.put("documentId", "doc-1");
        source.put("title", "Title");
        source.put("author", "Author");
        source.put("category", "Cat");
        source.put("version", "1.0");
        source.putArray("tags").add("tag1");
        hit.set("_source", source);
        // No highlight node

        hits.putArray("hits").add(hit);
        root.set("hits", hits);

        Method method = ElasticsearchService.class.getDeclaredMethod("parseSearchResponse", JsonNode.class, int.class,
                int.class);
        method.setAccessible(true);

        SearchResponse result = (SearchResponse) method.invoke(service, root, 0, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).highlight()).isEmpty();
    }

    @Test
    void parseSearchResponse_handlesMinimalSource() throws Exception {
        ObjectNode root = realMapper.createObjectNode();
        ObjectNode hits = realMapper.createObjectNode();
        ObjectNode total = realMapper.createObjectNode();
        total.put("value", 1);
        hits.set("total", total);

        ObjectNode hit = realMapper.createObjectNode();
        ObjectNode source = realMapper.createObjectNode();
        source.put("documentId", "doc-1");
        hit.set("_source", source);

        hits.putArray("hits").add(hit);
        root.set("hits", hits);

        Method method = ElasticsearchService.class.getDeclaredMethod("parseSearchResponse", JsonNode.class, int.class,
                int.class);
        method.setAccessible(true);

        SearchResponse result = (SearchResponse) method.invoke(service, root, 0, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).documentId()).isEqualTo("doc-1");
    }

    @Test
    void ensureIndexExists_throwsOnHeadError() throws Exception {
        lenient().when(lowLevelClient.performRequest(any())).thenThrow(new IOException("HEAD failed"));
        assertThatThrownBy(() -> service.ensureIndexExists())
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "INDEXING_ERROR");
    }

    @Test
    void search_throwsOnMalformedJson() throws Exception {
        Response response = mockResponse("not json");
        lenient().when(lowLevelClient.performRequest(any())).thenReturn(response);
        assertThatThrownBy(() -> service.search("query", 0, 20))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "SEARCH_ERROR");
    }

    @Test
    void parseSearchResponse_handlesTotalAsInteger() throws Exception {
        // ES 6.x format: total as integer
        ObjectNode root = realMapper.createObjectNode();
        ObjectNode hits = realMapper.createObjectNode();
        hits.put("total", 7); // integer
        hits.putArray("hits");
        root.set("hits", hits);
        Method method = ElasticsearchService.class.getDeclaredMethod("parseSearchResponse", JsonNode.class, int.class,
                int.class);
        method.setAccessible(true);
        SearchResponse result = (SearchResponse) method.invoke(service, root, 0, 20);
        assertThat(result.total()).isEqualTo(7);
    }

    @Test
    void parseSearchResponse_handlesMissingTotal() throws Exception {
        ObjectNode root = realMapper.createObjectNode();
        ObjectNode hits = realMapper.createObjectNode();
        // total missing
        hits.putArray("hits");
        root.set("hits", hits);
        Method method = ElasticsearchService.class.getDeclaredMethod("parseSearchResponse", JsonNode.class, int.class,
                int.class);
        method.setAccessible(true);
        SearchResponse result = (SearchResponse) method.invoke(service, root, 0, 20);
        assertThat(result.total()).isZero();
    }

    @Test
    void indexDocument_withEmptyTagsArray() throws Exception {
        Response response = mockResponse(201);
        lenient().when(lowLevelClient.performRequest(any())).thenReturn(response);
        service.indexDocument("doc-1", "Title", "Author", "Category", new String[] {}, "1.0", "Content");
        verify(lowLevelClient).performRequest(any());
    }

    @Test
    void ensureIndexExists_whenPutFails_throws() throws Exception {
        Response headResponse = mockResponse(404);
        Response putResponse = mockResponse(500, "Internal Server Error");
        lenient().when(lowLevelClient.performRequest(any())).thenReturn(headResponse, putResponse);
        assertThatThrownBy(() -> service.ensureIndexExists())
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "INDEXING_ERROR");
    }

    @Test
    void parseHost_uppercaseScheme() throws Exception {
        Method method = ElasticsearchService.class.getDeclaredMethod("parseHost", String.class);
        method.setAccessible(true);
        org.apache.http.HttpHost host = (org.apache.http.HttpHost) method.invoke(service, "HTTP://LOCALHOST:9200");
        assertThat(host.getSchemeName()).isEqualTo("http");
    }

    @Test
    void parseHost_noPort_defaultsTo9200() throws Exception {
        Method method = ElasticsearchService.class.getDeclaredMethod("parseHost", String.class);
        method.setAccessible(true);
        org.apache.http.HttpHost host = (org.apache.http.HttpHost) method.invoke(service, "localhost");
        assertThat(host.getPort()).isEqualTo(9200);
        assertThat(host.getHostName()).isEqualTo("localhost");
    }

    @Test
    void parseHost_handlesFullUriWithPath() throws Exception {
        Method method = ElasticsearchService.class.getDeclaredMethod("parseHost", String.class);
        method.setAccessible(true);
        org.apache.http.HttpHost host = (org.apache.http.HttpHost) method.invoke(service,
                "http://es.example.com:9200/some/path");
        assertThat(host.getHostName()).isEqualTo("es.example.com");
        assertThat(host.getPort()).isEqualTo(9200);
        assertThat(host.getSchemeName()).isEqualTo("http");
    }

    @Test
    void close_closesClientGracefully() throws Exception {
        // Test that close method doesn't throw
        Method method = ElasticsearchService.class.getDeclaredMethod("close");
        method.setAccessible(true);
        method.invoke(service);
        // Verify close was called on client
        verify(lowLevelClient).close();
    }

    @Test
    void search_retryOnServerError() throws Exception {
        // First two calls fail with 500, third succeeds
        Response failResponse = mockResponse(500, "Internal Server Error");
        Response successResponse = mockResponse(
                """
                        {"hits":{"total":{"value":1},"hits":[{"_source":{"documentId":"doc-1","title":"Test","author":"A","category":"C","tags":[],"version":"1.0","content":"content"},"highlight":{}}]}}
                        """);

        lenient().when(lowLevelClient.performRequest(any()))
                .thenReturn(failResponse, failResponse, successResponse);

        // This should retry and eventually succeed
        SearchResponse result = service.search("test", 0, 10);
        assertThat(result.total()).isEqualTo(1);
        verify(lowLevelClient, times(3)).performRequest(any());
    }

    @Test
    void indexDocument_retryOnServerError() throws Exception {
        Response failResponse = mockResponse(500, "Internal Server Error");
        Response successResponse = mockResponse(201);

        lenient().when(lowLevelClient.performRequest(any()))
                .thenReturn(failResponse, failResponse, successResponse);

        service.indexDocument("doc-1", "Title", "Author", "Category", new String[] { "tag1" }, "1.0", "Content");
        verify(lowLevelClient, times(3)).performRequest(any());
    }

    @Test
    void search_doesNotRetryOnClientError() throws Exception {
        Response clientErrorResponse = mockResponse(400, "Bad Request");
        lenient().when(lowLevelClient.performRequest(any())).thenReturn(clientErrorResponse);

        assertThatThrownBy(() -> service.search("query", 0, 20))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "SEARCH_ERROR");

        // Should only be called once, no retry on 4xx
        verify(lowLevelClient).performRequest(any());
    }

    private Response mockResponse(int statusCode) {
        return mockResponse(statusCode, "OK");
    }

    private Response mockResponse(int statusCode, String reasonPhrase) {
        Response response = org.mockito.Mockito.mock(Response.class);
        org.apache.http.StatusLine statusLine = org.mockito.Mockito.mock(org.apache.http.StatusLine.class);
        lenient().when(statusLine.getStatusCode()).thenReturn(statusCode);
        lenient().when(statusLine.getReasonPhrase()).thenReturn(reasonPhrase);
        lenient().when(response.getStatusLine()).thenReturn(statusLine);
        return response;
    }

    private Response mockResponse(String json) {
        Response response = org.mockito.Mockito.mock(Response.class);
        org.apache.http.StatusLine statusLine = org.mockito.Mockito.mock(org.apache.http.StatusLine.class);
        lenient().when(statusLine.getStatusCode()).thenReturn(200);
        lenient().when(response.getStatusLine()).thenReturn(statusLine);

        // Use a real BasicHttpEntity instead of mocking
        org.apache.http.entity.BasicHttpEntity entity = new org.apache.http.entity.BasicHttpEntity();
        entity.setContent(new ByteArrayInputStream(json.getBytes()));
        entity.setContentLength(json.getBytes().length);
        lenient().when(response.getEntity()).thenReturn(entity);

        return response;
    }
}