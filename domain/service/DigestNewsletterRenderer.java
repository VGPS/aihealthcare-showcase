package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRunStatus;
import com.wgblackmon.aihealthcare.domain.model.ScoredArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleBodyFormattingPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleScoringPort;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Renders the FREE-tier daily article digest newsletter by fetching recent
 * RSS articles from the database and rendering them in an email-safe layout
 * with a "Subscribe for AI analysis" CTA footer.
 *
 * <p>Each run leads with an "Article of the Day" block: the top-20 articles
 * by source weight are scored 1–10 by an LLM for healthcare AI significance.
 * The highest-scored article is featured at the top of the email with its full
 * body text (reformatted with entity bolding) and the LLM rationale as a
 * pull-quote. Both LLM calls are wrapped in try/catch — the digest sends
 * without the featured block if scoring or formatting fails.
 *
 * @author  Bill Blackmon
 * @version 3.3
 * @since   2026-07-20
 * @updated 2026-08-24
 */
@Slf4j
public class DigestNewsletterRenderer {

    private static final DateTimeFormatter DISPLAY_FMT        = DateTimeFormatter.ofPattern("MMMM d, yyyy");
    private static final int               LOOKBACK_DAYS       = 1;
    private static final int               MAX_DIGEST_ARTICLES = 75;
    private static final int               BODY_PREVIEW_MAX    = 300;
    /** Number of top articles (by sourceWeight) submitted to the scoring LLM. */
    private static final int               SCORING_CANDIDATES  = 20;
    private static final String            SCORING_THEME       = "AI Healthcare";
    private static final String            SCORING_DESCRIPTION =
            "Significant developments in AI healthcare: FDA approvals, clinical AI breakthroughs, "
            + "major partnerships, funding rounds, regulatory rulings, and commercial product launches.";

    private final ArticleIngestionPort      articleIngestionPort;
    private final ArticleScoringPort        articleScoringPort;
    private final ArticleBodyFormattingPort bodyFormattingPort;
    private final ArticleQualityFilter      articleQualityFilter;

    public DigestNewsletterRenderer(ArticleIngestionPort articleIngestionPort,
                                    ArticleScoringPort articleScoringPort,
                                    ArticleBodyFormattingPort bodyFormattingPort,
                                    ArticleQualityFilter articleQualityFilter) {
        log.debug("DigestNewsletterRenderer() | articleIngestionPort={}, articleScoringPort={}, bodyFormattingPort={}, articleQualityFilter={}",
                  articleIngestionPort.getClass().getSimpleName(),
                  articleScoringPort.getClass().getSimpleName(),
                  bodyFormattingPort.getClass().getSimpleName(),
                  articleQualityFilter.getClass().getSimpleName());
        this.articleIngestionPort = articleIngestionPort;
        this.articleScoringPort   = articleScoringPort;
        this.bodyFormattingPort   = bodyFormattingPort;
        this.articleQualityFilter = articleQualityFilter;
        log.debug("DigestNewsletterRenderer() | return=void");
    }

