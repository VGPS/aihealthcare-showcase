package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.ArticleSearchCriteria;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleSearchQueryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ArticleSearchService}.
 *
 * <p>Verifies empty-criteria short-circuit, delegation to the outbound port,
 * and null-criteria handling.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-01
 * @updated 2026-06-01
 */
@ExtendWith(MockitoExtension.class)
class ArticleSearchServiceTest {

    @Mock
    private ArticleSearchQueryPort queryPort;

    private ArticleSearchService service;

    @BeforeEach
    void setUp() {
        service = new ArticleSearchService(queryPort);
    }

    private NewsArticle sampleArticle(String title) {
        return new NewsArticle(
                "id-1", title, URI.create("https://example.com"),
                "body", "PubMed AI Healthcare", "Smith",
                1L, "PubMed", "ACADEMIC", 0.9, Instant.now());
    }

    @Test
    void search_nullCriteria_returnsEmptyList() {
        List<NewsArticle> result = service.search(null);

        assertThat(result).isEmpty();
        verify(queryPort, never()).findByCriteria(any());
    }

    @Test
    void search_emptyCriteria_returnsEmptyListWithoutQueryingPort() {
        ArticleSearchCriteria criteria = new ArticleSearchCriteria(
                null, null, null, null, null, null, null);

        List<NewsArticle> result = service.search(criteria);

        assertThat(result).isEmpty();
        verify(queryPort, never()).findByCriteria(any());
    }

    @Test
    void search_withTitleCriteria_delegatesToPort() {
        ArticleSearchCriteria criteria = new ArticleSearchCriteria(
                "radiology", null, null, null, null, null, null);
        NewsArticle article = sampleArticle("AI in Radiology");
        when(queryPort.findByCriteria(criteria)).thenReturn(List.of(article));

        List<NewsArticle> result = service.search(criteria);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("AI in Radiology");
        verify(queryPort).findByCriteria(criteria);
    }

    @Test
    void search_withMultipleCriteria_delegatesToPort() {
        ArticleSearchCriteria criteria = new ArticleSearchCriteria(
                "radiology", "PubMed", null, null, null, null, null);
        when(queryPort.findByCriteria(criteria)).thenReturn(List.of());

        List<NewsArticle> result = service.search(criteria);

        assertThat(result).isEmpty();
        verify(queryPort).findByCriteria(criteria);
    }

    @Test
    void search_portReturnsMultipleResults_allReturned() {
        ArticleSearchCriteria criteria = new ArticleSearchCriteria(
                null, "PubMed", null, null, null, null, null);
        NewsArticle a1 = sampleArticle("Article One");
        NewsArticle a2 = sampleArticle("Article Two");
        when(queryPort.findByCriteria(criteria)).thenReturn(List.of(a1, a2));

        List<NewsArticle> result = service.search(criteria);

        assertThat(result).hasSize(2);
    }
}
