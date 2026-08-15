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
 * Renders a complimentary sample newsletter for cold outreach prospects.
 *
 * <p>Builds an email-safe HTML newsletter from recent harvested articles,
 * framed as a free sample issue with a prominent 7-day trial CTA banner.
 * Demonstrates the product's value before asking for commitment.
 *
 * <p>Uses the same article fetching and dedup logic as
 * {@link DigestNewsletterRenderer} but with different framing: a
 * "Complimentary Issue" header, inline value propositions, and a
 * soft-sell trial CTA rather than a pricing push.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-06
 * @updated 2026-08-06
 */
@Slf4j
public class SampleNewsletterRenderer {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy");
    private static final int LOOKBACK_DAYS = 3;
    private static final int BODY_PREVIEW_MAX_CHARS = 300;
    private static final int MAX_ARTICLES = 15;

    private final ArticleIngestionPort articleIngestionPort;

    public SampleNewsletterRenderer(ArticleIngestionPort articleIngestionPort) {
        log.debug("SampleNewsletterRenderer() | articleIngestionPort={}", articleIngestionPort.getClass().getSimpleName());
        this.articleIngestionPort = articleIngestionPort;
    }

    /**
     * Builds a sample newsletter for prospect outreach.
     *
     * @return An optional newsletter run; empty if no articles found.
     */
    public Optional<NewsletterRun> buildSample() {
        log.debug("buildSample() | (no args)");

        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        List<NewsArticle> rawArticles = articleIngestionPort.fetchRecentArticles(LOOKBACK_DAYS);
        log.info("buildSample() | Fetched {} raw articles from last {} days", rawArticles.size(), LOOKBACK_DAYS);

        List<NewsArticle> filtered = filterAndDedup(rawArticles);
        log.info("buildSample() | {} articles after filtering and dedup", filtered.size());

        if (filtered.isEmpty()) {
            log.info("buildSample() | No articles found — skipping sample");
            log.debug("buildSample() | return=Optional.empty()");
            return Optional.empty();
        }

        if (filtered.size() > MAX_ARTICLES) {
            filtered = filtered.subList(0, MAX_ARTICLES);
        }

        String bodyHtml = renderArticleCards(filtered);
        String wrappedHtml = wrapInEmailLayout(bodyHtml, today, filtered.size());
        String plainText = renderPlainText(filtered, today);

        NewsletterRun result = new NewsletterRun(
                "sample-" + today.format(DateTimeFormatter.ISO_LOCAL_DATE),
                "Complimentary Issue: AI Healthcare Intelligence",
                today,
                wrappedHtml,
                plainText,
                NewsletterRunStatus.DRAFT,
                Instant.now()
        );

        log.debug("buildSample() | return=Optional[NewsletterRun[runId={}]]", result.runId());
        return Optional.of(result);
    }

