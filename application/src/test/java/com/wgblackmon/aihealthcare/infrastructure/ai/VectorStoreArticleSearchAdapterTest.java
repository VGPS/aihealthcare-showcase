package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VectorStoreArticleSearchAdapter}.
 *
 * <p>Verifies the adapter's contract: a natural-language query is forwarded to
 * the {@link VectorStore}, resulting documents are resolved to full
 * {@link NewsArticle} domain records via {@link NewsArticleRepository}, and
 * edge cases (empty result, missing metadata, DB lookup miss, search exception)
 * are handled gracefully.  No Spring context is loaded — all dependencies are
 * Mockito mocks.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-13
 * @updated 2026-04-13
 */
@ExtendWith(MockitoExtension.class)
class VectorStoreArticleSearchAdapterTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private NewsArticleRepository repository;

    private VectorStoreArticleSearchAdapter adapter;

    // --- helpers ---

    private static Document docWithArticleId(String articleId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("articleId", articleId);
        metadata.put("topic", "AI diagnostics");
        metadata.put("url", "https://example.com/" + articleId);
        return new Document(articleId, "Some article content.", metadata);
    }

    private static NewsArticleEntity entityFor(String articleId) {
        NewsArticleEntity e = new NewsArticleEntity();
        e.setArticleId(articleId);
        e.setTitle("Test Article " + articleId);
        e.setUrl("https://example.com/" + articleId);
        e.setBodyText("Body text.");
        e.setTopic("AI diagnostics");
        e.setSourceWeight(0.5);
        return e;
    }

    @BeforeEach
    void setUp() {
        ObjectProvider<VectorStore> provider = new ObjectProvider<>() {
            @Override public VectorStore getObject()          { return vectorStore; }
            @Override public VectorStore getIfAvailable()     { return vectorStore; }
            @Override public VectorStore getIfUnique()        { return vectorStore; }
        };
        adapter = new VectorStoreArticleSearchAdapter(provider, repository);
    }

    // -------------------------------------------------------------------------
    // findSimilar() — happy path
    // -------------------------------------------------------------------------

    @Test
    void findSimilar_returnsMappedDomainArticles() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(docWithArticleId("a-001")));
        when(repository.findById("a-001"))
                .thenReturn(Optional.of(entityFor("a-001")));

        List<NewsArticle> result = adapter.findSimilar("AI diagnosis tools", 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).articleId()).isEqualTo("a-001");
    }

    @Test
    void findSimilar_forwardsQueryAndTopKToVectorStore() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        adapter.findSimilar("robot surgery", 10);

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());

        SearchRequest req = captor.getValue();
        assertThat(req.getQuery()).isEqualTo("robot surgery");
        assertThat(req.getTopK()).isEqualTo(10);
    }

    @Test
    void findSimilar_multipleDocuments_returnsAllResolved() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(docWithArticleId("a-001"), docWithArticleId("a-002")));
        when(repository.findById("a-001")).thenReturn(Optional.of(entityFor("a-001")));
        when(repository.findById("a-002")).thenReturn(Optional.of(entityFor("a-002")));

        List<NewsArticle> result = adapter.findSimilar("clinical AI", 5);

        assertThat(result).hasSize(2);
    }

    // -------------------------------------------------------------------------
    // findSimilar() — edge cases
    // -------------------------------------------------------------------------

    @Test
    void findSimilar_emptyVectorStoreResult_returnsEmptyList() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        List<NewsArticle> result = adapter.findSimilar("any query", 5);

        assertThat(result).isEmpty();
    }

    @Test
    void findSimilar_documentMissingArticleIdMetadata_isSkipped() {
        Document docWithoutId = new Document("orphan-doc", "content", Map.of());
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(docWithoutId));

        List<NewsArticle> result = adapter.findSimilar("query", 5);

        assertThat(result).isEmpty();
    }

    @Test
    void findSimilar_articleIdNotInDatabase_isSkipped() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(docWithArticleId("a-ghost")));
        when(repository.findById("a-ghost")).thenReturn(Optional.empty());

        List<NewsArticle> result = adapter.findSimilar("query", 5);

        assertThat(result).isEmpty();
    }

    @Test
    void findSimilar_vectorStoreThrows_returnsEmptyList() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("API key not configured"));

        List<NewsArticle> result = adapter.findSimilar("query", 5);

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // findSimilar() — domain record mapping
    // -------------------------------------------------------------------------

    @Test
    void findSimilar_domainArticleCarriesCorrectTitle() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(docWithArticleId("a-001")));
        when(repository.findById("a-001")).thenReturn(Optional.of(entityFor("a-001")));

        List<NewsArticle> result = adapter.findSimilar("query", 5);

        assertThat(result.get(0).title()).isEqualTo("Test Article a-001");
    }
}
