package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRunStatus;
import com.wgblackmon.aihealthcare.domain.port.outbound.DailySummaryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DigestNewsletterRenderer}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-20
 * @updated 2026-07-21
 */
class DigestNewsletterRendererTest {

    private DailySummaryPort dailySummaryPort;
    private DigestNewsletterRenderer renderer;

    @BeforeEach
    void setUp() {
        dailySummaryPort = mock(DailySummaryPort.class);
        renderer = new DigestNewsletterRenderer(dailySummaryPort);
    }

    @Test
    void buildDigest_withSummaryFile_wrapsInEmailLayout() {
        when(dailySummaryPort.getHtmlSummary(any(LocalDate.class)))
                .thenReturn(Optional.of("<h2>Today's Articles</h2><p>Content here</p>"));
        when(dailySummaryPort.getTextSummary(any(LocalDate.class)))
                .thenReturn(Optional.of("Today's articles in plain text"));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        assertThat(result.get().htmlContent()).contains("News Articles From");
        assertThat(result.get().htmlContent()).contains("Today's Articles");
        assertThat(result.get().htmlContent()).contains("Upgrade to Subscriber");
        assertThat(result.get().plainTextContent()).isEqualTo("Today's articles in plain text");
        assertThat(result.get().status()).isEqualTo(NewsletterRunStatus.DRAFT);
    }

    @Test
    void buildDigest_withSummaryFile_containsCtaFooter() {
        when(dailySummaryPort.getHtmlSummary(any(LocalDate.class)))
                .thenReturn(Optional.of("<p>Summary</p>"));
        when(dailySummaryPort.getTextSummary(any(LocalDate.class)))
                .thenReturn(Optional.of("Summary"));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        assertThat(result.get().htmlContent()).contains("Want deeper AI analysis?");
        assertThat(result.get().htmlContent()).contains("$39/mo");
        assertThat(result.get().htmlContent()).contains("pricing");
        assertThat(result.get().htmlContent()).contains("Free 7 Day Demo");
        assertThat(result.get().htmlContent()).contains("app.bigskylabs.ai/demo");
        assertThat(result.get().htmlContent()).contains("Unsubscribe");
        assertThat(result.get().htmlContent()).contains("app.bigskylabs.ai/unsubscribe");
    }

    @Test
    void buildDigest_noSummaryToday_fallsBackToMostRecent() {
        LocalDate recentDate = LocalDate.of(2026, 7, 19);
        when(dailySummaryPort.getHtmlSummary(any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(dailySummaryPort.findMostRecentSummaryDate())
                .thenReturn(Optional.of(recentDate));
        when(dailySummaryPort.getHtmlSummary(recentDate))
                .thenReturn(Optional.of("<p>Saturday's articles</p>"));
        when(dailySummaryPort.getTextSummary(recentDate))
                .thenReturn(Optional.of("Saturday's articles in text"));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        assertThat(result.get().htmlContent()).contains("No new articles found");
        assertThat(result.get().htmlContent()).contains("July 19, 2026");
        assertThat(result.get().htmlContent()).contains("Saturday's articles");
        assertThat(result.get().plainTextContent()).contains("No new articles found");
        assertThat(result.get().plainTextContent()).contains("July 19, 2026");
        assertThat(result.get().plainTextContent()).contains("Saturday's articles in text");
    }

    @Test
    void buildDigest_noSummaryAtAll_returnsEmpty() {
        when(dailySummaryPort.getHtmlSummary(any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(dailySummaryPort.findMostRecentSummaryDate())
                .thenReturn(Optional.empty());

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isEmpty();
    }

    @Test
    void buildDigest_runIdContainsDate() {
        when(dailySummaryPort.getHtmlSummary(any(LocalDate.class)))
                .thenReturn(Optional.of("<p>Content</p>"));
        when(dailySummaryPort.getTextSummary(any(LocalDate.class)))
                .thenReturn(Optional.of("Content"));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        assertThat(result.get().runId()).startsWith("digest-");
        assertThat(result.get().title()).contains("News Articles From");
    }

    @Test
    void buildDigest_htmlSummaryPresent_textMissing_usesFallbackText() {
        when(dailySummaryPort.getHtmlSummary(any(LocalDate.class)))
                .thenReturn(Optional.of("<p>HTML content</p>"));
        when(dailySummaryPort.getTextSummary(any(LocalDate.class)))
                .thenReturn(Optional.empty());

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        assertThat(result.get().htmlContent()).contains("HTML content");
        assertThat(result.get().plainTextContent()).contains("View in a browser");
    }
}
