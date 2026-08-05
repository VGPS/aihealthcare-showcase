package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.DealSignal;
import com.wgblackmon.aihealthcare.domain.model.DealSignalType;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DealSignalPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DealSignalDetectionService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@ExtendWith(MockitoExtension.class)
class DealSignalDetectionServiceTest {

    @Mock
    private ArticleIngestionPort articleIngestionPort;

    @Mock
    private DealSignalPort dealSignalPort;

    private DealSignalDetectionService service;

    @BeforeEach
    void setUp() {
        service = new DealSignalDetectionService(articleIngestionPort, dealSignalPort);
    }

    private NewsArticle article(String id, String title, String body) {
        return new NewsArticle(id, title, URI.create("https://example.com/" + id),
                body, "Test Topic", null, null, "Source", "INDUSTRY", 0.5, Instant.now());
    }

    @Test
    void detectSignals_fundingArticle_detectsFunding() {
        NewsArticle fundingArticle = article("a1",
                "Tempus AI raises $200 million in Series D funding round",
                "Tempus AI has raised $200 million in a Series D funding round led by venture capital firm...");
        when(articleIngestionPort.fetchRecentArticles(7)).thenReturn(List.of(fundingArticle));
        when(dealSignalPort.existsByArticleId("a1")).thenReturn(false);

        List<DealSignal> result = service.detectSignals();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).signalType()).isEqualTo(DealSignalType.FUNDING);
        assertThat(result.get(0).confidence()).isGreaterThanOrEqualTo(0.5);
    }

    @Test
    void detectSignals_acquisitionArticle_detectsAcquisition() {
        NewsArticle acqArticle = article("a2",
                "Oracle acquires health AI startup in major takeover deal",
                "Oracle has acquired the health AI company in a takeover valued at...");
        when(articleIngestionPort.fetchRecentArticles(7)).thenReturn(List.of(acqArticle));
        when(dealSignalPort.existsByArticleId("a2")).thenReturn(false);

        List<DealSignal> result = service.detectSignals();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).signalType()).isEqualTo(DealSignalType.ACQUISITION);
    }

    @Test
    void detectSignals_partnershipArticle_detectsPartnership() {
        NewsArticle partnerArticle = article("a3",
                "Google Health partners with Mayo Clinic in collaboration agreement",
                "Google Health and Mayo Clinic have entered a strategic alliance to jointly develop...");
        when(articleIngestionPort.fetchRecentArticles(7)).thenReturn(List.of(partnerArticle));
        when(dealSignalPort.existsByArticleId("a3")).thenReturn(false);

        List<DealSignal> result = service.detectSignals();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).signalType()).isEqualTo(DealSignalType.PARTNERSHIP);
    }

    @Test
    void detectSignals_noKeywords_noSignals() {
        NewsArticle boringArticle = article("a4",
                "Weekly healthcare news roundup",
                "This week in healthcare we review several developments...");
        when(articleIngestionPort.fetchRecentArticles(7)).thenReturn(List.of(boringArticle));
        when(dealSignalPort.existsByArticleId("a4")).thenReturn(false);

        List<DealSignal> result = service.detectSignals();

        assertThat(result).isEmpty();
        verify(dealSignalPort, never()).saveAll(any());
    }

    @Test
    void detectSignals_duplicateArticle_skips() {
        NewsArticle fundingArticle = article("a5",
                "Startup raises $50 million in funding round",
                "The startup raised capital from venture investors...");
        when(articleIngestionPort.fetchRecentArticles(7)).thenReturn(List.of(fundingArticle));
        when(dealSignalPort.existsByArticleId("a5")).thenReturn(true);

        List<DealSignal> result = service.detectSignals();

        assertThat(result).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void detectSignals_savesNewSignals() {
        NewsArticle fundingArticle = article("a6",
                "HealthTech raises $100 million Series B funding led by Sequoia",
                "HealthTech has raised $100 million in Series B investment...");
        when(articleIngestionPort.fetchRecentArticles(7)).thenReturn(List.of(fundingArticle));
        when(dealSignalPort.existsByArticleId("a6")).thenReturn(false);

        service.detectSignals();

        ArgumentCaptor<List<DealSignal>> captor = ArgumentCaptor.forClass(List.class);
        verify(dealSignalPort).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    @Test
    void getRecentSignals_delegatesToPort() {
        DealSignal signal = new DealSignal("s1", "a1", "Title",
                DealSignalType.FUNDING, "Company", "Summary", 0.8, Instant.now());
        when(dealSignalPort.findRecent(10)).thenReturn(List.of(signal));

        List<DealSignal> result = service.getRecentSignals(10);

        assertThat(result).hasSize(1);
        verify(dealSignalPort).findRecent(10);
    }
}