    /**
     * Builds a digest newsletter run for FREE-tier subscribers.
     *
     * <p>Scores the top-20 articles by source weight via LLM, features the
     * highest-scored article at the top, then renders the full article list below.
     *
     * @return An optional newsletter run; empty if no articles found.
     */
    public Optional<NewsletterRun> buildDigest() {
        log.debug("buildDigest() | (no args)");

        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        List<NewsArticle> rawArticles = articleIngestionPort.fetchRecentArticles(LOOKBACK_DAYS);
        log.info("buildDigest() | fetched {} raw articles from last {} days", rawArticles.size(), LOOKBACK_DAYS);

        List<NewsArticle> filtered = filterAndDedup(rawArticles);
        log.info("buildDigest() | {} articles after filtering and dedup", filtered.size());

        Collections.sort(filtered, (a, b) -> Double.compare(b.sourceWeight(), a.sourceWeight()));
        if (filtered.size() > MAX_DIGEST_ARTICLES) {
            filtered = new ArrayList<>(filtered.subList(0, MAX_DIGEST_ARTICLES));
            log.info("buildDigest() | capped to top {} articles by sourceWeight", MAX_DIGEST_ARTICLES);
        }

        if (filtered.isEmpty()) {
            log.info("buildDigest() | no articles found — skipping digest");
            log.debug("buildDigest() | return=Optional.empty()");
            return Optional.empty();
        }

        // Score top candidates and build "Article of the Day" block
        String featuredHtml     = "";
        String featuredPlainText = "";
        // Exclude domain-style titles and stub articles (body < 100 chars) from Article of the Day
        List<NewsArticle> scorable = new ArrayList<>();
        for (NewsArticle a : filtered) {
            if (isDomainTitle(a.title())) continue;
            if (a.bodyText() == null || a.bodyText().length() < 100) continue;
            scorable.add(a);
            if (scorable.size() >= SCORING_CANDIDATES) break;
        }
        List<NewsArticle> candidates = scorable.isEmpty()
                ? filtered.subList(0, Math.min(SCORING_CANDIDATES, filtered.size()))
                : scorable;

        try {
            List<ScoredArticle> scored = articleScoringPort.scoreArticles(
                    candidates, SCORING_THEME, SCORING_DESCRIPTION, 1);
            log.info("buildDigest() | scored {} articles, top score={}",
                     scored.size(), scored.isEmpty() ? "n/a" : scored.get(0).score());

            if (!scored.isEmpty()) {
                ScoredArticle topScore  = scored.get(0);
                NewsArticle   topArticle = findByArticleId(candidates, topScore.articleId());
                if (topArticle != null) {
                    featuredHtml      = renderFeaturedArticle(topArticle, topScore);
                    featuredPlainText = renderFeaturedPlainText(topArticle, topScore);
                }
            }
        } catch (Exception e) {
            log.warn("buildDigest() | article scoring failed — digest will send without Article of the Day", e);
        }

        Instant oneDayAgo = Instant.now().minus(1, ChronoUnit.DAYS);
        List<NewsArticle> todaysItems     = new ArrayList<>();
        List<NewsArticle> discoveredItems = new ArrayList<>();
        for (NewsArticle article : filtered) {
            if (article.publishedAt() != null && !article.publishedAt().isBefore(oneDayAgo)) {
                todaysItems.add(article);
            } else {
                discoveredItems.add(article);
            }
        }
        log.info("buildDigest() | today={}, discovered={}", todaysItems.size(), discoveredItems.size());

        String bodyHtml  = featuredHtml + renderSectionedCards(todaysItems, discoveredItems);
        String subject   = "AI Healthcare Intelligence — " + today.format(DISPLAY_FMT);
        String plainText = featuredPlainText + renderSectionedPlainText(todaysItems, discoveredItems)
                + renderPlainTextFooter();

        NewsletterRun result = new NewsletterRun(
                "digest-" + today.format(DateTimeFormatter.ISO_LOCAL_DATE),
                subject,
                today,
                wrapInEmailLayout(bodyHtml, today, filtered.size(), subject),
                plainText,
                NewsletterRunStatus.DRAFT,
                Instant.now());

        log.debug("buildDigest() | return=Optional[NewsletterRun[runId={}]]", result.runId());
        return Optional.of(result);
    }

    // -------------------------------------------------------------------------
    // Featured article block
    // -------------------------------------------------------------------------

