package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmbeddingScheduler}.
 *
 * <p>Verifies that the scheduler correctly converts persisted {@link NewsArticleEntity}
 * objects to Spring AI {@link Document} objects and calls {@link VectorStore#add}.
 * Both the happy path and the error-recovery path (API key missing) are covered.
 * No Spring context is loaded — all dependencies are injected as Mockito mocks.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-13
 * @updated 2026-07-30
 */
@ExtendWith(MockitoExtension.class)
class EmbeddingSchedulerTest {

    @Mock
    private NewsArticleRepository repository;

    @Mock
    private VectorStore vectorStore;

    private EmbeddingScheduler scheduler;

    // --- shared fixtures ---

    private static NewsArticleEntity entity(String id, String title, String body) {
        NewsArticleEntity e = new NewsArticleEntity();
        e.setArticleId(id);
        e.setTitle(title);
        e.setBodyText(body);
        e.setTopic("AI diagnostics");
        e.setUrl("https://example.com/" + id);
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
        scheduler = new EmbeddingScheduler(repository, provider);
    }

    // -------------------------------------------------------------------------
    // embedArticles() — happy path
    // -------------------------------------------------------------------------

    @Test
    void embedArticles_callsVectorStoreAddWithOneDocumentPerEntity() {
        NewsArticleEntity e1 = entity("a-001", "AI improves diagnosis", "Body text one.");
        NewsArticleEntity e2 = entity("a-002", "ML in drug discovery", "Body text two.");
        when(repository.findByEmbeddedFalse()).thenReturn(List.of(e1, e2));

        scheduler.embedArticles();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());

        List<Document> docs = captor.getValue();
        assertThat(docs).hasSize(2);
    }

    @Test
    void embedArticles_documentIdMatchesArticleId() {
        NewsArticleEntity e = entity("a-001", "Title", "Body.");
        when(repository.findByEmbeddedFalse()).thenReturn(List.of(e));

        scheduler.embedArticles();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());

        Document doc = captor.getValue().get(0);
        String expectedId = java.util.UUID.nameUUIDFromBytes(
                "a-001".getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        assertThat(doc.getId()).isEqualTo(expectedId);
    }

    @Test
    void embedArticles_documentContentCombinesTitleAndBody() {
        NewsArticleEntity e = entity("a-001", "My Title", "My body.");
        when(repository.findByEmbeddedFalse()).thenReturn(List.of(e));

        scheduler.embedArticles();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());

        Document doc = captor.getValue().get(0);
        assertThat(doc.getText()).contains("My Title").contains("My body.");
    }

    @Test
    void embedArticles_documentMetadataContainsArticleIdTopicAndUrl() {
        NewsArticleEntity e = entity("a-001", "Title", "Body.");
        when(repository.findByEmbeddedFalse()).thenReturn(List.of(e));

        scheduler.embedArticles();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());

        Document doc = captor.getValue().get(0);
        assertThat(doc.getMetadata()).containsKey("articleId");
        assertThat(doc.getMetadata()).containsKey("topic");
        assertThat(doc.getMetadata()).containsKey("url");
        assertThat(doc.getMetadata().get("articleId")).isEqualTo("a-001");
    }

    @Test
    void embedArticles_marksArticlesAsEmbeddedAfterSuccess() {
        NewsArticleEntity e1 = entity("a-001", "Title One", "Body one.");
        NewsArticleEntity e2 = entity("a-002", "Title Two", "Body two.");
        when(repository.findByEmbeddedFalse()).thenReturn(List.of(e1, e2));

        scheduler.embedArticles();

        assertThat(e1.isEmbedded()).isTrue();
        assertThat(e2.isEmbedded()).isTrue();
        verify(repository).saveAll(anyList());
    }

    // -------------------------------------------------------------------------
    // embedArticles() — empty repository
    // -------------------------------------------------------------------------

    @Test
    void embedArticles_emptyRepository_doesNotCallVectorStore() {
        when(repository.findByEmbeddedFalse()).thenReturn(List.of());

        scheduler.embedArticles();

        verify(vectorStore, never()).add(anyList());
    }

    // -------------------------------------------------------------------------
    // embedArticles() — exception handling
    // -------------------------------------------------------------------------

    @Test
    void embedArticles_vectorStoreThrows_doesNotPropagateException() {
        NewsArticleEntity e = entity("a-001", "Title", "Body.");
        when(repository.findByEmbeddedFalse()).thenReturn(List.of(e));
        doThrow(new RuntimeException("invalid API key")).when(vectorStore).add(anyList());

        // Must not throw — the scheduler swallows embedding failures so the thread stays alive
        scheduler.embedArticles();
    }
}
