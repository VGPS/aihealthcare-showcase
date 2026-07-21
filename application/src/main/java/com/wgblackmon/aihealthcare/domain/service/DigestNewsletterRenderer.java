package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRunStatus;
import com.wgblackmon.aihealthcare.domain.port.outbound.DailySummaryPort;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Renders the FREE-tier daily article digest newsletter by wrapping the
 * NotebookLM-generated HTML summary in an email-safe layout with a
 * "Subscribe for AI analysis" CTA footer.
 *
 * <p>If no summary file exists for the current date, a minimal fallback
 * message is generated instead.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-20
 * @updated 2026-07-20
 */
@Slf4j
public class DigestNewsletterRenderer {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy");

    private final DailySummaryPort dailySummaryPort;

    public DigestNewsletterRenderer(DailySummaryPort dailySummaryPort) {
        log.debug("DigestNewsletterRenderer() | dailySummaryPort={}", dailySummaryPort.getClass().getSimpleName());
        this.dailySummaryPort = dailySummaryPort;
    }

    /**
     * Builds a digest newsletter run for FREE-tier subscribers.
     *
     * <p>Reads today's HTML summary from the daily summary port, wraps it
     * in an email-safe layout, and returns a synthetic {@link NewsletterRun}
     * suitable for delivery.
     *
     * @return A newsletter run containing the digest HTML and plain-text content.
     */
    public NewsletterRun buildDigest() {
        log.debug("buildDigest() | (no args)");

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String dateDisplay = today.format(DISPLAY_FMT);

        Optional<String> htmlOpt = dailySummaryPort.getHtmlSummary(today);
        Optional<String> textOpt = dailySummaryPort.getTextSummary(today);

        String bodyHtml;
        String bodyText;

        if (htmlOpt.isPresent()) {
            bodyHtml = htmlOpt.get();
            bodyText = textOpt.orElse("Today's AI Healthcare article digest. View in a browser for best experience.");
        } else {
            log.info("buildDigest() | No summary file for {} — using fallback", today);
            bodyHtml = buildFallbackHtml(dateDisplay);
            bodyText = "No articles available for " + dateDisplay + ". Check back tomorrow for the latest AI healthcare news.";
        }

        String wrappedHtml = wrapInEmailLayout(bodyHtml, dateDisplay);

        NewsletterRun result = new NewsletterRun(
                "digest-" + today.format(DateTimeFormatter.ISO_LOCAL_DATE),
                "AI Healthcare Daily Digest — " + dateDisplay,
                today,
                wrappedHtml,
                bodyText,
                NewsletterRunStatus.DRAFT,
                Instant.now()
        );

        log.debug("buildDigest() | return=NewsletterRun[runId={}]", result.runId());
        return result;
    }

    private String wrapInEmailLayout(String bodyHtml, String dateDisplay) {
        log.debug("wrapInEmailLayout() | bodyLength={}, date={}", bodyHtml.length(), dateDisplay);

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"en\">\n<head>\n");
        sb.append("  <meta charset=\"UTF-8\">\n");
        sb.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("  <title>AI Healthcare Daily Digest</title>\n");
        sb.append("</head>\n<body style=\"margin:0; padding:0; background:#f4f6f9; font-family:Arial,sans-serif;\">\n");
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f4f6f9;\">\n");
        sb.append("<tr><td align=\"center\" style=\"padding:20px 10px;\">\n");
        sb.append("<table width=\"640\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:640px; width:100%;\">\n");

        // Header
        sb.append("<tr><td style=\"background:#1a1a2e; color:white; padding:20px 24px; border-radius:8px 8px 0 0;\">\n");
        sb.append("  <h1 style=\"margin:0; font-size:1.3em;\">AI Healthcare Daily Digest</h1>\n");
        sb.append("  <p style=\"margin:4px 0 0; font-size:0.85em; opacity:0.7;\">").append(dateDisplay).append("</p>\n");
        sb.append("</td></tr>\n");

        // Body content
        sb.append("<tr><td style=\"background:white; padding:24px;\">\n");
        sb.append(bodyHtml);
        sb.append("\n</td></tr>\n");

        // CTA footer
        sb.append("<tr><td style=\"background:#f0f7ff; padding:20px 24px; border-top:2px solid #0066cc;\">\n");
        sb.append("  <h3 style=\"margin:0 0 8px; color:#1a1a2e; font-size:1em;\">Want deeper AI analysis?</h3>\n");
        sb.append("  <p style=\"margin:0 0 12px; font-size:0.9em; color:#555;\">\n");
        sb.append("    Subscribe for full AI-powered newsletters with expert synthesis, vendor comparisons, and research insights.\n");
        sb.append("  </p>\n");
        sb.append("  <a href=\"https://app.bigskylabs.ai/pricing\" style=\"display:inline-block; background:#0066cc; color:white; ");
        sb.append("padding:10px 24px; border-radius:6px; text-decoration:none; font-weight:600; font-size:0.9em;\">Upgrade to Subscriber — $39/mo</a>\n");
        sb.append("</td></tr>\n");

        // Footer
        sb.append("<tr><td style=\"padding:16px 24px; text-align:center; font-size:0.75em; color:#999; border-radius:0 0 8px 8px;\">\n");
        sb.append("  You are receiving this because you signed up for the free AI Healthcare digest.\n");
        sb.append("</td></tr>\n");

        sb.append("</table>\n</td></tr>\n</table>\n</body>\n</html>\n");

        String result = sb.toString();
        log.debug("wrapInEmailLayout() | return={} chars", result.length());
        return result;
    }

    private String buildFallbackHtml(String dateDisplay) {
        log.debug("buildFallbackHtml() | date={}", dateDisplay);
        String result = "<p style=\"font-size:0.95em; color:#333; line-height:1.6;\">"
                + "No articles are available for " + dateDisplay + ". "
                + "Check back tomorrow for the latest AI healthcare news and research.</p>";
        log.debug("buildFallbackHtml() | return={} chars", result.length());
        return result;
    }
}