    private String renderFeaturedArticle(NewsArticle article, ScoredArticle score) {
        log.debug("renderFeaturedArticle() | articleId={}, score={}", article.articleId(), score.score());

        String formattedBody = buildFormattedBody(article);

        String url  = article.url() != null ? escapeHtml(article.url().toString()) : "#";
        String meta = buildArticleMeta(article);

        StringBuilder sb = new StringBuilder();
        // Outer box — blue border, light blue background
        sb.append("<div style=\"background:#f0f7ff; border:2px solid #0066cc; border-radius:8px; ")
          .append("padding:24px 28px; margin-bottom:28px;\">\n");

        // Badge row
        sb.append("  <div style=\"font-size:0.7em; font-weight:bold; color:#0066cc; text-transform:uppercase; ")
          .append("letter-spacing:1.5px; margin-bottom:10px;\">")
          .append("&#11088; Article of the Day &nbsp;&middot;&nbsp; Significance: ")
          .append(score.score()).append("/10</div>\n");

        // Title (linked)
        sb.append("  <h2 style=\"margin:0 0 6px; font-size:1.15em; line-height:1.4; font-family:Arial,sans-serif;\">")
          .append("<a href=\"").append(url)
          .append("\" target=\"_blank\" rel=\"noopener\" style=\"color:#1a3a5c; text-decoration:underline;\">")
          .append(escapeHtml(featuredTitle(article))).append("</a></h2>\n");

        // Source · date meta
        if (!meta.isEmpty()) {
            sb.append("  <div style=\"font-size:0.83em; color:#666; margin-bottom:14px; font-family:Arial,sans-serif;\">")
              .append(escapeHtml(meta)).append("</div>\n");
        }

        // Rationale pull-quote
        sb.append("  <blockquote style=\"margin:0 0 16px 0; padding:10px 16px; border-left:3px solid #0066cc; ")
          .append("background:#ddeeff; border-radius:0 4px 4px 0; font-size:0.88em; ")
          .append("font-style:italic; color:#2a4a7f; line-height:1.5; font-family:Arial,sans-serif;\">")
          .append(escapeHtml(score.rationale())).append("</blockquote>\n");

        // Formatted body (LLM-enhanced with entity bolding)
        if (!formattedBody.isEmpty()) {
            sb.append("  <div style=\"font-size:0.9em; line-height:1.75; color:#333; font-family:Arial,sans-serif;\">")
              .append(formattedBody).append("</div>\n");
        }

        sb.append("</div>\n");

        String result = sb.toString();
        log.debug("renderFeaturedArticle() | return={} chars", result.length());
        return result;
    }

    /**
     * Calls the formatting port to get entity-bolded paragraphs, falling back
     * to a plain-text preview if the LLM call fails or the body is empty.
     */
    private String buildFormattedBody(NewsArticle article) {
        log.debug("buildFormattedBody() | articleId={}", article.articleId());

        if (article.bodyText() == null || article.bodyText().isBlank()) {
            log.debug("buildFormattedBody() | return=empty (no body)");
            return "";
        }

        try {
            String formatted = bodyFormattingPort.formatWithEntityBolding(article.title(), article.bodyText());
            if (formatted != null && !formatted.isBlank()) {
                String result = convertMarkdownToHtml(formatted);
                log.debug("buildFormattedBody() | return=formatted {} chars", result.length());
                return result;
            }
        } catch (Exception e) {
            log.warn("buildFormattedBody() | formatting failed — falling back to body preview", e);
        }

        // Fallback: render raw body as a single indented paragraph
        String preview = buildBodyPreview(article);
        if (preview.isEmpty()) return "";
        String result = "<p style=\"padding-left:16px; margin:0;\">" + escapeHtml(preview) + "</p>";
        log.debug("buildFormattedBody() | return=fallback {} chars", result.length());
        return result;
    }

    /**
     * Converts text containing {@code **bold**} markers to indented HTML paragraphs
     * with {@code <strong>} tags. Paragraphs are split on blank lines.
     */
    private String convertMarkdownToHtml(String text) {
        if (text == null || text.isBlank()) return "";

        String[] paragraphs = text.split("\\n\\n+");
        StringBuilder sb = new StringBuilder();
        for (String para : paragraphs) {
            String trimmed = para.trim().replace("\n", " ");
            if (trimmed.isEmpty()) continue;

            // HTML-escape first (** markers survive — they contain no special HTML chars)
            String escaped = trimmed
                    .replace("&",  "&amp;")
                    .replace("<",  "&lt;")
                    .replace(">",  "&gt;")
                    .replace("\"", "&quot;");

            // Convert **entity** → <strong>entity</strong>
            String bolded = escaped.replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>");

            sb.append("<p style=\"padding-left:16px; margin:0 0 12px 0;\">").append(bolded).append("</p>\n");
        }
        return sb.toString();
    }

