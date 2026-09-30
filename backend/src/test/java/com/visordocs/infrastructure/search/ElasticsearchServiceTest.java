package com.visordocs.infrastructure.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.InlineGet;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import com.visordocs.domain.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElasticsearchServiceTest {

    @Mock
    private ElasticsearchClient client;

    @Mock
    private ElasticsearchIndicesClient indicesClient;

    private ElasticsearchService service;

    @BeforeEach
    void setUp() {
        service = new ElasticsearchService();
        service.client = client;
        service.indexName = "documents";
    }

    @Nested
    @DisplayName("ensureIndexExists tests")
    class EnsureIndexExistsTests {

        @Test
        @DisplayName("When index does not exist, creates the index with standard mappings")
        void ensureIndexExists_whenIndexDoesNotExist_createsIndex() throws Exception {
            when(client.indices()).thenReturn(indicesClient);
            when(indicesClient.exists(any(ExistsRequest.class))).thenReturn(new BooleanResponse(false));

            service.ensureIndexExists();

            verify(indicesClient).exists(any(ExistsRequest.class));
            verify(indicesClient).create(any(CreateIndexRequest.class));
        }

        @Test
        @DisplayName("When index already exists, does not attempt to create it")
        void ensureIndexExists_whenIndexExists_doesNotCreate() throws Exception {
            when(client.indices()).thenReturn(indicesClient);
            when(indicesClient.exists(any(ExistsRequest.class))).thenReturn(new BooleanResponse(true));

            service.ensureIndexExists();

            verify(indicesClient).exists(any(ExistsRequest.class));
            verify(indicesClient, never()).create(any(CreateIndexRequest.class));
        }

        @Test
        @DisplayName("When IOException occurs, wraps into AppException.indexingError")
        void ensureIndexExists_whenIOException_throwsAppException() throws Exception {
            when(client.indices()).thenReturn(indicesClient);
            when(indicesClient.exists(any(ExistsRequest.class))).thenThrow(new IOException("Connection refused"));

            assertThatThrownBy(() -> service.ensureIndexExists())
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Failed to ensure Elasticsearch index");
        }
    }

    @Nested
    @DisplayName("indexDocument tests")
    class IndexDocumentTests {

        @Test
        @DisplayName("Indexes document successfully with all fields mapped")
        @SuppressWarnings("unchecked")
        void indexDocument_success() throws Exception {
            IndexResponse indexResponse = mock(IndexResponse.class);
            when(client.index(any(IndexRequest.class))).thenReturn(indexResponse);

            service.indexDocument("doc-1", "Clean Architecture", "Robert Martin", "Architecture",
                    new String[]{"books", "design"}, "1.0", "Some technical content");

            ArgumentCaptor<IndexRequest<Map<String, Object>>> captor = ArgumentCaptor.forClass(IndexRequest.class);
            verify(client).index(captor.capture());

            IndexRequest<Map<String, Object>> request = captor.getValue();
            assertThat(request.index()).isEqualTo("documents");
            assertThat(request.id()).isEqualTo("doc-1");

            Map<String, Object> documentMap = request.document();
            assertThat(documentMap).isNotNull();
            assertThat(documentMap.get("documentId")).isEqualTo("doc-1");
            assertThat(documentMap.get("title")).isEqualTo("Clean Architecture");
            assertThat(documentMap.get("author")).isEqualTo("Robert Martin");
            assertThat(documentMap.get("category")).isEqualTo("Architecture");
            assertThat(documentMap.get("tags")).isEqualTo(List.of("books", "design"));
            assertThat(documentMap.get("version")).isEqualTo("1.0");
            assertThat(documentMap.get("content")).isEqualTo("Some technical content");
        }

        @Test
        @DisplayName("Handles null tags gracefully by setting empty list")
        @SuppressWarnings("unchecked")
        void indexDocument_nullTags_handledSafely() throws Exception {
            IndexResponse indexResponse = mock(IndexResponse.class);
            when(client.index(any(IndexRequest.class))).thenReturn(indexResponse);

            service.indexDocument("doc-2", "Guide", "Author", "General", null, "0.1", "Content");

            ArgumentCaptor<IndexRequest<Map<String, Object>>> captor = ArgumentCaptor.forClass(IndexRequest.class);
            verify(client).index(captor.capture());

            Map<String, Object> doc = captor.getValue().document();
            assertThat(doc.get("tags")).isEqualTo(Collections.emptyList());
        }

        @Test
        @DisplayName("When index fails with IOException, throws AppException.indexingError")
        void indexDocument_ioException_throwsAppException() throws Exception {
            when(client.index(any(IndexRequest.class))).thenThrow(new IOException("ES unavailable"));

            assertThatThrownBy(() -> service.indexDocument("doc-1", "T", "A", "C", new String[]{}, "1", "body"))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("ES unavailable");
        }
    }

    @Nested
    @DisplayName("search tests")
    class SearchTests {

        @Test
        @DisplayName("Search executes successfully and maps hits, metadata and total count")
        @SuppressWarnings("unchecked")
        void search_success_withHits() throws Exception {
            SearchResponse<Map> searchResponse = mock(SearchResponse.class);
            HitsMetadata<Map> hitsMetadata = mock(HitsMetadata.class);
            TotalHits totalHits = mock(TotalHits.class);
            Hit<Map> hit = mock(Hit.class);

            when(client.search(any(Function.class), any(Class.class))).thenReturn(searchResponse);
            when(searchResponse.hits()).thenReturn(hitsMetadata);
            when(hitsMetadata.total()).thenReturn(totalHits);
            when(totalHits.value()).thenReturn(1L);

            Map<String, Object> sourceMap = Map.of(
                    "documentId", "doc-100",
                    "title", "Quarkus Guide",
                    "author", "Red Hat",
                    "category", "Framework",
                    "version", "3.0",
                    "tags", List.of("java", "quarkus")
            );
            when(hit.source()).thenReturn(sourceMap);
            when(hit.highlight()).thenReturn(Map.of("content", List.of("<mark>Quarkus</mark> is reactive")));
            when(hitsMetadata.hits()).thenReturn(List.of(hit));

            com.visordocs.interfaces.dto.SearchResponse result = service.search("quarkus", 0, 10);

            assertThat(result.total()).isEqualTo(1L);
            assertThat(result.page()).isZero();
            assertThat(result.pageSize()).isEqualTo(10);
            assertThat(result.items()).hasSize(1);

            var item = result.items().get(0);
            assertThat(item.documentId()).isEqualTo("doc-100");
            assertThat(item.title()).isEqualTo("Quarkus Guide");
            assertThat(item.metadata().author()).isEqualTo("Red Hat");
            assertThat(item.metadata().category()).isEqualTo("Framework");
            assertThat(item.metadata().version()).isEqualTo("3.0");
            assertThat(item.metadata().tags()).containsExactly("java", "quarkus");
            assertThat(item.highlight()).containsExactly("<mark>Quarkus</mark> is reactive");
        }

        @Test
        @DisplayName("Search returns zero total and empty items when no hits match")
        @SuppressWarnings("unchecked")
        void search_emptyResults() throws Exception {
            SearchResponse<Map> searchResponse = mock(SearchResponse.class);
            HitsMetadata<Map> hitsMetadata = mock(HitsMetadata.class);
            TotalHits totalHits = mock(TotalHits.class);

            when(client.search(any(Function.class), any(Class.class))).thenReturn(searchResponse);
            when(searchResponse.hits()).thenReturn(hitsMetadata);
            when(hitsMetadata.total()).thenReturn(totalHits);
            when(totalHits.value()).thenReturn(0L);
            when(hitsMetadata.hits()).thenReturn(Collections.emptyList());

            var result = service.search("nonexistent", 0, 20);

            assertThat(result.total()).isZero();
            assertThat(result.items()).isEmpty();
        }

        @Test
        @DisplayName("Search throws AppException.searchError when IOException occurs")
        @SuppressWarnings("unchecked")
        void search_ioException_throwsAppException() throws Exception {
            when(client.search(any(Function.class), any(Class.class))).thenThrow(new IOException("Search timed out"));

            assertThatThrownBy(() -> service.search("any", 0, 10))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Search timed out");
        }
    }
}
