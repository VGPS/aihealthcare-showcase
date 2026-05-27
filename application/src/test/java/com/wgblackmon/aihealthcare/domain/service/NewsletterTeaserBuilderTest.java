package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRunStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link NewsletterTeaserBuilder}.
 *
 * <p>Verifies that teaser generation truncates content after the first section
 * and appends a call-to-action block for both HTML and plain-text formats.
 * Also verifies that the CTA wording adapts to the configured days-back value.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-24
 * @updated 2026-05-24
 */
class NewsletterTeaserBuilderTest {

    private final NewsletterTeaserBuilder dailyBuilder = new NewsletterTeaserBuilder(1);
    private final NewsletterTeaserBuilder weeklyBuilder = new NewsletterTeaserBuilder(7);
    private final NewsletterTeaserBuilder customBuilder = new NewsletterTeaserBuilder(3);

    private static final String MULTI_SECTION_HTML =
            "<h1 style=\"color: #0066cc;\">Title</h1>" +
            "<p style=\"font-size: 1.05em;\">Introduction paragraph.</p>" +
            "<div style=\"border-left: 4px solid #0066cc; padding: 10px 20px;\">" +
            "<h2>Section 1</h2><p>First section content.</p></div>" +
            "<div style=\"border-left: 4px solid #0066cc; padding: 10px 20px;\">" +
            "<h2>Section 2</h2><p>Second section content.</p></div>" +
            "<hr><h3>Sources</h3><ul><li>Source 1</li></ul>" +
            "</body></html>";

    private static final String MULTI_SECTION_TEXT =
            "AI in Healthcare Weekly\n" +
            "Week of 2026-05-24\n" +
            "============================================================\n\n" +
            "Introduction paragraph.\n\n" +
            "---\n" +
            "Section 1 Headline\n\nFirst section content.\n\n" +
            "---\n" +
            "Section 2 Headline\n\nSecond section content.\n\n" +
            "---\nSOURCES\n\n- Source 1: http://example.com\n";

    private NewsletterRun createRun(String html, String text) {
        return new NewsletterRun(
                "run-teaser-test",
                "AI in Healthcare Weekly",
                LocalDate.of(2026, 5, 24),
                html,
                text,
                NewsletterRunStatus.DRAFT,
                Instant.parse("2026-05-24T08:00:00Z")
        );
    }

    @Test
    void buildTeaser_htmlTruncatesAfterFirstSection() {
        NewsletterRun fullRun = createRun(MULTI_SECTION_HTML, MULTI_SECTION_TEXT);

        NewsletterRun teaser = dailyBuilder.buildTeaser(fullRun);

        assertThat(teaser.htmlContent()).contains("Section 1");
        assertThat(teaser.htmlContent()).doesNotContain("Section 2");
        assertThat(teaser.htmlContent()).doesNotContain("Sources");
    }

    @Test
    void buildTeaser_htmlContainsCta() {
        NewsletterRun fullRun = createRun(MULTI_SECTION_HTML, MULTI_SECTION_TEXT);

        NewsletterRun teaser = dailyBuilder.buildTeaser(fullRun);

        assertThat(teaser.htmlContent()).contains("Want the full newsletter?");
        assertThat(teaser.htmlContent()).contains("Upgrade to");
        assertThat(teaser.htmlContent()).contains("Member");
    }

    @Test
    void buildTeaser_plainTextTruncatesAfterFirstSection() {
        NewsletterRun fullRun = createRun(MULTI_SECTION_HTML, MULTI_SECTION_TEXT);

        NewsletterRun teaser = dailyBuilder.buildTeaser(fullRun);

        assertThat(teaser.plainTextContent()).contains("Section 1 Headline");
        assertThat(teaser.plainTextContent()).doesNotContain("Section 2 Headline");
        assertThat(teaser.plainTextContent()).doesNotContain("SOURCES");
    }

