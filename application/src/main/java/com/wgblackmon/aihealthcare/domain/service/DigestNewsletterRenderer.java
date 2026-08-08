package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRunStatus;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Renders the FREE-tier daily article digest newsletter by fetching recent
 * RSS articles from the database and rendering them in an email-safe layout
 * with a "Subscribe for AI analysis" CTA footer.
 *
 * <p>Sources articles directly from {@link ArticleIngestionPort} with a
 * configurable lookback window (default 3 days). Articles are deduplicated
 * by title and filtered to exclude URL-derived nonsense titles.
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-07-20
 * @updated 2026-08-05
 */
@Slf4j
public class DigestNewsletterRenderer {

    private static final DateTimeFormatter EMAIL_DATE_FMT =
            DateTimeFormatter.ofPattern("M/d/yyyy");
    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy");
    private static final int LOOKBACK_DAYS = 3;
    private static final int BODY_PREVIEW_MAX_CHARS = 300;

    private final ArticleIngestionPort articleIngestionPort;

    public DigestNewsletterRenderer(ArticleIngestionPort articleIngestionPort) {
        log.debug("DigestNewsletterRenderer() | articleIngestionPort={}", articleIngestionPort.getClass().getSimpleName());
        this.articleIngestionPort = articleIngestionPort;
    }

    /**
     * Builds a digest newsletter run for FREE-tier subscribers.
     *
     * <p>Fetches articles from the last {@value LOOKBACK_DAYS} days, deduplicates
     * by title, filters out URL-derived nonsense titles, and renders them as
     * an email-safe HTML newsletter.
     *
     * @return An optional newsletter run; empty if no articles found.
     */
    public Optional<NewsletterRun> buildDigest() {
        log.debug("buildDigest() | (no args)");

        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        List<NewsArticle> rawArticles = articleIngestionPort.fetchRecentArticles(LOOKBACK_DAYS);
        log.info("buildDigest() | Fetched {} raw articles from last {} days", rawArticles.size(), LOOKBACK_DAYS);

        List<NewsArticle> filtered = filterAndDedup(rawArticles);
        log.info("buildDigest() | {} articles after filtering and dedup", filtered.size());

        if (filtered.isEmpty()) {
            log.info("buildDigest() | No articles found — skipping digest");
            log.debug("buildDigest() | return=Optional.empty()");
            return Optional.empty();
        }

        String bodyHtml = renderArticleCards(filtered);
        String wrappedHtml = wrapInEmailLayout(bodyHtml, today, filtered.size());
        String plainText = renderPlainText(filtered);

        String emailDate = today.format(EMAIL_DATE_FMT);
        NewsletterRun result = new NewsletterRun(
                "digest-" + today.format(DateTimeFormatter.ISO_LOCAL_DATE),
                filtered.size() + " News Articles From " + emailDate,
                today,
                wrappedHtml,
                plainText,
                NewsletterRunStatus.DRAFT,
                Instant.now()
        );

        log.debug("buildDigest() | return=Optional[NewsletterRun[runId={}]]", result.runId());
        return Optional.of(result);
    }

    private List<NewsArticle> filterAndDedup(List<NewsArticle> articles) {
        log.debug("filterAndDedup() | inputSize={}", articles.size());

        Set<String> seenTitles = new HashSet<>();
        List<NewsArticle> result = new ArrayList<>();

        for (NewsArticle article : articles) {
            String tier = article.sourceTier();
            if (tier != null && (tier.equals("COMPETITOR") || tier.equals("HUGGINGFACE"))) {
                continue;
            }
            String title = article.title();
            if (title == null || title.isBlank()) {
                continue;
            }
            if (isNonsenseTitle(title)) {
                continue;
            }
            String normalizedTitle = title.trim().toLowerCase();
            if (seenTitles.contains(normalizedTitle)) {
                continue;
            }
            seenTitles.add(normalizedTitle);
            result.add(article);
        }

        log.debug("filterAndDedup() | return={} articles", result.size());
        return result;
    }

    private boolean isNonsenseTitle(String title) {
        log.debug("isNonsenseTitle() | title={}", title);

        String lower = title.trim().toLowerCase();
        boolean nonsense = lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("perplexity.ai/")
                || lower.startsWith("www.")
                || lower.matches("^[a-f0-9\\-]{20,}$");

        log.debug("isNonsenseTitle() | return={}", nonsense);
        return nonsense;
    }