    private String renderFeaturedPlainText(NewsArticle article, ScoredArticle score) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ARTICLE OF THE DAY (Significance: ").append(score.score()).append("/10) ===\n\n");
        sb.append(article.title()).append("\n");
        String meta = buildArticleMeta(article);
        if (!meta.isEmpty()) sb.append(meta).append("\n");
        if (article.url() != null) sb.append(article.url().toString()).append("\n");
        sb.append("\nWhy it matters: ").append(score.rationale()).append("\n");
        if (article.bodyText() != null && !article.bodyText().isBlank()) {
            String cleaned = cleanBodyText(article.bodyText());
            if (!cleaned.isEmpty()) sb.append("\n").append(cleaned);
        }
        sb.append("\n\n");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Existing rendering (unchanged behaviour)
    // -------------------------------------------------------------------------

    /** Returns a human-readable display title; substitutes sourceName when title is a bare domain. */
    private String featuredTitle(NewsArticle article) {
        String title = article.title();
        if (isDomainTitle(title)) {
            String src = article.sourceName();
            return (src != null && !src.isBlank()) ? src : title;
        }
        return title;
    }

    /** Returns true when a title is a bare domain name rather than an article headline. */
    private boolean isDomainTitle(String title) {
        if (title == null) return false;
        String base = title.split("\\s[—\\-]\\s")[0].trim();
        return base.matches("[a-z0-9][a-z0-9.\\-]*\\.[a-z]{2,}(/[\\S]*)?");
    }

