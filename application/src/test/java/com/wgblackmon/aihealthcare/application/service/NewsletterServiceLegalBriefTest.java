package com.wgblackmon.aihealthcare.application.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterDraft;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
import com.wgblackmon.aihealthcare.domain.model.NewsletterTone;
import com.wgblackmon.aihealthcare.domain.model.SectionType;
import com.wgblackmon.aihealthcare.domain.port.inbound.MonitorRegulatoryEventsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSummarizationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleSearchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterRunPort;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests verifying that {@link NewsletterService#generate} integrates
 * the Legal Brief section from legal articles and regulatory events.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-07-30
 */
@ExtendWith(MockitoExtension.class)
class NewsletterServiceLegalBriefTest {

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

    private static final String RUN_ID   = "run-legal-001";
    private static final String DRAFT_ID = "draft-legal-001";
    private static final String TOPIC    = "AI diagnostics";

    private static final NewsArticle ARTICLE = new NewsArticle(
            "article-001", "AI Improves Diagnostic Accuracy",
            URI.create("https://example.com/article-001"),
            "Researchers found that AI models outperform radiologists.",
            TOPIC, null, null, "PubMed", "ACADEMIC", 0.9, null
    );

    private static final NewsArticle LEGAL_ARTICLE = new NewsArticle(
            "legal-001", "AI Malpractice Lawsuit Filed",
            URI.create("https://example.com/legal-001"),
            "Hospital faces lawsuit over AI diagnosis.",
            "AI Healthcare Legal", null, null, "Legal News", "INDUSTRY", 0.7, Instant.now()
    );

    private static final NewsletterSection SECTION = new NewsletterSection(
            "section-001", SectionType.WHAT_SHIPPED, TOPIC,
            "AI Outperforms Radiologists",
            "A new study confirms AI-assisted diagnosis improves accuracy by 20%.",
            List.of("article-001")
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
    @DisplayName("generate() with legal data appends LEGAL_BRIEF section")
    void generate_withLegalData_appendsLegalBriefSection() {
        when(ingestionPort.fetchArticles(anyString(), anyInt())).thenReturn(List.of(ARTICLE));
        when(ingestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Legal"), anyInt()))
                .thenReturn(List.of(LEGAL_ARTICLE));
        when(ingestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Government Policy"), anyInt()))
                .thenReturn(Collections.emptyList());
        when(regulatoryUseCase.getRecentEvents(anyInt()))
                .thenReturn(Collections.emptyList());
        when(summarizationPort.summarize(anyList(), anyString(), any(NewsletterTone.class), anyString()))
                .thenReturn(SECTION);
        when(summarizationPort.generateIntroduction(anyList(), any(NewsletterTone.class)))
                .thenReturn("Welcome.");
        when(wikiQueryPort.recentContradictions(any(Instant.class)))
                .thenReturn(Collections.emptyList());

        service.ingest(RUN_ID, LocalDate.now(), List.of(TOPIC), 5);
        NewsletterDraft draft = service.generate(RUN_ID, DRAFT_ID, "AI Weekly",
                NewsletterTone.PROFESSIONAL, 3, false, 3);

        boolean hasLegalBrief = false;
        for (NewsletterSection section : draft.sections()) {
            if (section.sectionType() == SectionType.LEGAL_BRIEF) {
                hasLegalBrief = true;
            }
        }
        assertThat(hasLegalBrief).isTrue();
    }

    @Test
    @DisplayName("generate() with no legal data omits LEGAL_BRIEF section")
    void generate_noLegalData_omitsLegalBriefSection() {
        when(ingestionPort.fetchArticles(anyString(), anyInt())).thenReturn(List.of(ARTICLE));
        when(ingestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Legal"), anyInt()))
                .thenReturn(Collections.emptyList());
        when(ingestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Government Policy"), anyInt()))
                .thenReturn(Collections.emptyList());
        when(regulatoryUseCase.getRecentEvents(anyInt()))
                .thenReturn(Collections.emptyList());
        when(summarizationPort.summarize(anyList(), anyString(), any(NewsletterTone.class), anyString()))
                .thenReturn(SECTION);
        when(summarizationPort.generateIntroduction(anyList(), any(NewsletterTone.class)))
                .thenReturn("Welcome.");
        when(wikiQueryPort.recentContradictions(any(Instant.class)))
                .thenReturn(Collections.emptyList());

        service.ingest(RUN_ID, LocalDate.now(), List.of(TOPIC), 5);
        NewsletterDraft draft = service.generate(RUN_ID, DRAFT_ID, "AI Weekly",
                NewsletterTone.PROFESSIONAL, 3, false, 3);

        for (NewsletterSection section : draft.sections()) {
            assertThat(section.sectionType()).isNotEqualTo(SectionType.LEGAL_BRIEF);
        }
    }

    @Test
    @DisplayName("generate() places LEGAL_BRIEF after REVERSAL_WATCH when both present")
    void generate_legalBriefAfterReversalWatch() {
        when(ingestionPort.fetchArticles(anyString(), anyInt())).thenReturn(List.of(ARTICLE));
        when(ingestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Legal"), anyInt()))
                .thenReturn(List.of(LEGAL_ARTICLE));
        when(ingestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Government Policy"), anyInt()))
                .thenReturn(Collections.emptyList());
        when(regulatoryUseCase.getRecentEvents(anyInt()))
                .thenReturn(Collections.emptyList());
        when(summarizationPort.summarize(anyList(), anyString(), any(NewsletterTone.class), anyString()))
                .thenReturn(SECTION);
        when(summarizationPort.generateIntroduction(anyList(), any(NewsletterTone.class)))
                .thenReturn("Welcome.");

        // Provide contradictions so Reversal Watch is included
        com.wgblackmon.aihealthcare.domain.model.Contradiction contradiction =
                new com.wgblackmon.aihealthcare.domain.model.Contradiction(
                        "test-slug", "Prior claim", "New claim",
                        List.of(new com.wgblackmon.aihealthcare.domain.model.SourceRef(
                                "art-1", "Source", LocalDate.now(), "excerpt")),
                        List.of(new com.wgblackmon.aihealthcare.domain.model.SourceRef(
                                "art-2", "Source", LocalDate.now(), "excerpt")),
                        Instant.now());
        when(wikiQueryPort.recentContradictions(any(Instant.class)))
                .thenReturn(List.of(contradiction));

        service.ingest(RUN_ID, LocalDate.now(), List.of(TOPIC), 5);
        NewsletterDraft draft = service.generate(RUN_ID, DRAFT_ID, "AI Weekly",
                NewsletterTone.PROFESSIONAL, 3, false, 3);

        List<NewsletterSection> sections = draft.sections();
        int reversalIdx = -1;
        int legalIdx = -1;
        for (int i = 0; i < sections.size(); i++) {
            if (sections.get(i).sectionType() == SectionType.REVERSAL_WATCH) {
                reversalIdx = i;
            }
            if (sections.get(i).sectionType() == SectionType.LEGAL_BRIEF) {
                legalIdx = i;
            }
        }
        assertThat(reversalIdx).isGreaterThan(-1);
        assertThat(legalIdx).isGreaterThan(-1);
        assertThat(legalIdx).isGreaterThan(reversalIdx);
    }
}