    private String renderArticleCards(List<NewsArticle> articles) {
        log.debug("renderArticleCards() | articleCount={}", articles.size());

        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"margin-bottom:36px; border-radius:8px; box-shadow:0 2px 6px rgba(0,0,0,0.08); overflow:hidden;\">\n");

        int index = 0;
        for (NewsArticle article : articles) {
            index++;
            String articleTitle = article.title() != null ? article.title() : "(no title)";
            String url = article.url() != null ? article.url().toString() : "#";

            String borderTop = index == 1 ? "" : " border-top:1px solid #e4eaf1;";
            sb.append("  <div id=\"article-").append(index)
              .append("\" style=\"background:#fff; padding:18px 22px;").append(borderTop).append("\">\n");
            sb.append("    <div style=\"font-size:1.05em; font-weight:bold; margin-bottom:6px;\">");
            sb.append("<a href=\"").append(escapeHtml(url))
              .append("\" target=\"_blank\" rel=\"noopener\" style=\"color:#1a3a5c; text-decoration:underline;\">")
              .append(escapeHtml(articleTitle)).append("</a>");

            String meta = buildArticleMeta(article);
            if (!meta.isEmpty()) {
                sb.append(" <span style=\"font-weight:normal; font-size:0.85em; color:#666; font-family:Arial,sans-serif;\">- ")
                  .append(escapeHtml(meta)).append("</span>");
            }
            sb.append("</div>\n");

            String bodyPreview = buildBodyPreview(article);
            if (!bodyPreview.isEmpty()) {
                sb.append("    <div style=\"font-size:0.9em; color:#444; margin-top:4px; line-height:1.5; font-family:Arial,sans-serif;\">")
                  .append(escapeHtml(bodyPreview)).append("</div>\n");
            }

            sb.append("  </div>\n");
        }
        sb.append("</div>\n");

