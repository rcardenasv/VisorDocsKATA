package com.visordocs.interfaces;

import com.visordocs.infrastructure.search.ElasticsearchService;
import com.visordocs.interfaces.dto.SearchResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@QuarkusTest
class SearchResourceIntegrationTest {

    @InjectMock
    ElasticsearchService elasticsearchService;

    @Test
    void search_withNullQuery_returnsEmptyPage() {
        given()
                .queryParam("q", "")
                .get("/api/documents/search")
                .then()
                .statusCode(200)
                .body("items", org.hamcrest.Matchers.empty())
                .body("page", org.hamcrest.CoreMatchers.equalTo(0))
                .body("pageSize", org.hamcrest.CoreMatchers.equalTo(20))
                .body("total", org.hamcrest.CoreMatchers.equalTo(0));
    }

    @Test
    void search_withBlankQuery_returnsEmptyPage() {
        given()
                .queryParam("q", "   ")
                .get("/api/documents/search")
                .then()
                .statusCode(200)
                .body("items", org.hamcrest.Matchers.empty())
                .body("total", org.hamcrest.CoreMatchers.equalTo(0));
    }

    @Test
    void search_withValidQuery_delegatesToService() {
        SearchResponse expected = new SearchResponse(
                List.of(new SearchResponse.SearchResultItem(
                        "doc-1",
                        "Title",
                        new SearchResponse.SearchMetadata("author", "cat", new String[]{"tag1"}, "1.0"),
                        List.of("highlight")
                )),
                0, 20, 100
        );
        when(elasticsearchService.search("query", 0, 20)).thenReturn(expected);

        SearchResponse response = given()
                .queryParam("q", "query")
                .get("/api/documents/search")
                .then()
                .statusCode(200)
                .extract().as(SearchResponse.class);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).documentId()).isEqualTo("doc-1");
        assertThat(response.total()).isEqualTo(100);
    }

    @Test
    void search_withPagination_usesParameters() {
        SearchResponse expected = new SearchResponse(List.of(), 2, 5, 50);
        when(elasticsearchService.search("test", 2, 5)).thenReturn(expected);

        given()
                .queryParam("q", "test")
                .queryParam("page", 2)
                .queryParam("pageSize", 5)
                .get("/api/documents/search")
                .then()
                .statusCode(200)
                .body("page", org.hamcrest.CoreMatchers.equalTo(2))
                .body("pageSize", org.hamcrest.CoreMatchers.equalTo(5));
    }
}