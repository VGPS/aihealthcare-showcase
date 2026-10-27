package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WikiEmbeddingScheduler}.
 *
 * <p>Verifies that the scheduler correctly converts {@link WikiPageEntity}
 * objects needing (re-)embedding into Spring AI {@link Document} objects
 * tagged with {@code type=wiki_page} metadata, and calls
 * {@link VectorStore#add}. Both the happy path and the error-recovery path
 * are covered. No Spring context is loaded — all dependencies are injected
 * as Mockito mocks, mirroring {@code EmbeddingSchedulerTest}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-26
 * @updated 2026-10-26
 */
@ExtendWith(MockitoExtension.class)
class WikiEmbeddingSchedulerTest {

    @Mock
    private WikiPageRepository repository;

    @Mock
    private VectorStore vectorStore;

    private WikiEmbeddingScheduler scheduler;

    private static WikiPageEntity page(String slug, String title, String markdown, int revision) {
        WikiPageEntity e = new WikiPageEntity();
        e.setSlug(slug);
        e.setTitle(title);
        e.setContentMarkdown(markdown);
        e.setPageType("ENTITY");
        e.setCreatedAt(Instant.parse("2026-10-26T00:00:00Z"));
        e.setRevision(revision);
        return e;
    }

    @BeforeEach
    void setUp() {
        ObjectProvider<VectorStore> provider = new ObjectProvider<>() {
            @Override public VectorStore getObject()      { return vectorStore; }
            @Override public VectorStore getIfAvailable() { return vectorStore; }
            @Override public VectorStore getIfUnique()    { return vectorStore; }
        };
        scheduler = new WikiEmbeddingScheduler(repository, provider, mock(PipelineHealthService.class));
    }

    // -------------------------------------------------------------------------
    // embedWikiPages() — happy path
    // -------------------------------------------------------------------------

    @Test
    void embedWikiPages_callsVectorStoreAddWithOneDocumentPerEntity() {
        WikiPageEntity p1 = page("fda-ai-guidance", "FDA AI Guidance", "Some content.", 1);
        WikiPageEntity p2 = page("epic-ambient", "Epic Ambient AI", "Other content.", 1);
        when(repository.findNeedingEmbedding()).thenReturn(List.of(p1, p2));

        scheduler.embedWikiPages();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());

        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void embedWikiPages_documentIdDerivedFromSlug() {
        WikiPageEntity p = page("fda-ai-guidance", "Title", "Body.", 1);
        when(repository.findNeedingEmbedding()).thenReturn(List.of(p));

        scheduler.embedWikiPages();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());

        Document doc = captor.getValue().get(0);
        String expectedId = java.util.UUID.nameUUIDFromBytes(
                "fda-ai-guidance".getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        assertThat(doc.getId()).isEqualTo(expectedId);
    }

    @Test
    void embedWikiPages_documentContentCombinesTitleAndMarkdown() {
        WikiPageEntity p = page("fda-ai-guidance", "My Title", "My markdown.", 1);
        when(repository.findNeedingEmbedding()).thenReturn(List.of(p));

        scheduler.embedWikiPages();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());

        Document doc = captor.getValue().get(0);
        assertThat(doc.getText()).contains("My Title").contains("My markdown.");
    }

    @Test
    void embedWikiPages_documentMetadataTaggedAsWikiPageWithSlug() {
        WikiPageEntity p = page("fda-ai-guidance", "Title", "Body.", 1);
        when(repository.findNeedingEmbedding()).thenReturn(List.of(p));

        scheduler.embedWikiPages();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());

        Document doc = captor.getValue().get(0);
        assertThat(doc.getMetadata().get("type")).isEqualTo("wiki_page");
        assertThat(doc.getMetadata().get("slug")).isEqualTo("fda-ai-guidance");
    }

    @Test
    void embedWikiPages_marksLastEmbeddedRevisionAfterSuccess() {
        WikiPageEntity p1 = page("fda-ai-guidance", "Title One", "Body one.", 3);
        WikiPageEntity p2 = page("epic-ambient", "Title Two", "Body two.", 1);
        when(repository.findNeedingEmbedding()).thenReturn(List.of(p1, p2));

        scheduler.embedWikiPages();

        assertThat(p1.getLastEmbeddedRevision()).isEqualTo(3);
        assertThat(p2.getLastEmbeddedRevision()).isEqualTo(1);
        verify(repository).saveAll(anyList());
    }

    // -------------------------------------------------------------------------
    // embedWikiPages() — nothing to embed
    // -------------------------------------------------------------------------

    @Test
    void embedWikiPages_nothingNeedingEmbedding_doesNotCallVectorStore() {
        when(repository.findNeedingEmbedding()).thenReturn(List.of());

        scheduler.embedWikiPages();

        verify(vectorStore, never()).add(anyList());
    }

    // -------------------------------------------------------------------------
    // embedWikiPages() — exception handling
    // -------------------------------------------------------------------------

    @Test
    void embedWikiPages_vectorStoreThrows_doesNotPropagateException() {
        WikiPageEntity p = page("fda-ai-guidance", "Title", "Body.", 1);
        when(repository.findNeedingEmbedding()).thenReturn(List.of(p));
        doThrow(new RuntimeException("invalid API key")).when(vectorStore).add(anyList());

        // Must not throw — the scheduler swallows embedding failures so the thread stays alive
        scheduler.embedWikiPages();
    }
}
