package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterDraft;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
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
 * @updated 2026-04-11
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
            .append("<body style=\"font-family: Arial, sans-serif; max-width: 800px; ")
            .append("margin: 40px auto; padding: 0 20px; color: #333;\">")
            .append("<h1 style=\"color: #0066cc; border-bottom: 2px solid #0066cc; ")
            .append("padding-bottom: 10px;\">")
            .append(escapeHtml(draft.title()))
            .append("</h1>")
            .append("<p style=\"color: #666; font-size: 0.9em;\">")
            .append("Week of ").append(draft.weekOf())
            .append("</p>");

        if (draft.introduction() != null && !draft.introduction().isBlank()) {
            html.append("<p style=\"font-size: 1.05em; line-height: 1.6;\">")
                .append(escapeHtml(draft.introduction()))
                .append("</p>");
        }

        for (NewsletterSection section : draft.sections()) {
            html.append("<div style=\"border-left: 4px solid #0066cc; padding: 10px 20px; ")
                .append("margin: 24px 0; background: #f9f9f9;\">")
                .append("<h2 style=\"margin-top: 0; color: #0066cc;\">")
                .append(escapeHtml(section.headline()))
                .append("</h2>")
                .append("<p style=\"line-height: 1.6;\">")
                .append(escapeHtml(section.summary()))
                .append("</p>")
                .append("</div>");
        }

        if (!draft.sourceArticles().isEmpty()) {
            html.append("<hr style=\"border: none; border-top: 1px solid #ddd; margin: 32px 0;\">")
                .append("<h3 style=\"color: #555;\">Sources</h3>")
                .append("<ul style=\"line-height: 1.8;\">");
            for (NewsArticle article : draft.sourceArticles()) {
                html.append("<li><a href=\"")
                    .append(article.url())
                    .append("\" style=\"color: #0066cc;\">")
                    .append(escapeHtml(article.title()))
                    .append("</a></li>");
            }
            html.append("</ul>");
        }

        html.append("</body></html>");

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
            text.append("---\n");
            text.append(section.headline()).append("\n\n");
            text.append(section.summary()).append("\n\n");
        }

        if (!draft.sourceArticles().isEmpty()) {
            text.append("---\nSOURCES\n\n");
            for (NewsArticle article : draft.sourceArticles()) {
                text.append("- ").append(article.title())
                    .append(": ").append(article.url()).append("\n");
            }
        }

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