    private List<NewsArticle> filterAndDedup(List<NewsArticle> articles) {
        log.debug("filterAndDedup() | inputSize={}", articles.size());

        Set<String> seenTitles = new HashSet<>();
        List<NewsArticle> result = new ArrayList<>();

        for (NewsArticle article : articles) {
            String tier = article.sourceTier();
            if (tier != null && tier.equals("COMPETITOR")) {
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
        String lower = title.trim().toLowerCase();
        return lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("perplexity.ai/")
                || lower.startsWith("www.")
                || lower.matches("^[a-f0-9\\-]{20,}$");
    }

    private String renderArticleCards(List<NewsArticle> articles) {
        log.debug("renderArticleCards() | articleCount={}", articles.size());

        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"margin-bottom:24px; border-radius:8px; box-shadow:0 2px 6px rgba(0,0,0,0.08); overflow:hidden;\">\n");

        int index = 0;
        for (NewsArticle article : articles) {
            index++;
            String articleTitle = article.title() != null ? article.title() : "(no title)";
            String url = article.url() != null ? article.url().toString() : "#";

            String borderTop = index == 1 ? "" : " border-top:1px solid #e4eaf1;";
            sb.append("  <div style=\"background:#fff; padding:16px 22px;").append(borderTop).append("\">\n");
            sb.append("    <div style=\"font-size:1.02em; font-weight:bold; margin-bottom:4px;\">");
            sb.append("<a href=\"").append(escapeHtml(url))
              .append("\" target=\"_blank\" rel=\"noopener\" style=\"color:#1a3a5c; text-decoration:underline;\">")
              .append(escapeHtml(articleTitle)).append("</a>");

            String meta = buildArticleMeta(article);
            if (!meta.isEmpty()) {
                sb.append(" <span style=\"font-weight:normal; font-size:0.85em; color:#666;\">- ")
                  .append(escapeHtml(meta)).append("</span>");
            }
            sb.append("</div>\n");

            String bodyPreview = buildBodyPreview(article);
            if (!bodyPreview.isEmpty()) {
                sb.append("    <div style=\"font-size:0.9em; color:#444; margin-top:4px; line-height:1.5;\">")
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
        return meta.toString();
    }

    private String buildBodyPreview(NewsArticle article) {
        String body = article.bodyText();
        if (body == null || body.isBlank()) {
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

        if (cleaned.length() <= BODY_PREVIEW_MAX_CHARS) {
            return cleaned;
        }
        String truncated = cleaned.substring(0, BODY_PREVIEW_MAX_CHARS);
        int lastPeriod = truncated.lastIndexOf('.');
        if (lastPeriod > BODY_PREVIEW_MAX_CHARS / 2) {
            return truncated.substring(0, lastPeriod + 1);
        }
        return truncated.trim() + "...";
    }

    private String wrapInEmailLayout(String bodyHtml, LocalDate today, int articleCount) {
        log.debug("wrapInEmailLayout() | bodyLength={}, today={}, articleCount={}",
                  bodyHtml.length(), today, articleCount);

        String dateStr = today.format(DISPLAY_FMT);

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"en\">\n<head>\n");
        sb.append("  <meta charset=\"UTF-8\">\n");
        sb.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("  <title>Complimentary Issue: AI Healthcare Intelligence</title>\n");
        sb.append("</head>\n<body style=\"margin:0; padding:0; background:#f4f6f9; font-family:Arial,sans-serif;\">\n");
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f4f6f9;\">\n");
        sb.append("<tr><td align=\"center\" style=\"padding:20px 10px;\">\n");
        sb.append("<table width=\"640\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:640px; width:100%;\">\n");

        // Header
        sb.append("<tr><td style=\"background:#1a1a2e; color:white; padding:24px 28px; border-radius:8px 8px 0 0;\">\n");
        sb.append("  <div style=\"font-size:0.8em; text-transform:uppercase; letter-spacing:1px; color:#8ecae6; margin-bottom:6px;\">Complimentary Issue</div>\n");
        sb.append("  <h1 style=\"margin:0; font-size:1.4em; color:white;\">AI Healthcare Intelligence</h1>\n");
        sb.append("  <p style=\"margin:6px 0 0; font-size:0.85em; color:#ccc;\">").append(dateStr).append(" &bull; ").append(articleCount).append(" articles</p>\n");
        sb.append("</td></tr>\n");

        // Intro banner
        sb.append("<tr><td style=\"background:#e8f4fd; padding:20px 28px; border-bottom:2px solid #0066cc;\">\n");
        sb.append("  <p style=\"margin:0; font-size:0.95em; color:#1a3a5c; line-height:1.6;\">\n");
        sb.append("    <strong>This is a complimentary issue</strong> of what subscribers receive daily &mdash; curated AI healthcare news, ");
        sb.append("regulatory alerts, deal signals, and competitive intelligence across the industry.\n");
        sb.append("  </p>\n");
        sb.append("</td></tr>\n");

        // Articles
        sb.append("<tr><td style=\"background:white; padding:24px 28px;\">\n");
        sb.append("  <h2 style=\"margin:0 0 16px; font-size:1.1em; color:#1a1a2e; border-bottom:1px solid #e4eaf1; padding-bottom:8px;\">Today's Headlines</h2>\n");
        sb.append(bodyHtml);
        sb.append("\n</td></tr>\n");

        // What subscribers also get
        sb.append("<tr><td style=\"background:#f8f9fb; padding:24px 28px; border-top:1px solid #e4eaf1;\">\n");
        sb.append("  <h3 style=\"margin:0 0 12px; font-size:1em; color:#1a1a2e;\">Subscribers Also Get</h3>\n");
        sb.append("  <table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">\n");
        sb.append("    <tr>\n");
        sb.append("      <td style=\"padding:6px 0; font-size:0.9em; color:#444;\">\n");
        sb.append("        <span style=\"color:#0066cc; font-weight:bold;\">&#10003;</span> AI-powered multi-model research synthesis (Claude, GPT, Gemini, Perplexity)\n");
        sb.append("      </td>\n");
        sb.append("    </tr>\n");
        sb.append("    <tr>\n");
        sb.append("      <td style=\"padding:6px 0; font-size:0.9em; color:#444;\">\n");
        sb.append("        <span style=\"color:#0066cc; font-weight:bold;\">&#10003;</span> Real-time deal signals &mdash; funding, acquisitions, partnerships, IPOs\n");
        sb.append("      </td>\n");
        sb.append("    </tr>\n");
        sb.append("    <tr>\n");
        sb.append("      <td style=\"padding:6px 0; font-size:0.9em; color:#444;\">\n");
        sb.append("        <span style=\"color:#0066cc; font-weight:bold;\">&#10003;</span> Competitive framework analysis with 6-dimension scoring\n");
        sb.append("      </td>\n");
        sb.append("    </tr>\n");
        sb.append("    <tr>\n");
        sb.append("      <td style=\"padding:6px 0; font-size:0.9em; color:#444;\">\n");
        sb.append("        <span style=\"color:#0066cc; font-weight:bold;\">&#10003;</span> FDA/CMS regulatory alerts and company sentiment tracking\n");
        sb.append("      </td>\n");
        sb.append("    </tr>\n");
        sb.append("    <tr>\n");
        sb.append("      <td style=\"padding:6px 0; font-size:0.9em; color:#444;\">\n");
        sb.append("        <span style=\"color:#0066cc; font-weight:bold;\">&#10003;</span> Keyword trend detection and custom watchlists\n");
        sb.append("      </td>\n");
        sb.append("    </tr>\n");
        sb.append("  </table>\n");
        sb.append("</td></tr>\n");

        // CTA
        sb.append("<tr><td style=\"background:#0066cc; padding:28px; text-align:center;\">\n");
        sb.append("  <h3 style=\"margin:0 0 8px; color:white; font-size:1.1em;\">Start Your Free 7-Day Trial</h3>\n");
        sb.append("  <p style=\"margin:0 0 16px; color:#d0e8ff; font-size:0.9em;\">Full platform access. No credit card required. Cancel anytime.</p>\n");
        sb.append("  <a href=\"https://app.bigskylabs.ai/register\" style=\"display:inline-block; background:white; color:#0066cc; ");
        sb.append("padding:12px 32px; border-radius:6px; text-decoration:none; font-weight:700; font-size:1em;\">Start Free Trial</a>\n");
        sb.append("  <div style=\"margin-top:12px;\">\n");
        sb.append("    <a href=\"https://app.bigskylabs.ai/pricing\" style=\"color:#d0e8ff; font-size:0.85em; text-decoration:underline;\">View pricing plans</a>\n");
        sb.append("  </div>\n");
        sb.append("</td></tr>\n");

        // Footer
        sb.append("<tr><td style=\"padding:16px 28px; text-align:center; font-size:0.75em; color:#999; border-radius:0 0 8px 8px;\">\n");
        sb.append("  You received this complimentary issue from Big Sky Labs.<br>\n");
        sb.append("  <a href=\"{{unsubscribe_url}}\" style=\"color:#999; text-decoration:underline;\">Not interested? Unsubscribe</a>\n");
        sb.append("  &nbsp;&bull;&nbsp; <a href=\"https://bigskylabs.ai\" style=\"color:#999; text-decoration:underline;\">bigskylabs.ai</a>\n");
        sb.append("</td></tr>\n");

        sb.append("</table>\n</td></tr>\n</table>\n</body>\n</html>\n");

        String result = sb.toString();
        log.debug("wrapInEmailLayout() | return={} chars", result.length());
        return result;
    }

    private String renderPlainText(List<NewsArticle> articles, LocalDate today) {
        log.debug("renderPlainText() | articleCount={}", articles.size());

        StringBuilder sb = new StringBuilder();
        sb.append("COMPLIMENTARY ISSUE: AI Healthcare Intelligence\n");
        sb.append(today.format(DISPLAY_FMT)).append("\n\n");
        sb.append("This is a complimentary issue of what subscribers receive daily.\n\n");
        sb.append("---\n\n");

        int index = 0;
        for (NewsArticle article : articles) {
            index++;
            sb.append(index).append(". ").append(article.title());
            if (article.url() != null) {
                sb.append("\n   ").append(article.url().toString());
            }
            sb.append("\n\n");
        }

        sb.append("---\n\n");
        sb.append("SUBSCRIBERS ALSO GET:\n");
        sb.append("- AI-powered multi-model research synthesis\n");
        sb.append("- Real-time deal signals (funding, acquisitions, partnerships)\n");
        sb.append("- Competitive framework analysis\n");
        sb.append("- FDA/CMS regulatory alerts\n");
        sb.append("- Keyword trend detection and custom watchlists\n\n");
        sb.append("Start your free 7-day trial: https://app.bigskylabs.ai/register\n\n");
        sb.append("---\n");
        sb.append("Not interested? {{unsubscribe_url}}\n");

        String result = sb.toString();
        log.debug("renderPlainText() | return={} chars", result.length());
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
