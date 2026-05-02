package com.wgblackmon.aihealthcare.application.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
import com.wgblackmon.aihealthcare.domain.model.NewsletterTone;
import com.wgblackmon.aihealthcare.domain.model.SectionType;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSummarizationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleSearchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterRunPort;
import com.wgblackmon.aihealthcare.domain.service.NewsletterRenderer;
import com.wgblackmon.aihealthcare.domain.service.NewsletterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests verifying the RAG (retrieval-augmented generation) path in
 * {@link NewsletterService#generate}.
 *
 * <p>When {@code ragEnabled=true} the service must:
 * <ol>
 *   <li>Call {@link ArticleSearchPort#findSimilar} once per topic group.</li>
 *   <li>De-duplicate results against the fresh article batch.</li>
 *   <li>Delegate to {@link AiSummarizationPort#summarizeWithContext} (not {@code summarize}).</li>
 * </ol>
 * When {@code ragEnabled=false} the service must NOT call the vector store at all.
 *
 * <p>No Spring context is loaded — all ports are Mockito mocks.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-27
 * @updated 2026-04-27
 */
@ExtendWith(MockitoExtension.class)
class NewsletterServiceRagTest {

    @Mock
    private ArticleIngestionPort ingestionPort;

    @Mock
    private AiSummarizationPort summarizationPort;

    @Mock
    private NewsletterRunPort newsletterRunPort;

    @Mock
    private ArticleSearchPort searchPort;

    private NewsletterService service;

    private static final String RUN_ID   = "run-rag-001";
    private static final String DRAFT_ID = "draft-rag-001";
    private static final String TOPIC    = "AI diagnostics";

    private static final NewsArticle FRESH_ARTICLE = new NewsArticle(
            "fresh-001",
            "New AI Diagnostic Tool Approved",
            URI.create("https://example.com/fresh-001"),
            "The FDA approved a new AI-powered diagnostic system this week.",
            TOPIC,
            null, null, "PubMed", "ACADEMIC", 0.9, null
    );

    private static final NewsArticle PAST_ARTICLE = new NewsArticle(
            "past-001",
            "AI Diagnostics: A Year in Review",
            URI.create("https://example.com/past-001"),
            "Last year saw ten AI diagnostic tools receive regulatory clearance.",
            TOPIC,
            null, null, "PubMed", "ACADEMIC", 0.8, null
    );

    private static final NewsletterSection SECTION = new NewsletterSection(
            "section-001",
            SectionType.WHAT_SHIPPED,
            TOPIC,
            "FDA Clears AI Diagnostic Platform",
            "A new AI-powered diagnostic system received FDA approval, building on a year of regulatory advances.",
            List.of("fresh-001")
    );

    @BeforeEach
    void setUp() {
        service = new NewsletterService(ingestionPort, summarizationPort,
                                        new NewsletterRenderer(), newsletterRunPort, searchPort);
    }

    // -------------------------------------------------------------------------
    // RAG enabled — happy path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generate() with ragEnabled=true calls ArticleSearchPort.findSimilar for the topic")
    void generate_ragEnabled_callsFindSimilarForTopic() {
        when(ingestionPort.fetchArticles(anyString(), anyInt())).thenReturn(List.of(FRESH_ARTICLE));
        when(searchPort.findSimilar(eq(TOPIC), eq(2))).thenReturn(List.of(PAST_ARTICLE));
        when(summarizationPort.summarizeWithContext(anyList(), anyList(), anyString(),
                any(NewsletterTone.class), anyString())).thenReturn(SECTION);
        when(summarizationPort.generateIntroduction(anyList(), any(NewsletterTone.class)))
                .thenReturn("Intro.");

        service.ingest(RUN_ID, LocalDate.now(), List.of(TOPIC), 5);
        service.generate(RUN_ID, DRAFT_ID, "AI in Healthcare Weekly",
                         NewsletterTone.PROFESSIONAL, 5, true, 2);

        verify(searchPort).findSimilar(eq(TOPIC), eq(2));
    }

    @Test
    @DisplayName("generate() with ragEnabled=true delegates to summarizeWithContext (not summarize)")
    void generate_ragEnabled_callsSummarizeWithContext() {
        when(ingestionPort.fetchArticles(anyString(), anyInt())).thenReturn(List.of(FRESH_ARTICLE));
        when(searchPort.findSimilar(anyString(), anyInt())).thenReturn(List.of(PAST_ARTICLE));
        when(summarizationPort.summarizeWithContext(anyList(), anyList(), anyString(),
                any(NewsletterTone.class), anyString())).thenReturn(SECTION);
        when(summarizationPort.generateIntroduction(anyList(), any(NewsletterTone.class)))
                .thenReturn("Intro.");

        service.ingest(RUN_ID, LocalDate.now(), List.of(TOPIC), 5);
        service.generate(RUN_ID, DRAFT_ID, "AI in Healthcare Weekly",
                         NewsletterTone.PROFESSIONAL, 5, true, 3);

        verify(summarizationPort).summarizeWithContext(anyList(), anyList(), anyString(),
                any(NewsletterTone.class), anyString());
        verify(summarizationPort, never()).summarize(anyList(), anyString(),
                any(NewsletterTone.class), anyString());
    }

    @Test
    @DisplayName("generate() with ragEnabled=true de-duplicates fresh articles from RAG context")
    void generate_ragEnabled_deduplicatesFreshArticlesFromContext() {
        // PAST_ARTICLE has same articleId as FRESH_ARTICLE — should be filtered out
        NewsArticle duplicate = new NewsArticle(
                "fresh-001",  // same ID as FRESH_ARTICLE
                "Duplicate Past Entry",
                URI.create("https://example.com/past-dup"),
                "This should be filtered out because it duplicates a fresh article.",
                TOPIC,
                null, null, null, null, 0.5, null
        );

        when(ingestionPort.fetchArticles(anyString(), anyInt())).thenReturn(List.of(FRESH_ARTICLE));
        when(searchPort.findSimilar(anyString(), anyInt())).thenReturn(List.of(duplicate, PAST_ARTICLE));
        when(summarizationPort.summarizeWithContext(anyList(), anyList(), anyString(),
                any(NewsletterTone.class), anyString())).thenReturn(SECTION);
        when(summarizationPort.generateIntroduction(anyList(), any(NewsletterTone.class)))
                .thenReturn("Intro.");

        service.ingest(RUN_ID, LocalDate.now(), List.of(TOPIC), 5);
        service.generate(RUN_ID, DRAFT_ID, "AI in Healthcare Weekly",
                         NewsletterTone.PROFESSIONAL, 5, true, 3);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NewsArticle>> contextCaptor = ArgumentCaptor.forClass(List.class);
        verify(summarizationPort).summarizeWithContext(anyList(), contextCaptor.capture(),
                anyString(), any(NewsletterTone.class), anyString());

        List<NewsArticle> capturedContext = contextCaptor.getValue();
        // duplicate (fresh-001) must be removed; PAST_ARTICLE (past-001) must remain
        assertThat(capturedContext).hasSize(1);
        assertThat(capturedContext.get(0).articleId()).isEqualTo("past-001");
    }

    // -------------------------------------------------------------------------
    // RAG disabled
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generate() with ragEnabled=false does not call ArticleSearchPort")
    void generate_ragDisabled_doesNotCallSearchPort() {
        when(ingestionPort.fetchArticles(anyString(), anyInt())).thenReturn(List.of(FRESH_ARTICLE));
        when(summarizationPort.summarize(anyList(), anyString(), any(NewsletterTone.class), anyString()))
                .thenReturn(SECTION);
        when(summarizationPort.generateIntroduction(anyList(), any(NewsletterTone.class)))
                .thenReturn("Intro.");

        service.ingest(RUN_ID, LocalDate.now(), List.of(TOPIC), 5);
        service.generate(RUN_ID, DRAFT_ID, "AI in Healthcare Weekly",
                         NewsletterTone.PROFESSIONAL, 5, false, 3);

        verify(searchPort, never()).findSimilar(anyString(), anyInt());
        verify(summarizationPort, never()).summarizeWithContext(anyList(), anyList(),
                anyString(), any(NewsletterTone.class), anyString());
    }
}
