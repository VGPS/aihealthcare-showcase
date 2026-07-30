package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.ExtractedTrend;
import com.wgblackmon.aihealthcare.domain.model.LegalTrendSignal;
import com.wgblackmon.aihealthcare.domain.model.LegalTrendSnapshot;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryBody;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEventType;
import com.wgblackmon.aihealthcare.domain.model.TrendDirection;
import com.wgblackmon.aihealthcare.domain.port.inbound.MonitorRegulatoryEventsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.LegalTrendSnapshotPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendSummaryPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendTopicExtractionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LegalTrendDetectionService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-07-30
 */
@ExtendWith(MockitoExtension.class)
class LegalTrendDetectionServiceTest {

    @Mock private ArticleIngestionPort articleIngestionPort;
    @Mock private MonitorRegulatoryEventsUseCase regulatoryUseCase;
    @Mock private TrendTopicExtractionPort trendTopicExtractionPort;
    @Mock private LegalTrendSnapshotPort legalTrendSnapshotPort;
    @Mock private TrendSummaryPort trendSummaryPort;

    private LegalTrendDetectionService service;

    private static final Instant NOW = Instant.now();

    private static NewsArticle legalArticle(String id, String title) {
        return new NewsArticle(id, title, URI.create("https://example.com/" + id),
                "body", "AI Healthcare Legal", null, 1L, "Legal Source",
                "INDUSTRY", 0.8, NOW.minus(5, ChronoUnit.DAYS));
    }

    private static NewsArticle policyArticle(String id, String title) {
        return new NewsArticle(id, title, URI.create("https://example.com/" + id),
                "body", "AI Healthcare Government Policy", null, 2L, "Policy Source",
                "REGULATORY", 0.9, NOW.minus(5, ChronoUnit.DAYS));
    }

    private static RegulatoryEvent regulatoryEvent(String id, String title) {
        return new RegulatoryEvent(id, RegulatoryEventType.FDA_510K_CLEARANCE,
                RegulatoryBody.FDA, title, null, "K241234", "Company",
                "AI Device", "https://fda.gov/" + id, null,
                NOW.minus(3, ChronoUnit.DAYS), NOW.minus(2, ChronoUnit.DAYS),
                List.of("ai", "healthcare"),
                null, null, null, null);
    }

    @BeforeEach
    void setUp() {
        service = new LegalTrendDetectionService(
                articleIngestionPort, regulatoryUseCase, trendTopicExtractionPort,
                legalTrendSnapshotPort, trendSummaryPort, 15);
    }

