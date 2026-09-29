package com.visordocs.interfaces;

import com.visordocs.infrastructure.search.ElasticsearchService;
import com.visordocs.interfaces.dto.SearchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchResourceTest {

    @Mock
    ElasticsearchService elasticsearchService;

    private SearchResource searchResource;

    @Test
    void search_withNullQuery_returnsEmptyPageWithoutCallingService() {
        searchResource = new SearchResource();
        searchResource.elasticsearchService = elasticsearchService;

        SearchResponse response = searchResource.search(null, 0, 20);

        assertThat(response.items()).isEmpty();
        assertThat(response.page()).isZero();
        assertThat(response.pageSize()).isEqualTo(20);
        assertThat(response.total()).isZero();
        verifyNoInteractions(elasticsearchService);
    }

    @Test
    void search_withBlankQuery_returnsEmptyPageWithoutCallingService() {
        searchResource = new SearchResource();
        searchResource.elasticsearchService = elasticsearchService;

        SearchResponse response = searchResource.search("   ", 1, 10);

        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isZero();
        verifyNoInteractions(elasticsearchService);
    }

    @Test
    void search_withValidQuery_delegatesToElasticsearchService() {
        searchResource = new SearchResource();
        searchResource.elasticsearchService = elasticsearchService;

        SearchResponse expected = new SearchResponse(
                List.of(new SearchResponse.SearchResultItem(
                        "doc-1",
                        "Title",
                        new SearchResponse.SearchMetadata("author", "cat", new String[]{"tag1"}, "1.0"),
                        List.of("highlight")
                )),
                2, 5, 100
        );
        when(elasticsearchService.search("query text", 2, 5)).thenReturn(expected);

        SearchResponse response = searchResource.search("query text", 2, 5);

        assertThat(response).isSameAs(expected);
        verify(elasticsearchService).search("query text", 2, 5);
    }

    @Test
    void search_withDefaultParameters_usesDefaults() {
        searchResource = new SearchResource();
        searchResource.elasticsearchService = elasticsearchService;

        SearchResponse expected = new SearchResponse(List.of(), 0, 20, 0);
        when(elasticsearchService.search("test", 0, 20)).thenReturn(expected);

        SearchResponse response = searchResource.search("test", 0, 20);

        assertThat(response.page()).isZero();
        assertThat(response.pageSize()).isEqualTo(20);
        verify(elasticsearchService).search("test", 0, 20);
    }
}