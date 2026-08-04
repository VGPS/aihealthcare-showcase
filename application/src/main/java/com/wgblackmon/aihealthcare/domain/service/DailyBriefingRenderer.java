package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.AnalystNote;
import com.wgblackmon.aihealthcare.domain.model.CompanySentiment;
import com.wgblackmon.aihealthcare.domain.model.DailyBriefingData;
import com.wgblackmon.aihealthcare.domain.model.SentimentLabel;
import com.wgblackmon.aihealthcare.domain.model.WatchlistMatch;
import lombok.extern.slf4j.Slf4j;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Renders a {@link DailyBriefingData} into personalized HTML and plain-text
 * email content for one subscriber's daily briefing.
 *
 * <p>Pure domain service with no Spring annotations. Uses {@link StringBuilder}
 * and inline CSS for email-client compatibility, following the same 600px
 * table layout pattern as {@link NewsletterRenderer}.
 *
 * <p>Sections are conditionally omitted when their data list is empty.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Slf4j
public class DailyBriefingRenderer {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy")
                    .withZone(ZoneId.of("America/New_York"));

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("MMM d, h:mm a")
                    .withZone(ZoneId.of("America/New_York"));

    /**
     * Renders personalized HTML email content.
     */
    public String renderHtml(DailyBriefingData data) {
        log.debug("renderHtml() | email={}", data.subscriberEmail());

        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>")
            .append("<html lang=\"en\"><head>")
            .append("<meta charset=\"UTF-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
            .append("<title>Your Daily AI Healthcare Briefing</title>")
            .append("</head>")
            .append("<body style=\"margin: 0; padding: 0; background-color: #f5f6fa; ")
            .append("font-family: Arial, Helvetica, sans-serif; color: #333;\">");

        // Outer wrapper table
        html.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
            .append("style=\"background-color: #f5f6fa;\"><tr><td align=\"center\" ")
            .append("style=\"padding: 24px 16px;\">");

        // Inner content table
        html.append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" ")
            .append("style=\"max-width: 600px; width: 100%; background-color: #ffffff; ")
            .append("border-radius: 8px; overflow: hidden; ")
            .append("box-shadow: 0 2px 8px rgba(0,0,0,0.08);\">");

        // Header banner
        String displayName = data.subscriberName() != null && !data.subscriberName().isBlank()
                ? data.subscriberName() : "Subscriber";
        html.append("<tr><td style=\"background-color: #1a1a2e; padding: 28px 32px; ")
            .append("text-align: center;\">")
            .append("<h1 style=\"margin: 0; font-size: 20px; font-weight: 700; color: #ffffff; ")
            .append("letter-spacing: 0.5px;\">")
            .append("Your Daily AI Healthcare Briefing")
            .append("</h1>")
            .append("<p style=\"margin: 8px 0 0; font-size: 13px; color: #a0a0b8;\">")
            .append("Good morning, ").append(escapeHtml(displayName))
            .append(" &mdash; ").append(data.briefingDate())
            .append("</p>")
            .append("</td></tr>");

        boolean hasContent = false;

        // Section 1: Watchlist Alerts
        if (!data.recentMatches().isEmpty()) {
            hasContent = true;
            html.append("<tr><td style=\"padding: 24px 32px;\">")
                .append("<h2 style=\"margin: 0 0 16px; font-size: 16px; font-weight: 600; ")
                .append("color: #1a1a2e; border-left: 4px solid #0066cc; padding-left: 12px;\">")
                .append("Your Watchlist Alerts")
                .append("</h2>");

            for (WatchlistMatch match : data.recentMatches()) {
                html.append("<div style=\"margin-bottom: 12px; padding: 12px 16px; ")
                    .append("background-color: #f8f9fc; border-radius: 6px; border-left: 3px solid #0066cc;\">")
                    .append("<p style=\"margin: 0 0 4px; font-size: 13px; font-weight: 600; color: #333;\">")
                    .append(escapeHtml(match.articleId()))
                    .append("</p>");
                if (match.snippet() != null && !match.snippet().isBlank()) {
                    String snippet = match.snippet().length() > 200
                            ? match.snippet().substring(0, 200) + "..."
                            : match.snippet();
                    html.append("<p style=\"margin: 0 0 4px; font-size: 12px; color: #555;\">")
                        .append(escapeHtml(snippet))
                        .append("</p>");
                }
                html.append("<p style=\"margin: 0; font-size: 11px; color: #999;\">")
                    .append("Matched ").append(TIMESTAMP_FMT.format(match.matchedOn()))
                    .append("</p>")
                    .append("</div>");
            }
            html.append("</td></tr>");
        }

        // Section 2: Sentiment Update
        if (!data.watchedCompanySentiments().isEmpty()) {
            hasContent = true;
            html.append("<tr><td style=\"padding: 24px 32px;\">")
                .append("<h2 style=\"margin: 0 0 16px; font-size: 16px; font-weight: 600; ")
                .append("color: #1a1a2e; border-left: 4px solid #22c55e; padding-left: 12px;\">")
                .append("Sentiment Update")
                .append("</h2>");

            for (CompanySentiment sentiment : data.watchedCompanySentiments()) {
                String borderColor = sentimentColor(sentiment.overallSentiment());
                String scoreText = String.format("%+.0f%%", sentiment.sentimentScore() * 100);
                html.append("<div style=\"margin-bottom: 12px; padding: 12px 16px; ")
                    .append("background-color: #fafafa; border-radius: 6px; border-left: 3px solid ")
                    .append(borderColor).append(";\">")
                    .append("<p style=\"margin: 0 0 4px; font-size: 14px; font-weight: 600; color: #333;\">")
                    .append(escapeHtml(sentiment.companyName()))
                    .append(" <span style=\"font-size: 12px; font-weight: 400; color: ")
                    .append(borderColor).append(";\">")
                    .append(sentiment.overallSentiment().name()).append(" (").append(scoreText).append(")")
                    .append("</span></p>");
                if (sentiment.riskSummary() != null && !sentiment.riskSummary().isBlank()) {
                    String summary = firstSentence(sentiment.riskSummary());
                    html.append("<p style=\"margin: 4px 0 0; font-size: 12px; color: #555;\">")
                        .append(escapeHtml(summary))
                        .append("</p>");
                }
                html.append("</div>");
            }
            html.append("</td></tr>");
        }

        // Section 3: Recent Notes
        if (!data.recentNotes().isEmpty()) {
            hasContent = true;
            html.append("<tr><td style=\"padding: 24px 32px;\">")
                .append("<h2 style=\"margin: 0 0 16px; font-size: 16px; font-weight: 600; ")
                .append("color: #1a1a2e; border-left: 4px solid #6b21a8; padding-left: 12px;\">")
                .append("Your Recent Notes")
                .append("</h2>");

            for (AnalystNote note : data.recentNotes()) {
                html.append("<div style=\"margin-bottom: 12px; padding: 12px 16px; ")
                    .append("background-color: #faf5ff; border-radius: 6px; border-left: 3px solid #6b21a8;\">")
                    .append("<p style=\"margin: 0 0 4px; font-size: 13px; font-weight: 600; color: #333;\">")
                    .append(escapeHtml(note.targetLabel()))
                    .append(" <span style=\"font-size: 11px; font-weight: 400; color: #6b21a8;\">")
                    .append(note.targetType().name().replace('_', ' '))
                    .append("</span></p>");
                String content = note.content().length() > 150
                        ? note.content().substring(0, 150) + "..."
                        : note.content();
                html.append("<p style=\"margin: 4px 0 0; font-size: 12px; color: #555;\">")
                    .append(escapeHtml(content))
                    .append("</p>");
                java.time.Instant noteTime = note.updatedAt() != null ? note.updatedAt() : note.createdAt();
                html.append("<p style=\"margin: 4px 0 0; font-size: 11px; color: #999;\">")
                    .append(TIMESTAMP_FMT.format(noteTime))
                    .append("</p>")
                    .append("</div>");
            }
            html.append("</td></tr>");
        }

        if (!hasContent) {
            html.append("<tr><td style=\"padding: 32px; text-align: center; color: #999; font-size: 14px;\">")
                .append("No personalized content available today.")
                .append("</td></tr>");
        }

        // Close inner content table
        html.append("</table>");

        // Footer
        html.append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" ")
            .append("style=\"max-width: 600px; width: 100%; margin-top: 16px;\">")
            .append("<tr><td style=\"padding: 16px 32px; text-align: center; ")
            .append("font-size: 12px; color: #999;\">")
            .append("<p style=\"margin: 0 0 8px;\">AIHealthcare &mdash; AI-in-Healthcare Intelligence Platform</p>")
            .append("<p style=\"margin: 0;\">")
            .append("<a href=\"{{unsubscribe_url}}\" style=\"color: #999; text-decoration: underline;\">")
            .append("Unsubscribe</a> &middot; ")
            .append("<a href=\"{{preferences_url}}\" style=\"color: #999; text-decoration: underline;\">")
            .append("Manage preferences</a>")
            .append("</p>")
            .append("</td></tr></table>");