    @Test
    void buildTeaser_plainTextContainsCta() {
        NewsletterRun fullRun = createRun(MULTI_SECTION_HTML, MULTI_SECTION_TEXT);

        NewsletterRun teaser = dailyBuilder.buildTeaser(fullRun);

        assertThat(teaser.plainTextContent()).contains("WANT THE FULL NEWSLETTER?");
        assertThat(teaser.plainTextContent()).contains("Upgrade to Member");
    }

    @Test
    void buildTeaser_preservesMetadata() {
        NewsletterRun fullRun = createRun(MULTI_SECTION_HTML, MULTI_SECTION_TEXT);

        NewsletterRun teaser = dailyBuilder.buildTeaser(fullRun);

        assertThat(teaser.runId()).isEqualTo(fullRun.runId());
        assertThat(teaser.title()).isEqualTo(fullRun.title());
        assertThat(teaser.weekOf()).isEqualTo(fullRun.weekOf());
        assertThat(teaser.status()).isEqualTo(fullRun.status());
        assertThat(teaser.generatedAt()).isEqualTo(fullRun.generatedAt());
    }

    @Test
    void buildTeaser_singleSectionHtml_keepsItAndAddsCta() {
        String singleSectionHtml =
                "<h1>Title</h1><p>Intro</p>" +
                "<div style=\"border-left: 4px solid #0066cc;\"><h2>Only Section</h2><p>Content.</p></div>" +
                "</body></html>";
        String singleSectionText = "Title\nWeek of 2026-05-24\n===\n\n---\nOnly Section\n\nContent.\n\n";

        NewsletterRun fullRun = createRun(singleSectionHtml, singleSectionText);

        NewsletterRun teaser = dailyBuilder.buildTeaser(fullRun);

        assertThat(teaser.htmlContent()).contains("Only Section");
        assertThat(teaser.htmlContent()).contains("Want the full newsletter?");
    }

    @Test
    void buildTeaser_noSections_appendsCtaBeforeBodyClose() {
        String noSectionHtml = "<html><body><h1>Title</h1><p>Just an intro.</p></body></html>";
        String noSectionText = "Title\nJust an intro.\n";

        NewsletterRun fullRun = createRun(noSectionHtml, noSectionText);

        NewsletterRun teaser = dailyBuilder.buildTeaser(fullRun);

        assertThat(teaser.htmlContent()).contains("Just an intro.");
        assertThat(teaser.htmlContent()).contains("Want the full newsletter?");
        assertThat(teaser.plainTextContent()).contains("WANT THE FULL NEWSLETTER?");
    }

    // -------------------------------------------------------------------------
    // Dynamic timeframe wording based on days-back
    // -------------------------------------------------------------------------

    @Test
    void buildTeaser_daysBack1_usesTodaysWording() {
        NewsletterRun fullRun = createRun(MULTI_SECTION_HTML, MULTI_SECTION_TEXT);

        NewsletterRun teaser = dailyBuilder.buildTeaser(fullRun);

        assertThat(teaser.htmlContent()).contains("today's AI in Healthcare newsletter");
        assertThat(teaser.plainTextContent()).contains("today's AI in Healthcare newsletter");
    }

    @Test
    void buildTeaser_daysBack7_usesThisWeeksWording() {
        NewsletterRun fullRun = createRun(MULTI_SECTION_HTML, MULTI_SECTION_TEXT);

        NewsletterRun teaser = weeklyBuilder.buildTeaser(fullRun);

        assertThat(teaser.htmlContent()).contains("this week's AI in Healthcare newsletter");
        assertThat(teaser.plainTextContent()).contains("this week's AI in Healthcare newsletter");
    }

    @Test
    void buildTeaser_daysBack3_usesLastNDaysWording() {
        NewsletterRun fullRun = createRun(MULTI_SECTION_HTML, MULTI_SECTION_TEXT);

        NewsletterRun teaser = customBuilder.buildTeaser(fullRun);

        assertThat(teaser.htmlContent()).contains("the last 3 days' AI in Healthcare newsletter");
        assertThat(teaser.plainTextContent()).contains("the last 3 days' AI in Healthcare newsletter");
    }
}