        String result = sb.toString();
        log.debug("renderArticleCards() | return={} chars", result.length());
        return result;
    }

    private String buildArticleMeta(NewsArticle article) {
        log.debug("buildArticleMeta() | articleId={}", article.articleId());

        StringBuilder meta = new StringBuilder();
        if (article.author() != null && !article.author().isBlank()) {
            meta.append(article.author());
        }
        if (article.publishedAt() != null) {
            if (meta.length() > 0) {
                meta.append(", ");
            }
            LocalDate pubDate = article.publishedAt().atZone(ZoneOffset.UTC).toLocalDate();
            meta.append(pubDate.format(DISPLAY_FMT));
        }

        String result = meta.toString();
        log.debug("buildArticleMeta() | return={}", result);
        return result;
    }

    private String buildBodyPreview(NewsArticle article) {
        log.debug("buildBodyPreview() | articleId={}", article.articleId());

        String body = article.bodyText();
        if (body == null || body.isBlank()) {
            log.debug("buildBodyPreview() | return=(empty)");
            return "";
        }

        String cleaned = body.replaceAll("<[^>]+>", "")
                             .replaceAll("&nbsp;", " ")
                             .replaceAll("&amp;", "&")
                             .replaceAll("&lt;", "<")
                             .replaceAll("&gt;", ">")
                             .replaceAll("&quot;", "\"")
                             .replaceAll("&#?\\w+;", " ")
                             .replaceAll("\\s+", " ")
                             .trim();

        String result;
        if (cleaned.length() <= BODY_PREVIEW_MAX_CHARS) {
            result = cleaned;
        } else {
            String truncated = cleaned.substring(0, BODY_PREVIEW_MAX_CHARS);
            int lastPeriod = truncated.lastIndexOf('.');
            if (lastPeriod > BODY_PREVIEW_MAX_CHARS / 2) {
                result = truncated.substring(0, lastPeriod + 1);
            } else {
                result = truncated.trim() + "...";
            }
        }

        log.debug("buildBodyPreview() | return={} chars", result.length());
        return result;
    }

    private String renderPlainText(List<NewsArticle> articles) {
        log.debug("renderPlainText() | articleCount={}", articles.size());

        StringBuilder sb = new StringBuilder();
        int index = 0;
        for (NewsArticle article : articles) {
            index++;
            sb.append(index).append(". ").append(article.title());
            if (article.url() != null) {
                sb.append("\n   ").append(article.url().toString());
            }
            if (article.bodyText() != null && !article.bodyText().isBlank()) {
                String cleaned = article.bodyText().replaceAll("<[^>]+>", "")
                        .replaceAll("&nbsp;", " ")
                        .replaceAll("&amp;", "&")
                        .replaceAll("&lt;", "<")
                        .replaceAll("&gt;", ">")
                        .replaceAll("&quot;", "\"")
                        .replaceAll("&#?\\w+;", " ")
                        .replaceAll("\\s+", " ")
                        .trim();
                if (cleaned.length() > BODY_PREVIEW_MAX_CHARS) {
                    String truncated = cleaned.substring(0, BODY_PREVIEW_MAX_CHARS);
                    int lastPeriod = truncated.lastIndexOf('.');
                    if (lastPeriod > BODY_PREVIEW_MAX_CHARS / 2) {
                        cleaned = truncated.substring(0, lastPeriod + 1);
                    } else {
                        cleaned = truncated.trim() + "...";
                    }
                }
                sb.append("\n   ").append(cleaned);
            }
            sb.append("\n\n");
        }

        String result = sb.toString();
        log.debug("renderPlainText() | return={} chars", result.length());
        return result;
    }

    private String wrapInEmailLayout(String bodyHtml, LocalDate today, int articleCount) {
        log.debug("wrapInEmailLayout() | bodyLength={}, today={}, articleCount={}",
                  bodyHtml.length(), today, articleCount);

        String emailDate = today.format(EMAIL_DATE_FMT);
        String headerTitle = articleCount + " News Articles From " + emailDate;

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"en\">\n<head>\n");
        sb.append("  <meta charset=\"UTF-8\">\n");
        sb.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("  <title>").append(headerTitle).append("</title>\n");
        sb.append("</head>\n<body style=\"margin:0; padding:0; background:#f4f6f9; font-family:Arial,sans-serif;\">\n");
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f4f6f9;\">\n");
        sb.append("<tr><td align=\"center\" style=\"padding:20px 10px;\">\n");
        sb.append("<table width=\"640\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:640px; width:100%;\">\n");

        // Header
        sb.append("<tr><td style=\"background:#1a1a2e; color:white; padding:20px 24px; border-radius:8px 8px 0 0;\">\n");
        sb.append("  <h1 style=\"margin:0; font-size:1.3em; color:white;\">").append(headerTitle).append("</h1>\n");
        sb.append("  <p style=\"margin:8px 0 0; font-size:0.85em; color:#b0b8c8; line-height:1.4;\">");
        sb.append("AIHealthcare Intelligence is a weekly briefing on AI in healthcare &mdash; sourced from 69 feeds, ");
        sb.append("scored by five LLMs, and curated for decision-makers tracking regulatory, clinical, and commercial developments.");
        sb.append("</p>\n");
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
        sb.append("padding:10px 24px; border-radius:6px; text-decoration:none; font-weight:600; font-size:0.9em;\">Upgrade to Subscriber — $19/mo</a>\n");
        sb.append("  <span style=\"display:inline-block; margin-left:12px;\">");
        sb.append("<a href=\"https://app.bigskylabs.ai/demo\" style=\"display:inline-block; background:#28a745; color:white; ");
        sb.append("padding:10px 24px; border-radius:6px; text-decoration:none; font-weight:600; font-size:0.9em;\">Free 7 Day Demo</a></span>\n");
        sb.append("</td></tr>\n");

        // Footer with unsubscribe
        sb.append("<tr><td style=\"padding:16px 24px; text-align:center; font-size:0.75em; color:#999; border-radius:0 0 8px 8px;\">\n");
        sb.append("  You are receiving this because you signed up for the free AI Healthcare digest.<br>\n");
        sb.append("  <a href=\"https://app.bigskylabs.ai/unsubscribe\" style=\"color:#999; text-decoration:underline; font-size:0.9em;\">Unsubscribe</a>\n");
        sb.append("</td></tr>\n");

        sb.append("</table>\n</td></tr>\n</table>\n</body>\n</html>\n");

        String result = sb.toString();
        log.debug("wrapInEmailLayout() | return={} chars", result.length());
        return result;
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