    @Test
    @DisplayName("detectLegalTrends with articles returns snapshot")
    void detectTrends_withLegalArticles_returnsSnapshot() {
        NewsArticle legal = legalArticle("a1", "HIPAA AI Compliance Lawsuits Rise");
        NewsArticle policy = policyArticle("a2", "CMS AI Reimbursement Rule Proposed");

        when(articleIngestionPort.fetchByTopicWithArchiveLimit("AI Healthcare Legal", 30))
                .thenReturn(List.of(legal));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit("AI Healthcare Government Policy", 30))
                .thenReturn(List.of(policy));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit("AI Healthcare Legal", 90))
                .thenReturn(List.of(legal));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit("AI Healthcare Government Policy", 90))
                .thenReturn(List.of(policy));
        when(regulatoryUseCase.getRecentEvents(50)).thenReturn(List.of());

        ExtractedTrend trend = new ExtractedTrend("HIPAA Compliance", "AI compliance issues",
                List.of(0));
        when(trendTopicExtractionPort.extractTopics(anyList(), anyInt()))
                .thenReturn(List.of(trend));

        LegalTrendSnapshot result = service.detectLegalTrends();

        assertThat(result).isNotNull();
        assertThat(result.windowDays()).isEqualTo(30);
        verify(legalTrendSnapshotPort).save(any(LegalTrendSnapshot.class));
    }

    @Test
    @DisplayName("detectLegalTrends includes regulatory events")
    void detectTrends_includesRegulatoryEvents() {
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(anyString(), anyInt()))
                .thenReturn(List.of());
        RegulatoryEvent event = regulatoryEvent("evt1", "AI Device Clearance");
        when(regulatoryUseCase.getRecentEvents(50)).thenReturn(List.of(event));

        ExtractedTrend trend = new ExtractedTrend("AI Device Clearance", "FDA clearances",
                List.of(0));
        when(trendTopicExtractionPort.extractTopics(anyList(), anyInt()))
                .thenReturn(List.of(trend));

        LegalTrendSnapshot result = service.detectLegalTrends();

        assertThat(result).isNotNull();
        verify(legalTrendSnapshotPort).save(any(LegalTrendSnapshot.class));
    }

    @Test
    @DisplayName("detectLegalTrends computes momentum for rising themes")
    void detectTrends_computesMomentum() {
        NewsArticle legal = legalArticle("a1", "HIPAA AI Lawsuits");
        when(articleIngestionPort.fetchByTopicWithArchiveLimit("AI Healthcare Legal", 30))
                .thenReturn(List.of(legal));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit("AI Healthcare Government Policy", 30))
                .thenReturn(List.of());
        when(articleIngestionPort.fetchByTopicWithArchiveLimit("AI Healthcare Legal", 90))
                .thenReturn(List.of(legal));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit("AI Healthcare Government Policy", 90))
                .thenReturn(List.of());
        when(regulatoryUseCase.getRecentEvents(50)).thenReturn(List.of());

        // Same label in both windows means momentum computation
        ExtractedTrend trend = new ExtractedTrend("HIPAA", "HIPAA enforcement", List.of(0));
        when(trendTopicExtractionPort.extractTopics(anyList(), anyInt()))
                .thenReturn(List.of(trend));

        LegalTrendSnapshot result = service.detectLegalTrends();

        assertThat(result).isNotNull();
        verify(legalTrendSnapshotPort).save(any(LegalTrendSnapshot.class));
    }

    @Test
    @DisplayName("detectLegalTrends categorizes signals by source")
    void detectTrends_categorizesBySource() {
        NewsArticle legal = legalArticle("a1", "Lawsuit Filed");
        when(articleIngestionPort.fetchByTopicWithArchiveLimit("AI Healthcare Legal", 30))
                .thenReturn(List.of(legal));
        when(articleIngestionPort.fetchByTopicWithArchiveLimit("AI Healthcare Government Policy", 30))
                .thenReturn(List.of());
        when(articleIngestionPort.fetchByTopicWithArchiveLimit("AI Healthcare Legal", 90))
                .thenReturn(List.of());
        when(articleIngestionPort.fetchByTopicWithArchiveLimit("AI Healthcare Government Policy", 90))
                .thenReturn(List.of());
        when(regulatoryUseCase.getRecentEvents(50)).thenReturn(List.of());

        ExtractedTrend trend = new ExtractedTrend("Lawsuits", "AI lawsuits", List.of(0));
        when(trendTopicExtractionPort.extractTopics(anyList(), anyInt()))
                .thenReturn(List.of(trend));

        LegalTrendSnapshot result = service.detectLegalTrends();

        assertThat(result.risingTrends()).isNotEmpty();
        // The first article is "AI Healthcare Legal" → categorized as LITIGATION
        assertThat(result.risingTrends().get(0).category()).isEqualTo("LITIGATION");
    }

    @Test
    @DisplayName("detectLegalTrends with no articles returns empty snapshot")
    void detectTrends_noArticles_returnsEmpty() {
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(anyString(), anyInt()))
                .thenReturn(List.of());
        when(regulatoryUseCase.getRecentEvents(50)).thenReturn(List.of());

        LegalTrendSnapshot result = service.detectLegalTrends();

        assertThat(result.risingTrends()).isEmpty();
        assertThat(result.totalKeywords()).isEqualTo(0);
        verify(legalTrendSnapshotPort).save(any(LegalTrendSnapshot.class));
    }

    @Test
    @DisplayName("detectLegalTrends saves snapshot")
    void detectTrends_savesSnapshot() {
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(anyString(), anyInt()))
                .thenReturn(List.of());
        when(regulatoryUseCase.getRecentEvents(50)).thenReturn(List.of());

        service.detectLegalTrends();

        ArgumentCaptor<LegalTrendSnapshot> captor = ArgumentCaptor.forClass(LegalTrendSnapshot.class);
        verify(legalTrendSnapshotPort).save(captor.capture());
        assertThat(captor.getValue().windowDays()).isEqualTo(30);
    }

    @Test
    @DisplayName("getLatestSnapshot delegates to port")
    void getLatestSnapshot_delegatesToPort() {
        LegalTrendSnapshot snapshot = new LegalTrendSnapshot(
                Instant.now(), 30, List.of(), 10);
        when(legalTrendSnapshotPort.findLatest()).thenReturn(Optional.of(snapshot));

        Optional<LegalTrendSnapshot> result = service.getLatestSnapshot();

        assertThat(result).isPresent();
        assertThat(result.get().totalKeywords()).isEqualTo(10);
        verify(legalTrendSnapshotPort).findLatest();
    }

    @Test
    @DisplayName("detectLegalTrends skips summaries when port unavailable")
    void detectTrends_skipsSummariesWhenUnavailable() {
        when(articleIngestionPort.fetchByTopicWithArchiveLimit(anyString(), anyInt()))
                .thenReturn(List.of());
        when(regulatoryUseCase.getRecentEvents(50)).thenReturn(List.of());
        when(trendSummaryPort.isAvailable()).thenReturn(false);

        service.detectLegalTrends();

        verify(trendSummaryPort, never()).generateSummary(anyString(), anyList());
    }
}
