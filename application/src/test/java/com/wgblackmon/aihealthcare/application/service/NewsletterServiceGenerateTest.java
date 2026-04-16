package com.wgblackmon.aihealthcare.application.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRunStatus;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
import com.wgblackmon.aihealthcare.domain.model.NewsletterTone;
import com.wgblackmon.aihealthcare.domain.model.SectionType;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSummarizationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests verifying that {@link NewsletterService#generate} persists a
 * {@link NewsletterRun} via {@link NewsletterRunPort} with the correct content.
 *
 * <p>A real {@link NewsletterRenderer} is used (it is pure Java with no
 * dependencies) so that {@code htmlContent} and {@code plainTextContent}
 * assertions reflect actual rendered output.  All ports are Mockito mocks —
 * no Spring context or database is involved.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-11
 * @updated 2026-04-11
 */
@ExtendWith(MockitoExtension.class)
class NewsletterServiceGenerateTest {

    @Mock
    private ArticleIngestionPort ingestionPort;

    @Mock
    private AiSummarizationPort summarizationPort;

    @Mock
    private NewsletterRunPort newsletterRunPort;

    private NewsletterService service;

    private static final String RUN_ID   = "run-001";
    private static final String DRAFT_ID = "draft-001";
    private static final String TOPIC    = "PubMed AI Healthcare";

    private static final NewsArticle ARTICLE = new NewsArticle(
            "article-001",
            "AI Improves Diagnostic Accuracy",
            URI.create("https://example.com/article-001"),
            "Researchers found that AI models outperform radiologists.",
            TOPIC,
            null, 1L, "PubMed AI Healthcare", "ACADEMIC", 0.9, null
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
                                        new NewsletterRenderer(), newsletterRunPort);
    }

    @Test
    @DisplayName("generate() calls NewsletterRunPort.save() exactly once")
    void generate_savesNewsletterRun() {
        when(ingestionPort.fetchArticles(anyString(), anyInt())).thenReturn(List.of(ARTICLE));
        when(summarizationPort.summarize(anyList(), anyString(), any(NewsletterTone.class), anyString()))
                .thenReturn(SECTION);
        when(summarizationPort.generateIntroduction(anyList(), any(NewsletterTone.class)))
                .thenReturn("Welcome to the newsletter.");

        service.ingest(RUN_ID, LocalDate.now(), List.of(TOPIC), 5);
        service.generate(RUN_ID, DRAFT_ID, "AI in Healthcare Weekly",
                         NewsletterTone.PROFESSIONAL, 3);

        verify(newsletterRunPort).save(any(NewsletterRun.class));
    }

    @Test
    @DisplayName("generate() saves a run with status DRAFT")
    void generate_runHasStatusDraft() {
        when(ingestionPort.fetchArticles(anyString(), anyInt())).thenReturn(List.of(ARTICLE));
        when(summarizationPort.summarize(anyList(), anyString(), any(NewsletterTone.class), anyString()))
                .thenReturn(SECTION);
        when(summarizationPort.generateIntroduction(anyList(), any(NewsletterTone.class)))
                .thenReturn("Welcome to the newsletter.");

        service.ingest(RUN_ID, LocalDate.now(), List.of(TOPIC), 5);
        service.generate(RUN_ID, DRAFT_ID, "AI in Healthcare Weekly",
                         NewsletterTone.PROFESSIONAL, 3);

        ArgumentCaptor<NewsletterRun> captor = ArgumentCaptor.forClass(NewsletterRun.class);
        verify(newsletterRunPort).save(captor.capture());

        assertThat(captor.getValue().status()).isEqualTo(NewsletterRunStatus.DRAFT);
    }

    @Test
    @DisplayName("generate() saves a run with non-blank HTML and plain-text content")
    void generate_runHasNonBlankRenderedContent() {
        when(ingestionPort.fetchArticles(anyString(), anyInt())).thenReturn(List.of(ARTICLE));
        when(summarizationPort.summarize(anyList(), anyString(), any(NewsletterTone.class), anyString()))
                .thenReturn(SECTION);
        when(summarizationPort.generateIntroduction(anyList(), any(NewsletterTone.class)))
                .thenReturn("Welcome to the newsletter.");

        service.ingest(RUN_ID, LocalDate.now(), List.of(TOPIC), 5);
        service.generate(RUN_ID, DRAFT_ID, "AI in Healthcare Weekly",
                         NewsletterTone.PROFESSIONAL, 3);

        ArgumentCaptor<NewsletterRun> captor = ArgumentCaptor.forClass(NewsletterRun.class);
        verify(newsletterRunPort).save(captor.capture());

        assertThat(captor.getValue().htmlContent()).isNotBlank();
        assertThat(captor.getValue().plainTextContent()).isNotBlank();
    }

    @Test
    @DisplayName("generate() saves a run whose HTML contains the newsletter title")
    void generate_htmlContentContainsTitle() {
        when(ingestionPort.fetchArticles(anyString(), anyInt())).thenReturn(List.of(ARTICLE));
        when(summarizationPort.summarize(anyList(), anyString(), any(NewsletterTone.class), anyString()))
                .thenReturn(SECTION);
        when(summarizationPort.generateIntroduction(anyList(), any(NewsletterTone.class)))
                .thenReturn("Welcome to the newsletter.");

        service.ingest(RUN_ID, LocalDate.now(), List.of(TOPIC), 5);
        service.generate(RUN_ID, DRAFT_ID, "AI in Healthcare Weekly",
                         NewsletterTone.PROFESSIONAL, 3);

        ArgumentCaptor<NewsletterRun> captor = ArgumentCaptor.forClass(NewsletterRun.class);
        verify(newsletterRunPort).save(captor.capture());

        assertThat(captor.getValue().htmlContent()).contains("AI in Healthcare Weekly");
        assertThat(captor.getValue().plainTextContent()).contains("AI in Healthcare Weekly");
    }
}
