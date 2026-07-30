package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryBody;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEventType;
import com.wgblackmon.aihealthcare.domain.model.SectionType;
import com.wgblackmon.aihealthcare.domain.port.inbound.MonitorRegulatoryEventsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LegalBriefSectionBuilder}.
 *
 * <p>Verifies that the builder correctly merges legal articles, policy articles,
 * and regulatory events into a single LEGAL_BRIEF newsletter section, and
 * returns null when no data exists.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-07-30
 */
@ExtendWith(MockitoExtension.class)
class LegalBriefSectionBuilderTest {

    @Mock
    private ArticleIngestionPort articleIngestionPort;

    @Mock
    private MonitorRegulatoryEventsUseCase regulatoryUseCase;

    private LegalBriefSectionBuilder builder;

    private static final NewsArticle LEGAL_ARTICLE = new NewsArticle(
            "legal-001", "AI Malpractice Lawsuit Filed Against Hospital",
            URI.create("https://example.com/legal-001"),
            "A hospital faces a lawsuit over AI-assisted diagnosis.",
            "AI Healthcare Legal", null, null, "Legal News", "INDUSTRY", 0.7, Instant.now()
    );

    private static final NewsArticle POLICY_ARTICLE = new NewsArticle(
            "policy-001", "FDA Proposes New AI Device Guidance",
            URI.create("https://example.com/policy-001"),
            "The FDA has proposed new guidance for AI medical devices.",
            "AI Healthcare Government Policy", null, null, "FDA", "REGULATORY", 0.9, Instant.now()
    );

    private static final RegulatoryEvent REG_EVENT = new RegulatoryEvent(
            "reg-001", RegulatoryEventType.FDA_510K_CLEARANCE, RegulatoryBody.FDA,
            "AI-Powered ECG Monitor Cleared", "FDA clears AI ECG monitor",
            "K241234", "HeartTech Inc", "AI ECG Monitor",
            "https://fda.gov/510k/K241234", null, Instant.now(), Instant.now(),
            List.of("AI", "ECG"),
            null, null, null, null
    );

    @BeforeEach
    void setUp() {
        builder = new LegalBriefSectionBuilder(articleIngestionPort, regulatoryUseCase);
    }

    @Test
    @DisplayName("build() with litigation and policy articles returns LEGAL_BRIEF section")
    void build_withLitigationAndPolicyArticles_returnsSection() {
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Legal"), anyInt()))
                .thenReturn(List.of(LEGAL_ARTICLE));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Government Policy"), anyInt()))
                .thenReturn(List.of(POLICY_ARTICLE));
        when(regulatoryUseCase.getRecentEvents(anyInt()))
                .thenReturn(Collections.emptyList());

        NewsletterSection result = builder.build("section-010", 7);

        assertThat(result).isNotNull();
        assertThat(result.sectionType()).isEqualTo(SectionType.LEGAL_BRIEF);
        assertThat(result.summary()).contains("Litigation:");
        assertThat(result.summary()).contains("Policy:");
    }

    @Test
    @DisplayName("build() with regulatory events only returns section")
    void build_withRegulatoryEventsOnly_returnsSection() {
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Legal"), anyInt()))
                .thenReturn(Collections.emptyList());
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Government Policy"), anyInt()))
                .thenReturn(Collections.emptyList());
        when(regulatoryUseCase.getRecentEvents(anyInt()))
                .thenReturn(List.of(REG_EVENT));

        NewsletterSection result = builder.build("section-010", 7);

        assertThat(result).isNotNull();
        assertThat(result.summary()).contains("Regulatory:");
        assertThat(result.summary()).contains("FDA_510K_CLEARANCE");
    }

    @Test
    @DisplayName("build() with no data returns null")
    void build_withNoData_returnsNull() {
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Legal"), anyInt()))
                .thenReturn(Collections.emptyList());
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Government Policy"), anyInt()))
                .thenReturn(Collections.emptyList());
        when(regulatoryUseCase.getRecentEvents(anyInt()))
                .thenReturn(Collections.emptyList());

        NewsletterSection result = builder.build("section-010", 7);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("build() section type is LEGAL_BRIEF")
    void build_sectionType_isLegalBrief() {
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Legal"), anyInt()))
                .thenReturn(List.of(LEGAL_ARTICLE));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Government Policy"), anyInt()))
                .thenReturn(Collections.emptyList());
        when(regulatoryUseCase.getRecentEvents(anyInt()))
                .thenReturn(Collections.emptyList());

        NewsletterSection result = builder.build("section-010", 7);

        assertThat(result).isNotNull();
        assertThat(result.sectionType()).isEqualTo(SectionType.LEGAL_BRIEF);
        assertThat(result.topic()).isEqualTo("Legal & Regulatory Brief");
    }

    @Test
    @DisplayName("build() headline contains source counts")
    void build_headlineContainsCounts() {
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Legal"), anyInt()))
                .thenReturn(List.of(LEGAL_ARTICLE));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Government Policy"), anyInt()))
                .thenReturn(List.of(POLICY_ARTICLE));
        when(regulatoryUseCase.getRecentEvents(anyInt()))
                .thenReturn(List.of(REG_EVENT));

        NewsletterSection result = builder.build("section-010", 7);

        assertThat(result).isNotNull();
        assertThat(result.headline()).contains("1 litigation update");
        assertThat(result.headline()).contains("1 policy development");
        assertThat(result.headline()).contains("1 regulatory event");
    }

    @Test
    @DisplayName("build() article IDs contain source IDs from all sources")
    void build_articleIdsContainsSourceIds() {
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Legal"), anyInt()))
                .thenReturn(List.of(LEGAL_ARTICLE));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Government Policy"), anyInt()))
                .thenReturn(List.of(POLICY_ARTICLE));
        when(regulatoryUseCase.getRecentEvents(anyInt()))
                .thenReturn(List.of(REG_EVENT));

        NewsletterSection result = builder.build("section-010", 7);

        assertThat(result).isNotNull();
        assertThat(result.articleIds()).contains("legal-001", "policy-001", "reg-001");
    }

    @Test
    @DisplayName("build() with mixed sources combines all into summary")
    void build_mixedSources_combinesAll() {
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Legal"), anyInt()))
                .thenReturn(List.of(LEGAL_ARTICLE));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Government Policy"), anyInt()))
                .thenReturn(List.of(POLICY_ARTICLE));
        when(regulatoryUseCase.getRecentEvents(anyInt()))
                .thenReturn(List.of(REG_EVENT));

        NewsletterSection result = builder.build("section-010", 7);

        assertThat(result).isNotNull();
        assertThat(result.summary()).contains("Litigation:");
        assertThat(result.summary()).contains("Policy:");
        assertThat(result.summary()).contains("Regulatory:");
    }

    @Test
    @DisplayName("build() uses provided section ID")
    void build_usesProvidedSectionId() {
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Legal"), anyInt()))
                .thenReturn(List.of(LEGAL_ARTICLE));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(eq("AI Healthcare Government Policy"), anyInt()))
                .thenReturn(Collections.emptyList());
        when(regulatoryUseCase.getRecentEvents(anyInt()))
                .thenReturn(Collections.emptyList());

        NewsletterSection result = builder.build("section-042", 7);

        assertThat(result).isNotNull();
        assertThat(result.sectionId()).isEqualTo("section-042");
    }
}
