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
 * @updated 2026-07-20
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

        NewsletterRun result = renderer.buildDigest();

        assertThat(result.htmlContent()).contains("AI Healthcare Daily Digest");
        assertThat(result.htmlContent()).contains("Today's Articles");
        assertThat(result.htmlContent()).contains("Upgrade to Subscriber");
        assertThat(result.plainTextContent()).isEqualTo("Today's articles in plain text");
        assertThat(result.status()).isEqualTo(NewsletterRunStatus.DRAFT);
    }

    @Test
    void buildDigest_withSummaryFile_containsCtaFooter() {
        when(dailySummaryPort.getHtmlSummary(any(LocalDate.class)))
                .thenReturn(Optional.of("<p>Summary</p>"));
        when(dailySummaryPort.getTextSummary(any(LocalDate.class)))
                .thenReturn(Optional.of("Summary"));

        NewsletterRun result = renderer.buildDigest();

        assertThat(result.htmlContent()).contains("Want deeper AI analysis?");
        assertThat(result.htmlContent()).contains("$39/mo");
        assertThat(result.htmlContent()).contains("pricing");
    }

    @Test
    void buildDigest_noSummaryFile_returnsFallbackContent() {
        when(dailySummaryPort.getHtmlSummary(any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(dailySummaryPort.getTextSummary(any(LocalDate.class)))
                .thenReturn(Optional.empty());

        NewsletterRun result = renderer.buildDigest();

        assertThat(result.htmlContent()).contains("No articles are available");
        assertThat(result.plainTextContent()).contains("No articles available");
    }

    @Test
    void buildDigest_runIdContainsDate() {
        when(dailySummaryPort.getHtmlSummary(any(LocalDate.class)))
                .thenReturn(Optional.of("<p>Content</p>"));
        when(dailySummaryPort.getTextSummary(any(LocalDate.class)))
                .thenReturn(Optional.of("Content"));

        NewsletterRun result = renderer.buildDigest();

        assertThat(result.runId()).startsWith("digest-");
        assertThat(result.title()).contains("Daily Digest");
    }

    @Test
    void buildDigest_htmlSummaryPresent_textMissing_usesFallbackText() {
        when(dailySummaryPort.getHtmlSummary(any(LocalDate.class)))
                .thenReturn(Optional.of("<p>HTML content</p>"));
        when(dailySummaryPort.getTextSummary(any(LocalDate.class)))
                .thenReturn(Optional.empty());

        NewsletterRun result = renderer.buildDigest();

        assertThat(result.htmlContent()).contains("HTML content");
        assertThat(result.plainTextContent()).contains("View in a browser");
    }
}
