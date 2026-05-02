package com.wgblackmon.aihealthcare.application.service;

import com.wgblackmon.aihealthcare.domain.exception.NoArticlesFoundException;
import com.wgblackmon.aihealthcare.domain.exception.RunNotFoundException;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterDraft;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NewsletterService} demonstrating the mock-AI testing pattern.
 *
 * <p>Both {@link ArticleIngestionPort} and {@link AiSummarizationPort} are injected
 * as Mockito mocks — no real HTTP calls, no real AI calls, and no Spring context is
 * loaded. This is the standard pattern for testing application-layer services in this
 * project: mock the outbound ports, drive through inbound use-case methods, assert
 * on the resulting domain objects.
 *
 * <p>AI integration smoke tests (real calls to Anthropic/OpenAI) live separately in
 * {@code infrastructure/ai} and are gated behind {@code @Profile("ai-integration")}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-04
 * @updated 2026-04-11
 */
@ExtendWith(MockitoExtension.class)
class NewsletterServiceTest {

    @Mock
    private ArticleIngestionPort ingestionPort;

    @Mock
    private AiSummarizationPort summarizationPort;

    @Mock
    private NewsletterRunPort newsletterRunPort;

    @Mock
    private ArticleSearchPort searchPort;

    private NewsletterService service;

    // --- shared fixtures ---

    private static final String RUN_ID   = "run-001";
    private static final String DRAFT_ID = "draft-001";
    private static final String TOPIC    = "AI diagnostics";

    private static final NewsArticle ARTICLE = new NewsArticle(
            "article-001",
            "AI Improves Diagnostic Accuracy",
            URI.create("https://example.com/article-001"),
            "Researchers found that AI models outperform radiologists.",
            TOPIC,
            null,   // author — optional
            null,   // topicId — null until Slice 2
            null,   // sourceName
            null,   // sourceTier
            0.5,    // sourceWeight
            null    // publishedAt — optional
    );

    private static final NewsletterSection SECTION = new NewsletterSection(
            "section-001",
            SectionType.WHAT_SHIPPED,
            TOPIC,
            "AI Outperforms Radiologists",
            "A new study confirms AI-assisted diagnosis improves accuracy by 20%.",
            List.of("article-001")
    );

    @BeforeEach
    void setUp() {
        service = new NewsletterService(ingestionPort, summarizationPort,
                                        new NewsletterRenderer(), newsletterRunPort, searchPort);
    }

    // -------------------------------------------------------------------------
    // ingest()
    // -------------------------------------------------------------------------

    @Test
    void ingest_returnsArticlesFromPort() {
        when(ingestionPort.fetchArticles(eq(TOPIC), anyInt()))
                .thenReturn(List.of(ARTICLE));

        List<NewsArticle> result = service.ingest(RUN_ID, LocalDate.now(), List.of(TOPIC), 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).articleId()).isEqualTo("article-001");
    }

    @Test
    void ingest_aggregatesAcrossMultipleTopics() {
        NewsArticle article2 = new NewsArticle(
                "article-002", "ML in Drug Discovery",
                URI.create("https://example.com/article-002"),
                "Machine learning is accelerating drug discovery pipelines.",
                "ML drug discovery", null, null, null, null, 0.5, null
        );

        when(ingestionPort.fetchArticles(eq(TOPIC), anyInt())).thenReturn(List.of(ARTICLE));
        when(ingestionPort.fetchArticles(eq("ML drug discovery"), anyInt())).thenReturn(List.of(article2));

        List<NewsArticle> result = service.ingest(RUN_ID, LocalDate.now(),
                List.of(TOPIC, "ML drug discovery"), 5);

        assertThat(result).hasSize(2);
    }

    @Test
    void ingest_blankRunId_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.ingest("", LocalDate.now(), List.of(TOPIC), 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runId");
    }

    @Test
    void ingest_emptyTopics_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.ingest(RUN_ID, LocalDate.now(), List.of(), 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topics");
    }

    @Test
    void ingest_zeroMaxArticles_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.ingest(RUN_ID, LocalDate.now(), List.of(TOPIC), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxArticlesPerTopic");
    }

    // -------------------------------------------------------------------------
    // generate()
    // -------------------------------------------------------------------------

    @Test
    void generate_producesWellFormedDraft() {
        when(ingestionPort.fetchArticles(anyString(), anyInt())).thenReturn(List.of(ARTICLE));
        when(summarizationPort.summarize(anyList(), anyString(), any(NewsletterTone.class), anyString()))
                .thenReturn(SECTION);
        when(summarizationPort.generateIntroduction(anyList(), any(NewsletterTone.class)))
                .thenReturn("Welcome to this week's AI in Healthcare newsletter.");

        service.ingest(RUN_ID, LocalDate.now(), List.of(TOPIC), 5);
        NewsletterDraft draft = service.generate(RUN_ID, DRAFT_ID, "AI in Healthcare Weekly",
                NewsletterTone.PROFESSIONAL, 3, false, 3);

        assertThat(draft.draftId()).isEqualTo(DRAFT_ID);
        assertThat(draft.runId()).isEqualTo(RUN_ID);
        assertThat(draft.sections()).hasSize(1);
        assertThat(draft.sourceArticles()).hasSize(1);
        assertThat(draft.introduction()).isNotBlank();
        assertThat(draft.generatedAt()).isNotNull();
    }

    @Test
    void generate_unknownRunId_throwsRunNotFoundException() {
        assertThatThrownBy(() -> service.generate("no-such-run", DRAFT_ID,
                "Title", NewsletterTone.ACCESSIBLE, 3, false, 3))
                .isInstanceOf(RunNotFoundException.class);
    }

    @Test
    void generate_emptyIngestRun_throwsNoArticlesFoundException() {
        when(ingestionPort.fetchArticles(anyString(), anyInt())).thenReturn(List.of());

        service.ingest(RUN_ID, LocalDate.now(), List.of(TOPIC), 5);

        assertThatThrownBy(() -> service.generate(RUN_ID, DRAFT_ID,
                "Title", NewsletterTone.TECHNICAL, 3, false, 3))
                .isInstanceOf(NoArticlesFoundException.class);
    }

    // -------------------------------------------------------------------------
    // getDraft()
    // -------------------------------------------------------------------------

    @Test
    void getDraft_retrievesPreviouslyGeneratedDraft() {
        when(ingestionPort.fetchArticles(anyString(), anyInt())).thenReturn(List.of(ARTICLE));
        when(summarizationPort.summarize(anyList(), anyString(), any(NewsletterTone.class), anyString()))
                .thenReturn(SECTION);
        when(summarizationPort.generateIntroduction(anyList(), any(NewsletterTone.class)))
                .thenReturn("Intro.");

        service.ingest(RUN_ID, LocalDate.now(), List.of(TOPIC), 5);
        service.generate(RUN_ID, DRAFT_ID, "Title", NewsletterTone.PROFESSIONAL, 3, false, 3);

        NewsletterDraft retrieved = service.getDraft(DRAFT_ID);

        assertThat(retrieved.draftId()).isEqualTo(DRAFT_ID);
    }

    @Test
    void getDraft_unknownDraftId_throwsRunNotFoundException() {
        assertThatThrownBy(() -> service.getDraft("no-such-draft"))
                .isInstanceOf(RunNotFoundException.class);
    }
}
