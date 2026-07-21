package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds a truncated "teaser" version of a full {@link NewsletterRun} for FREE-tier subscribers.
 *
 * <p>The teaser retains the newsletter title, week-of date, and the first section of content,
 * then appends a styled call-to-action (CTA) block inviting the reader to upgrade to Subscriber
 * for the full newsletter.  This encourages conversion while still providing value to free
 * subscribers.
 *
 * <p>The CTA wording adapts to the configured article look-back window: "today's" when
 * {@code daysBack} is 1, "this week's" when 7, or "the last N days'" otherwise.
 *
 * <p>This is a pure domain service with no Spring or framework dependencies.  It is
 * constructed in {@link com.wgblackmon.aihealthcare.infrastructure.config.AppConfig}
 * and injected into {@link DeliveryService} via constructor injection.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-24
 * @updated 2026-05-26
 */
@Slf4j
public class NewsletterTeaserBuilder {

    private final String ctaHtml;
    private final String ctaPlainText;

    /**
     * Creates a teaser builder whose CTA wording adapts to the article look-back window.
     *
     * @param daysBack the configured {@code aihealthcare.articles.days-back} value
     */
    public NewsletterTeaserBuilder(int daysBack) {
        log.debug("NewsletterTeaserBuilder() | daysBack={}", daysBack);

        String timeframe;
        if (daysBack <= 1) {
            timeframe = "today's";
        } else if (daysBack == 7) {
            timeframe = "this week's";
        } else {
            timeframe = "the last " + daysBack + " days'";
        }

        this.ctaHtml =
                "<div style=\"background: linear-gradient(135deg, #0066cc, #004499); " +
                "color: white; padding: 24px 32px; margin: 32px 0; border-radius: 8px; " +
                "text-align: center;\">" +
                "<h2 style=\"margin: 0 0 12px 0; color: white; font-size: 1.3em;\">" +
                "Want the full newsletter?</h2>" +
                "<p style=\"margin: 0 0 16px 0; font-size: 1.05em; line-height: 1.5;\">" +
                "This is a preview of " + timeframe + " AI in Healthcare newsletter. " +
                "Upgrade to <strong>Subscriber</strong> for full coverage including deep-dive analysis, " +
                "vendor comparisons, and research summaries.</p>" +
                "<p style=\"margin: 0; font-size: 0.9em; opacity: 0.85;\">" +
                "Contact us to upgrade your subscription.</p>" +
                "</div>";

        this.ctaPlainText =
                "\n---\n" +
                "WANT THE FULL NEWSLETTER?\n\n" +
                "This is a preview of " + timeframe + " AI in Healthcare newsletter. " +
                "Upgrade to Subscriber for full coverage including deep-dive analysis, " +
                "vendor comparisons, and research summaries.\n\n" +
                "Contact us to upgrade your subscription.\n";

        log.debug("NewsletterTeaserBuilder() | timeframe={}", timeframe);
    }

    /**
     * Creates a teaser version of the given newsletter run.
     *
     * <p>The HTML content is truncated after the first {@code </div>} block following the
     * intro paragraph (i.e. the first newsletter section), then the CTA is appended.
     * The plain-text content is truncated after the first section separator ({@code ---}).
     *
     * @param fullRun the complete newsletter run; must not be {@code null}
     * @return a new {@link NewsletterRun} with truncated content and the same metadata
     */
    public NewsletterRun buildTeaser(NewsletterRun fullRun) {
        log.debug("buildTeaser() | runId={}", fullRun.runId());

        String teaserHtml = truncateHtml(fullRun.htmlContent());
        String teaserPlainText = truncatePlainText(fullRun.plainTextContent());

        NewsletterRun result = new NewsletterRun(
                fullRun.runId(),
                fullRun.title(),
                fullRun.weekOf(),
                teaserHtml,
                teaserPlainText,
                fullRun.status(),
                fullRun.generatedAt()
        );

        log.debug("buildTeaser() | return=NewsletterRun[html={} chars, text={} chars]",
                  teaserHtml.length(), teaserPlainText.length());
        return result;
    }

    /**
     * Truncates HTML after the first section {@code </div>} and appends the CTA.
     */
    private String truncateHtml(String html) {
        log.debug("truncateHtml() | inputLength={}", html.length());

        int firstSectionStart = html.indexOf("<div style=\"border-left:");
        if (firstSectionStart < 0) {
            String result = insertBeforeBodyClose(html, ctaHtml);
            log.debug("truncateHtml() | return=html[{} chars] (no sections found)", result.length());
            return result;
        }

        int closingDiv = html.indexOf("</div>", firstSectionStart);
        if (closingDiv < 0) {
            String result = insertBeforeBodyClose(html, ctaHtml);
            log.debug("truncateHtml() | return=html[{} chars] (no closing div)", result.length());
            return result;
        }

        String truncated = html.substring(0, closingDiv + "</div>".length());
        String result = truncated + ctaHtml + "</body></html>";

        log.debug("truncateHtml() | return=html[{} chars]", result.length());
        return result;
    }

    /**
     * Truncates plain text after the first section and appends the CTA.
     */
    private String truncatePlainText(String text) {
        log.debug("truncatePlainText() | inputLength={}", text.length());

        int firstSeparator = text.indexOf("---\n");
        if (firstSeparator < 0) {
            String result = text + ctaPlainText;
            log.debug("truncatePlainText() | return=text[{} chars] (no separator)", result.length());
            return result;
        }

        int secondSeparator = text.indexOf("---\n", firstSeparator + 4);
        if (secondSeparator < 0) {
            String result = text + ctaPlainText;
            log.debug("truncatePlainText() | return=text[{} chars] (one section)", result.length());
            return result;
        }

        String result = text.substring(0, secondSeparator) + ctaPlainText;

        log.debug("truncatePlainText() | return=text[{} chars]", result.length());
        return result;
    }

    /**
     * Inserts content before the {@code </body>} tag, or appends if no body tag found.
     */
    private String insertBeforeBodyClose(String html, String content) {
        int bodyClose = html.indexOf("</body>");
        if (bodyClose >= 0) {
            return html.substring(0, bodyClose) + content + html.substring(bodyClose);
        }
        return html + content;
    }
}