    private List<NewsArticle> filterAndDedup(List<NewsArticle> articles) {
        log.debug("filterAndDedup() | inputSize={}", articles.size());

        Set<String> seenTitles = new HashSet<>();
        List<NewsArticle> result = new ArrayList<>();

        for (NewsArticle article : articles) {
            String tier = article.sourceTier();
            if (tier != null && tier.equals("COMPETITOR")) continue;
            if (!articleQualityFilter.isUsable(article)) continue;
            String title = article.title();
            if (isNonsenseTitle(title)) continue;
            String normalizedTitle = title.trim().toLowerCase();
            if (seenTitles.contains(normalizedTitle)) continue;
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
        sb.append("<div style=\"margin-bottom:36px; border-radius:8px; box-shadow:0 2px 6px rgba(0,0,0,0.08); overflow:hidden;\">\n");

        int index = 0;
        for (NewsArticle article : articles) {
            index++;
            String articleTitle = article.title() != null ? article.title() : "(no title)";
            String url          = article.url() != null ? article.url().toString() : "#";
            String borderTop    = index == 1 ? "" : " border-top:1px solid #e4eaf1;";

            sb.append("  <div id=\"article-").append(index)
              .append("\" style=\"background:#fff; padding:18px 22px;").append(borderTop).append("\">\n");
            sb.append("    <div style=\"font-size:1.05em; font-weight:bold; margin-bottom:6px;\">")
              .append("<a href=\"").append(escapeHtml(url))
              .append("\" target=\"_blank\" rel=\"noopener\" style=\"color:#1a3a5c; text-decoration:underline;\">")
              .append(escapeHtml(articleTitle)).append("</a>");

            String meta = buildArticleMeta(article);
            if (!meta.isEmpty()) {
                sb.append(" <span style=\"font-weight:normal; font-size:0.85em; color:#666; font-family:Arial,sans-serif;\">- ")
                  .append(escapeHtml(meta)).append("</span>");
            }
            sb.append("</div>\n");

            String bodyPreview = buildBodyPreview(article);
            if (!bodyPreview.isEmpty() && !isRedundantWithTitle(articleTitle, bodyPreview)) {
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

    private String renderSectionedCards(List<NewsArticle> todaysItems, List<NewsArticle> discoveredItems) {
        log.debug("renderSectionedCards() | today={}, discovered={}", todaysItems.size(), discoveredItems.size());

        StringBuilder sb = new StringBuilder();
        if (!todaysItems.isEmpty()) {
            sb.append("<h2 style=\"color:#1a3a5c; font-size:1.15em; margin:0 0 12px; padding-bottom:8px; border-bottom:2px solid #0066cc;\">")
              .append("Today&rsquo;s Intelligence</h2>\n");
            sb.append(renderArticleCards(todaysItems));
        }
        if (!discoveredItems.isEmpty()) {
            sb.append("<h2 style=\"color:#666; font-size:1.05em; margin:24px 0 12px; padding-bottom:8px; border-bottom:1px solid #ccc;\">")
              .append("Also Discovered</h2>\n");
            sb.append(renderArticleCards(discoveredItems));
        }

        String result = sb.toString();
        log.debug("renderSectionedCards() | return={} chars", result.length());
        return result;
    }

    private String renderSectionedPlainText(List<NewsArticle> todaysItems, List<NewsArticle> discoveredItems) {
        StringBuilder sb = new StringBuilder();
        if (!todaysItems.isEmpty()) {
            sb.append("=== TODAY'S INTELLIGENCE ===\n\n").append(renderPlainText(todaysItems));
        }
        if (!discoveredItems.isEmpty()) {
            sb.append("=== ALSO DISCOVERED ===\n\n").append(renderPlainText(discoveredItems));
        }
        return sb.toString();
    }

    private String buildArticleMeta(NewsArticle article) {
        StringBuilder meta = new StringBuilder();
        if (article.author() != null && !article.author().isBlank()) {
            meta.append(article.author());
        }
        if (article.publishedAt() != null) {
            if (meta.length() > 0) meta.append(", ");
            meta.append(article.publishedAt().atZone(ZoneOffset.UTC).toLocalDate().format(DISPLAY_FMT));
        }
        return meta.toString();
    }

    private String buildBodyPreview(NewsArticle article) {
        String body = article.bodyText();
        if (body == null || body.isBlank()) return "";

        String cleaned = cleanBodyText(body);
        if (cleaned.length() <= BODY_PREVIEW_MAX) return cleaned;

        String truncated   = cleaned.substring(0, BODY_PREVIEW_MAX);
        int    lastPeriod  = truncated.lastIndexOf('.');
        return lastPeriod > BODY_PREVIEW_MAX / 2
                ? truncated.substring(0, lastPeriod + 1)
                : truncated.trim() + "...";
    }

    /** Strips HTML tags and decodes common entities from raw body text. */
    private String cleanBodyText(String body) {
        return body.replaceAll("<[^>]+>", "")
                   .replaceAll("&nbsp;",  " ")
                   .replaceAll("&amp;",   "&")
                   .replaceAll("&lt;",    "<")
                   .replaceAll("&gt;",    ">")
                   .replaceAll("&quot;",  "\"")
                   .replaceAll("&#?\\w+;", " ")
                   .replaceAll("\\s+",    " ")
                   .trim();
    }

    private String renderPlainText(List<NewsArticle> articles) {
        StringBuilder sb = new StringBuilder();
        int index = 0;
        for (NewsArticle article : articles) {
            index++;
            sb.append(index).append(". ").append(article.title());
            if (article.url() != null) sb.append("\n   ").append(article.url().toString());
            if (article.bodyText() != null && !article.bodyText().isBlank()) {
                String cleaned = cleanBodyText(article.bodyText());
                if (cleaned.length() > BODY_PREVIEW_MAX) {
                    String truncated  = cleaned.substring(0, BODY_PREVIEW_MAX);
                    int    lastPeriod = truncated.lastIndexOf('.');
                    cleaned = lastPeriod > BODY_PREVIEW_MAX / 2
                            ? truncated.substring(0, lastPeriod + 1) : truncated.trim() + "...";
                }
                if (!isRedundantWithTitle(article.title(), cleaned)) {
                    sb.append("\n   ").append(cleaned);
                }
            }
            sb.append("\n\n");
        }
        return sb.toString();
    }

    /**
     * True when {@code preview} adds no meaningful content beyond {@code title} —
     * e.g. an RSS lead paragraph that just restates the headline in sentence form.
     * Compares normalized (lowercase, punctuation-stripped) text so a preview that
     * is the title plus only incidental formatting differences is treated as a
     * duplicate; a preview with substantially more content is kept.
     */
    private boolean isRedundantWithTitle(String title, String preview) {
        String normTitle   = normalizeForComparison(title);
        String normPreview = normalizeForComparison(preview);
        if (normTitle.isEmpty() || normPreview.isEmpty()) return false;
        if (normTitle.equals(normPreview)) return true;
        if (normTitle.startsWith(normPreview)) return true;
        if (normPreview.startsWith(normTitle)) {
            return (normPreview.length() - normTitle.length()) < 30;
        }
        return false;
    }

    private String normalizeForComparison(String text) {
        if (text == null) return "";
        return text.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** Plain-text equivalent of the HTML CTA footer + unsubscribe line appended in {@link #wrapInEmailLayout}. */
    private String renderPlainTextFooter() {
        return "\n---\nWANT DEEPER AI ANALYSIS?\n\n"
                + "Subscribe for full AI-powered newsletters with expert synthesis, vendor comparisons, and research insights.\n"
                + PromotionalFooter.BUTTONS_PLAIN_TEXT + "\n\n"
                + "You are receiving this because you signed up for the free AI Healthcare digest.\n"
                + "Unsubscribe: https://app.bigskylabs.ai/unsubscribe\n";
    }

    private String wrapInEmailLayout(String bodyHtml, LocalDate today, int articleCount, String subject) {
        log.debug("wrapInEmailLayout() | bodyLength={}, today={}, articleCount={}, subject={}",
                  bodyHtml.length(), today, articleCount, subject);

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        sb.append("  <meta charset=\"UTF-8\">\n");
        sb.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("  <title>").append(subject).append("</title>\n");
        sb.append("</head>\n<body style=\"margin:0; padding:0; background:#f4f6f9; font-family:Arial,sans-serif;\">\n");
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f4f6f9;\">\n");
        sb.append("<tr><td align=\"center\" style=\"padding:20px 10px;\">\n");
        sb.append("<table width=\"640\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:640px; width:100%;\">\n");

        // Header
        sb.append("<tr><td style=\"background:#1a1a2e; color:white; padding:20px 24px; border-radius:8px 8px 0 0;\">\n");
        sb.append("  <h1 style=\"margin:0; font-size:1.3em; color:white;\">").append(subject).append("</h1>\n");
        sb.append("  <p style=\"margin:8px 0 0; font-size:0.85em; color:#b0b8c8; line-height:1.4;\">");
        sb.append("Your free daily digest of AI in healthcare &mdash; sourced from 69 feeds across regulatory, clinical, ");
        sb.append("and commercial developments. Subscribers get a deeper weekly briefing with full AI-powered analysis.");
        sb.append("</p>\n</td></tr>\n");

        // Body
        sb.append("<tr><td style=\"background:white; padding:24px;\">\n");
        sb.append(bodyHtml);
        sb.append("\n</td></tr>\n");

        // CTA footer
        sb.append("<tr><td style=\"background:#f0f7ff; padding:20px 24px; border-top:2px solid #0066cc;\">\n");
        sb.append("  <h3 style=\"margin:0 0 8px; color:#1a1a2e; font-size:1em;\">Want deeper AI analysis?</h3>\n");
        sb.append("  <p style=\"margin:0 0 12px; font-size:0.9em; color:#555;\">Subscribe for full AI-powered newsletters with expert synthesis, vendor comparisons, and research insights.</p>\n");
        sb.append("  ").append(PromotionalFooter.BUTTONS_HTML).append("\n");
        sb.append("</td></tr>\n");

        // Unsubscribe footer
        sb.append("<tr><td style=\"padding:16px 24px; text-align:center; font-size:0.75em; color:#999; border-radius:0 0 8px 8px;\">\n");
        sb.append("  You are receiving this because you signed up for the free AI Healthcare digest.<br>\n");
        sb.append("  <a href=\"https://app.bigskylabs.ai/unsubscribe\" style=\"color:#999; text-decoration:underline; font-size:0.9em;\">Unsubscribe</a>\n");
        sb.append("</td></tr>\n");

        sb.append("</table>\n</td></tr>\n</table>\n</body>\n</html>\n");

        String result = sb.toString();
        log.debug("wrapInEmailLayout() | return={} chars", result.length());
        return result;
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    /** Finds the first article in {@code list} matching {@code articleId}, or null. */
    private NewsArticle findByArticleId(List<NewsArticle> list, String articleId) {
        for (NewsArticle a : list) {
            if (a.articleId().equals(articleId)) return a;
        }
        return null;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&",  "&amp;")
                   .replace("<",  "&lt;")
                   .replace(">",  "&gt;")
                   .replace("\"", "&quot;");
    }
}
