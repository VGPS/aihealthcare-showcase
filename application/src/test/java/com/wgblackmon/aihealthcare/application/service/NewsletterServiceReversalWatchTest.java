package com.wgblackmon.aihealthcare.application.service;

import com.wgblackmon.aihealthcare.domain.model.Contradiction;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterDraft;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
import com.wgblackmon.aihealthcare.domain.model.NewsletterTone;
import com.wgblackmon.aihealthcare.domain.model.SectionType;
import com.wgblackmon.aihealthcare.domain.model.SourceRef;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSummarizationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleSearchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterRunPort;
import com.wgblackmon.aihealthcare.domain.port.inbound.MonitorRegulatoryEventsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.WikiQueryPort;
import com.wgblackmon.aihealthcare.domain.service.ArticleQualityFilter;
import com.wgblackmon.aihealthcare.domain.service.LegalBriefSectionBuilder;
import com.wgblackmon.aihealthcare.domain.service.NewsletterRenderer;
import com.wgblackmon.aihealthcare.domain.service.NewsletterService;
import com.wgblackmon.aihealthcare.domain.service.ReversalWatchSectionBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests verifying that {@link NewsletterService#generate} integrates
 * the Reversal Watch section from wiki contradictions.
 *
 * <p>When recent contradictions exist, a {@link SectionType#REVERSAL_WATCH}
 * section is appended as the last section.  When no contradictions exist,
 * no section is added.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-05
 * @updated 2026-07-05
 */
@ExtendWith(MockitoExtension.class)
class NewsletterServiceReversalWatchTest {

    @Mock
    private ArticleIngestionPort ingestionPort;

    @Mock
    private AiSummarizationPort summarizationPort;

    @Mock
    private NewsletterRunPort newsletterRunPort;

    @Mock
    private ArticleSearchPort searchPort;

    @Mock
    private WikiQueryPort wikiQueryPort;

    @Mock
    private MonitorRegulatoryEventsUseCase regulatoryUseCase;

    private NewsletterService service;

    private static final String RUN_ID   = "run-rw-001";
    private static final String DRAFT_ID = "draft-rw-001";
    private static final String TOPIC    = "AI diagnostics";

    private static final NewsArticle ARTICLE = new NewsArticle(
            "article-001",
            "AI Improves Diagnostic Accuracy",
            URI.create("https://example.com/article-001"),
            "Researchers found that AI models outperform radiologists.",
            TOPIC,
            null, null, "PubMed", "ACADEMIC", 0.9, null
    );

    private static final NewsletterSection SECTION = new NewsletterSection(
            "section-001",
            SectionType.WHAT_SHIPPED,
            TOPIC,
            "AI Outperforms Radiologists",
            "A new study confirms AI-assisted diagnosis improves accuracy by 20%.",
            List.of("article-001")
    );

    private static final Contradiction CONTRADICTION = new Contradiction(
            "fda-ai-guidance",
            "AI tools require full premarket review",
            "AI tools may use predetermined change control plans",
            List.of(new SourceRef("art-prior", "FDA", LocalDate.of(2026, 6, 1), "Original text")),
            List.of(new SourceRef("art-new", "FDA", LocalDate.of(2026, 7, 1), "Updated text")),
            Instant.now()
    );

    @BeforeEach
    void setUp() {
        LegalBriefSectionBuilder legalBriefBuilder = new LegalBriefSectionBuilder(
                ingestionPort, regulatoryUseCase, new ArticleQualityFilter());
        service = new NewsletterService(ingestionPort, summarizationPort,
                new NewsletterRenderer(), newsletterRunPort, searchPort,
                wikiQueryPort, new ReversalWatchSectionBuilder(), legalBriefBuilder);
    }

    @Test
    @DisplayName("generate() with contradictions appends REVERSAL_WATCH as last section")
    void generate_withContradictions_appendsReversalWatchSection() {
        when(ingestionPort.fetchArticles(anyString(), anyInt())).thenReturn(List.of(ARTICLE));
        when(summarizationPort.summarize(anyList(), anyString(), any(NewsletterTone.class), anyString()))
                .thenReturn(SECTION);
        when(summarizationPort.generateIntroduction(anyList(), any(NewsletterTone.class)))
                .thenReturn("Welcome to the newsletter.");
        when(wikiQueryPort.recentContradictions(any(Instant.class)))
                .thenReturn(List.of(CONTRADICTION));

        service.ingest(RUN_ID, LocalDate.now(), List.of(TOPIC), 5);
        NewsletterDraft draft = service.generate(RUN_ID, DRAFT_ID, "AI Weekly",
                NewsletterTone.PROFESSIONAL, 3, false, 3);

        List<NewsletterSection> sections = draft.sections();
        assertThat(sections).hasSizeGreaterThan(1);

        NewsletterSection lastSection = sections.get(sections.size() - 1);
        assertThat(lastSection.sectionType()).isEqualTo(SectionType.REVERSAL_WATCH);
        assertThat(lastSection.headline()).contains("Contradiction");
    }

    @Test
    @DisplayName("generate() with no contradictions omits REVERSAL_WATCH section")
    void generate_withNoContradictions_omitsReversalWatchSection() {
        when(ingestionPort.fetchArticles(anyString(), anyInt())).thenReturn(List.of(ARTICLE));
        when(summarizationPort.summarize(anyList(), anyString(), any(NewsletterTone.class), anyString()))
                .thenReturn(SECTION);
        when(summarizationPort.generateIntroduction(anyList(), any(NewsletterTone.class)))
                .thenReturn("Welcome to the newsletter.");
        when(wikiQueryPort.recentContradictions(any(Instant.class)))
                .thenReturn(Collections.emptyList());

        service.ingest(RUN_ID, LocalDate.now(), List.of(TOPIC), 5);
        NewsletterDraft draft = service.generate(RUN_ID, DRAFT_ID, "AI Weekly",
                NewsletterTone.PROFESSIONAL, 3, false, 3);

        for (NewsletterSection section : draft.sections()) {
            assertThat(section.sectionType()).isNotEqualTo(SectionType.REVERSAL_WATCH);
        }
    }

    @Test
    @DisplayName("generate() places REVERSAL_WATCH section last in the list")
    void generate_reversalWatchSectionAppearsLast() {
        when(ingestionPort.fetchArticles(anyString(), anyInt())).thenReturn(List.of(ARTICLE));
        when(summarizationPort.summarize(anyList(), anyString(), any(NewsletterTone.class), anyString()))
                .thenReturn(SECTION);
        when(summarizationPort.generateIntroduction(anyList(), any(NewsletterTone.class)))
                .thenReturn("Welcome to the newsletter.");
        when(wikiQueryPort.recentContradictions(any(Instant.class)))
                .thenReturn(List.of(CONTRADICTION));

        service.ingest(RUN_ID, LocalDate.now(), List.of(TOPIC), 5);
        NewsletterDraft draft = service.generate(RUN_ID, DRAFT_ID, "AI Weekly",
                NewsletterTone.PROFESSIONAL, 3, false, 3);

        List<NewsletterSection> sections = draft.sections();
        // First section is the topic section, last is Reversal Watch
        assertThat(sections.get(0).sectionType()).isNotEqualTo(SectionType.REVERSAL_WATCH);
        assertThat(sections.get(sections.size() - 1).sectionType()).isEqualTo(SectionType.REVERSAL_WATCH);
    }
}