        // Close outer wrapper
        html.append("</td></tr></table>")
            .append("</body></html>");

        String result = html.toString();
        log.debug("renderHtml() | return=html[{} chars]", result.length());
        return result;
    }

    /**
     * Renders personalized plain-text email content.
     */
    public String renderPlainText(DailyBriefingData data) {
        log.debug("renderPlainText() | email={}", data.subscriberEmail());

        String displayName = data.subscriberName() != null && !data.subscriberName().isBlank()
                ? data.subscriberName() : "Subscriber";

        StringBuilder text = new StringBuilder();
        text.append("YOUR DAILY AI HEALTHCARE BRIEFING\n");
        text.append("Good morning, ").append(displayName)
            .append(" - ").append(data.briefingDate()).append("\n");
        text.append("=".repeat(60)).append("\n\n");

        boolean hasContent = false;

        if (!data.recentMatches().isEmpty()) {
            hasContent = true;
            text.append("--- YOUR WATCHLIST ALERTS ---\n\n");
            for (WatchlistMatch match : data.recentMatches()) {
                text.append("* ").append(match.articleId()).append("\n");
                if (match.snippet() != null && !match.snippet().isBlank()) {
                    String snippet = match.snippet().length() > 200
                            ? match.snippet().substring(0, 200) + "..."
                            : match.snippet();
                    text.append("  ").append(snippet).append("\n");
                }
                text.append("  Matched: ").append(TIMESTAMP_FMT.format(match.matchedOn())).append("\n\n");
            }
        }

        if (!data.watchedCompanySentiments().isEmpty()) {
            hasContent = true;
            text.append("--- SENTIMENT UPDATE ---\n\n");
            for (CompanySentiment sentiment : data.watchedCompanySentiments()) {
                String scoreText = String.format("%+.0f%%", sentiment.sentimentScore() * 100);
                text.append("* ").append(sentiment.companyName())
                    .append(" - ").append(sentiment.overallSentiment().name())
                    .append(" (").append(scoreText).append(")\n");
                if (sentiment.riskSummary() != null && !sentiment.riskSummary().isBlank()) {
                    text.append("  ").append(firstSentence(sentiment.riskSummary())).append("\n");
                }
                text.append("\n");
            }
        }

        if (!data.recentNotes().isEmpty()) {
            hasContent = true;
            text.append("--- YOUR RECENT NOTES ---\n\n");
            for (AnalystNote note : data.recentNotes()) {
                text.append("* ").append(note.targetLabel())
                    .append(" [").append(note.targetType().name().replace('_', ' ')).append("]\n");
                String content = note.content().length() > 150
                        ? note.content().substring(0, 150) + "..."
                        : note.content();
                text.append("  ").append(content).append("\n");
                java.time.Instant noteTime = note.updatedAt() != null ? note.updatedAt() : note.createdAt();
                text.append("  ").append(TIMESTAMP_FMT.format(noteTime)).append("\n\n");
            }
        }

        if (!hasContent) {
            text.append("No personalized content available today.\n\n");
        }

        text.append("---\n");
        text.append("AIHealthcare - AI-in-Healthcare Intelligence Platform\n");
        text.append("Unsubscribe: {{unsubscribe_url}}\n");
        text.append("Preferences: {{preferences_url}}\n");

        String result = text.toString();
        log.debug("renderPlainText() | return=text[{} chars]", result.length());
        return result;
    }

    private String sentimentColor(SentimentLabel label) {
        if (label == SentimentLabel.POSITIVE) {
            return "#22c55e";
        } else if (label == SentimentLabel.NEGATIVE) {
            return "#ef4444";
        } else if (label == SentimentLabel.MIXED) {
            return "#f59e0b";
        }
        return "#6b7280";
    }

    private String firstSentence(String text) {
        int dot = text.indexOf('.');
        if (dot > 0 && dot < text.length() - 1) {
            return text.substring(0, dot + 1);
        }
        return text.length() > 120 ? text.substring(0, 120) + "..." : text;
    }

    private String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;")
                     .replace("<", "&lt;")
                     .replace(">", "&gt;")
                     .replace("\"", "&quot;");
    }
}
