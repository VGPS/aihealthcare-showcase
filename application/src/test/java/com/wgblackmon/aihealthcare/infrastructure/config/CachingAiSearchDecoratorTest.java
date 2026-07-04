package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.model.AiSearchResult;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.service.AiSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CachingAiSearchDecorator}.
 *
 * <p>Verifies that the decorator correctly delegates to the underlying
 * {@link AiSearchService}. Cache hit/miss behaviour is tested at the
 * integration level via Spring's cache infrastructure; these tests
 * validate delegation and result passthrough.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-03
 * @updated 2026-07-03
 */
class CachingAiSearchDecoratorTest {

    private AiSearchService delegate;
    private CachingAiSearchDecorator decorator;

    @BeforeEach
    void setUp() {
        delegate = mock(AiSearchService.class);
        decorator = new CachingAiSearchDecorator(delegate);
    }

    private AiSearchResult sampleResult(String query) {
        NewsArticle article = new NewsArticle(
                "id-1", "Test Article", URI.create("https://example.com/1"),
                "Body text", "AI Healthcare", "Author",
                1L, "Source", "ACADEMIC", 0.9, Instant.now());
        return new AiSearchResult("s1", query, List.of(article),
                Collections.emptyList(), Instant.now());
    }

    @Test
    void search_delegatesToUnderlyingService() {
        AiSearchResult expected = sampleResult("AI diagnostics");
        when(delegate.search("AI diagnostics", 20)).thenReturn(expected);

        AiSearchResult result = decorator.search("AI diagnostics", 20);

        assertThat(result).isEqualTo(expected);
        verify(delegate).search("AI diagnostics", 20);
    }

    @Test
    void searchWithModels_delegatesToUnderlyingService() {
        List<String> models = List.of("Claude", "GPT");
        AiSearchResult expected = sampleResult("radiology AI");
        when(delegate.search("radiology AI", 10, models)).thenReturn(expected);

        AiSearchResult result = decorator.search("radiology AI", 10, models);

        assertThat(result).isEqualTo(expected);
        verify(delegate).search("radiology AI", 10, models);
    }

    @Test
    void search_withoutCache_delegatesEveryCall() {
        AiSearchResult expected = sampleResult("query");
        when(delegate.search(anyString(), anyInt())).thenReturn(expected);

        decorator.search("query", 20);
        decorator.search("query", 20);

        // Without Spring cache proxy, delegate is called each time
        verify(delegate, times(2)).search(eq("query"), eq(20));
    }

    @Test
    void search_differentQueries_delegatesSeparately() {
        AiSearchResult r1 = sampleResult("query1");
        AiSearchResult r2 = sampleResult("query2");
        when(delegate.search("query1", 20)).thenReturn(r1);
        when(delegate.search("query2", 20)).thenReturn(r2);

        AiSearchResult result1 = decorator.search("query1", 20);
        AiSearchResult result2 = decorator.search("query2", 20);

        assertThat(result1.query()).isEqualTo("query1");
        assertThat(result2.query()).isEqualTo("query2");
        verify(delegate).search("query1", 20);
        verify(delegate).search("query2", 20);
    }
}
