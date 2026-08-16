package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterDraft;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
import com.wgblackmon.aihealthcare.domain.model.SectionType;
import lombok.extern.slf4j.Slf4j;

/**
 * Renders a {@link NewsletterDraft} into two output formats for storage and delivery.
 *
 * <p>This class is a pure application-layer service with no Spring annotations and
 * no framework dependencies.  It is constructed directly in {@code AppConfig} and
 * injected into {@link NewsletterService} via constructor injection.
 *
 * <p>Two rendering modes are provided:
 * <ul>
 *   <li><b>HTML</b> — inline-styled markup ready for email clients and the
 *       subscriber archive web page.  No external CSS framework is required;
 *       all styles are inline to maximize email client compatibility.</li>
 *   <li><b>Plain text</b> — the same content with all HTML stripped, suitable
 *       for a {@code <textarea>} display or accessibility use.  Sections are
 *       separated by {@code ---}.</li>
 * </ul>
 *
 * <p>Rendering uses only {@link StringBuilder} and traditional for loops — no
 * Java Streams — per project conventions.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-11
 * @updated 2026-08-08
 */
@Slf4j
public class NewsletterRenderer {

    /**
     * Renders the draft as an inline-styled HTML document.
     *
     * @param draft the newsletter draft to render; must not be {@code null}
     * @return non-blank HTML string
     */
    public String renderHtml(NewsletterDraft draft) {
        log.debug("renderHtml() | draftId={}", draft.draftId());

        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>")
            .append("<html lang=\"en\"><head>")
            .append("<meta charset=\"UTF-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
            .append("<title>").append(escapeHtml(draft.title())).append("</title>")
            .append("</head>")
            .append("<body style=\"margin: 0; padding: 0; background-color: #f5f6fa; ")
            .append("font-family: Arial, Helvetica, sans-serif; color: #333;\">");

        // Outer wrapper table for email client compatibility
        html.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
            .append("style=\"background-color: #f5f6fa;\"><tr><td align=\"center\" ")
            .append("style=\"padding: 24px 16px;\">");

        // Inner content table
        html.append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" ")
            .append("style=\"max-width: 600px; width: 100%; background-color: #ffffff; ")
            .append("border-radius: 8px; overflow: hidden; ")
            .append("box-shadow: 0 2px 8px rgba(0,0,0,0.08);\">");

        // Header banner
        html.append("<tr><td style=\"background-color: #1a1a2e; padding: 28px 32px; ")
            .append("text-align: center;\">")
            .append("<h1 style=\"margin: 0; font-size: 22px; font-weight: 700; color: #ffffff; ")
            .append("letter-spacing: 0.5px;\">")
            .append(escapeHtml(draft.title()))
            .append("</h1>")
            .append("<p style=\"margin: 8px 0 0; font-size: 13px; color: #a0a0b8;\">")
            .append("Week of ").append(draft.weekOf())
            .append("</p>")
            .append("<p style=\"margin: 8px 0 0; font-size: 12px; color: #a0a0b8; line-height: 1.4;\">")
            .append("AIHealthcare Intelligence is a weekly briefing on AI in healthcare &mdash; sourced from 69 feeds, ")
            .append("scored by five LLMs, and curated for decision-makers tracking regulatory, clinical, and commercial developments.")
            .append("</p>")
            .append("</td></tr>");

        // Introduction
        if (draft.introduction() != null && !draft.introduction().isBlank()) {
            html.append("<tr><td style=\"padding: 24px 32px 8px;\">")
                .append("<p style=\"margin: 0; font-size: 15px; line-height: 1.7; color: #444;\">")
                .append(escapeHtml(draft.introduction()))
                .append("</p>")
                .append("</td></tr>");
        }

        // Sections
        for (NewsletterSection section : draft.sections()) {
            String borderColor;
            String bgColor;
            String headlineColor;
            String icon;

            if (section.sectionType() == SectionType.REVERSAL_WATCH) {
                borderColor = "#cc3300";
                bgColor = "#fff5f5";
                headlineColor = "#cc3300";
                icon = "\u26A0 ";
            } else if (section.sectionType() == SectionType.LEGAL_BRIEF) {
                borderColor = "#1a5276";
                bgColor = "#eaf2f8";
                headlineColor = "#1a5276";
                icon = "\u2696 ";
            } else {
                borderColor = "#0066cc";
                bgColor = "#f8f9fc";
                headlineColor = "#0066cc";
                icon = "";
            }

            html.append("<tr><td style=\"padding: 16px 32px;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"border-left: 4px solid ").append(borderColor)
                .append("; background-color: ").append(bgColor)
                .append("; border-radius: 0 6px 6px 0;\">")
                .append("<tr><td style=\"padding: 16px 20px;\">")
                .append("<h2 style=\"margin: 0 0 8px; font-size: 17px; color: ").append(headlineColor).append(";\">")
                .append(icon)
                .append(escapeHtml(section.headline()))
                .append("</h2>");

            if (section.sectionType() == SectionType.REVERSAL_WATCH) {
                html.append("<ul style=\"margin: 0; padding: 0 0 0 18px; font-size: 14px; line-height: 1.9; color: #444;\">");
                for (String item : section.summary().split("\n")) {
                    if (!item.isBlank()) {
                        html.append("<li style=\"margin-bottom: 6px;\">").append(escapeHtml(item)).append("</li>");
                    }
                }
                html.append("</ul>");
            } else if (section.sectionType() == SectionType.LEGAL_BRIEF) {
                boolean inList = false;
                for (String line : section.summary().split("\n")) {
                    if (line.isBlank()) continue;
                    if (line.startsWith("##")) {
                        if (inList) { html.append("</ul>"); inList = false; }
                        html.append("<p style=\"margin: 10px 0 3px; font-size: 11px; font-weight: bold; ")
                           .append("color: #1a5276; text-transform: uppercase; letter-spacing: 0.8px; ")
                           .append("border-bottom: 1px solid #aed6f1; padding-bottom: 2px;\">")
                           .append(escapeHtml(line.substring(2))).append("</p>");
                        html.append("<ul style=\"margin: 0; padding: 0 0 6px 18px; font-size: 14px; ")
                           .append("line-height: 1.85; color: #444;\">");
                        inList = true;
                    } else {
                        if (!inList) {
                            html.append("<ul style=\"margin: 0; padding: 0 0 6px 18px; font-size: 14px; ")
                               .append("line-height: 1.85; color: #444;\">");
                            inList = true;
                        }
                        int sep = line.indexOf("||");
                        if (sep > 0 && sep < line.length() - 2) {
                            String linkText = line.substring(0, sep);
                            String linkUrl  = line.substring(sep + 2);
                            html.append("<li style=\"margin-bottom: 3px;\">")
                               .append("<a href=\"").append(escapeHtml(linkUrl))
                               .append("\" style=\"color: #1a5276; text-decoration: underline;\" target=\"_blank\">")
                               .append(escapeHtml(linkText)).append("</a></li>");
                        } else {
                            html.append("<li style=\"margin-bottom: 3px;\">").append(escapeHtml(line)).append("</li>");
                        }
                    }
                }
                if (inList) html.append("</ul>");
            } else {
                html.append("<p style=\"margin: 0; font-size: 14px; line-height: 1.7; color: #444;\">")
                    .append(escapeHtml(section.summary()))
                    .append("</p>");
            }

            html
                .append("</td></tr></table>")
                .append("</td></tr>");
        }

        // Sources
        if (!draft.sourceArticles().isEmpty()) {
            html.append("<tr><td style=\"padding: 8px 32px 0;\">")
                .append("<hr style=\"border: none; border-top: 1px solid #e8e8e8; margin: 0;\">")
                .append("</td></tr>")
                .append("<tr><td style=\"padding: 16px 32px;\">")
                .append("<h3 style=\"margin: 0 0 12px; font-size: 15px; color: #555; ")
                .append("text-transform: uppercase; letter-spacing: 0.5px;\">Sources</h3>")
                .append("<ul style=\"margin: 0; padding: 0 0 0 18px; line-height: 1.9;\">");
            for (NewsArticle article : draft.sourceArticles()) {
                html.append("<li style=\"font-size: 13px; color: #555;\"><a href=\"")
                    .append(article.url())
                    .append("\" style=\"color: #0066cc; text-decoration: none;\">")
                    .append(escapeHtml(article.title()))
                    .append("</a></li>");
            }
            html.append("</ul>")
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
     * Renders the draft as plain text with no HTML markup.
     *
     * <p>Sections are separated by {@code ---}.  The sources block appears at
     * the end, one per line as {@code - Title: URL}.
     *
     * @param draft the newsletter draft to render; must not be {@code null}
     * @return non-blank plain-text string
     */
    public String renderPlainText(NewsletterDraft draft) {
        log.debug("renderPlainText() | draftId={}", draft.draftId());

        StringBuilder text = new StringBuilder();

        text.append(draft.title()).append("\n");
        text.append("Week of ").append(draft.weekOf()).append("\n");
        text.append("=".repeat(60)).append("\n\n");

        if (draft.introduction() != null && !draft.introduction().isBlank()) {
            text.append(draft.introduction()).append("\n\n");
        }

        for (NewsletterSection section : draft.sections()) {
            if (section.sectionType() == SectionType.REVERSAL_WATCH) {
                text.append("=== REVERSAL WATCH ===\n");
            } else if (section.sectionType() == SectionType.LEGAL_BRIEF) {
                text.append("=== LEGAL & REGULATORY BRIEF ===\n");
            } else {
                text.append("---\n");
            }
            text.append(section.headline()).append("\n\n");
            if (section.sectionType() == SectionType.LEGAL_BRIEF) {
                for (String line : section.summary().split("\n")) {
                    if (line.isBlank()) continue;
                    if (line.startsWith("##")) {
                        text.append(line.substring(2)).append(":\n");
                    } else {
                        int sep = line.indexOf("||");
                        if (sep > 0) {
                            text.append("  • ").append(line.substring(0, sep)).append("\n")
                               .append("    ").append(line.substring(sep + 2)).append("\n");
                        } else {
                            text.append("  • ").append(line).append("\n");
                        }
                    }
                }
                text.append("\n");
            } else {
                text.append(section.summary()).append("\n\n");
            }
        }

        if (!draft.sourceArticles().isEmpty()) {
            text.append("---\nSOURCES\n\n");
            for (NewsArticle article : draft.sourceArticles()) {
                text.append("- ").append(article.title())
                    .append(": ").append(article.url()).append("\n");
            }
        }

        text.append("\n---\n");
        text.append("AIHealthcare - AI-in-Healthcare Intelligence Platform\n");
        text.append("Unsubscribe: {{unsubscribe_url}}\n");
        text.append("Manage preferences: {{preferences_url}}\n");

        String result = text.toString();
        log.debug("renderPlainText() | return=text[{} chars]", result.length());
        return result;
    }

    /**
     * Escapes the minimal set of HTML special characters to prevent XSS in
     * rendered output.
     *
     * @param input raw string; may be {@code null}
     * @return escaped string, or empty string if input was {@code null}
     */
    private String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '&') {
                sb.append("&amp;");
            } else if (c == '<') {
                sb.append("&lt;");
            } else if (c == '>') {
                sb.append("&gt;");
            } else if (c == '"') {
                sb.append("&quot;");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
