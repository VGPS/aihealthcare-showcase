package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.Contradiction;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.SourceRef;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WikiQueryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Thymeleaf controller that generates a Corrections Daily Post from recent
 * wiki-detected contradictions, formatted for LinkedIn copy-paste.
 *
 * <p>Serves {@code GET /dashboard/corrections} by fetching contradictions from
 * the last 30 days via {@link WikiQueryPort}, looking up their source articles
 * via {@link ArticleIngestionPort}, filtering for LEGAL/POLICY/FDA relevance and
 * quality (sourceWeight ≥ 0.7), and building LinkedIn-ready copy blocks plus a
 * visual preview of the correction pairs.
 *
 * <p>Produces two copy blocks:
 * <ul>
 *   <li>Post body (≤ 2,900 chars, no links) — paste directly into LinkedIn.</li>
 *   <li>Links block (≤ 1,200 chars) — paste as the first comment after publishing.</li>
 * </ul>
 *
 * <p>No LLM calls are made; all filtering uses keyword matching on existing wiki data.
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-08-22
 * @updated 2026-08-25
 */
@Slf4j
@Controller
public class CorrectionsPostController {

    private static final int    LOOKBACK_DAYS     = 30;
    private static final int    MAX_ENTRIES       = 10;
    private static final int    SNIPPET_MAX_CHARS = 200;
    private static final double MIN_WEIGHT        = 0.7;
    private static final int    POST_BODY_LIMIT   = 2900;
    // LinkedIn comment limit is 1,250 chars; reserve ~200 for footer + buffer
    private static final int    LINKS_BLOCK_LIMIT = 1050;

    private static final Set<String> TRACKING_PARAMS = Set.of(
            "utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term",
            "fc", "ff", "v", "oc", "hl", "gl", "ceid"
    );

    private static final String APP_URL  = "https://app.bigskylabs.ai";
    private static final String SITE_URL = "https://bigskylabs.ai";

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMMM d, yyyy");

    private static final String[] PRIORITY_KEYWORDS = {
        "fda", "cms", "ftc", "doj", "hhs", "onc",
        "lawsuit", "litigation", "court", "settlement",
        "legislation", "regulation", "rule", "mandate",
        "clearance", "approved", "denied", "recalled",
        "fine", "penalty", "investigation", "subpoena",
        "congress", "senate", "executive order", "white house",
        "hipaa", "510(k)", "de novo", "pma"
    };

    private final WikiQueryPort wikiQueryPort;
    private final ArticleIngestionPort articleIngestionPort;

    public CorrectionsPostController(WikiQueryPort wikiQueryPort,
                                     ArticleIngestionPort articleIngestionPort) {
        log.debug("CorrectionsPostController() | wikiQueryPort={}, articleIngestionPort={}",
                wikiQueryPort, articleIngestionPort);
        this.wikiQueryPort = wikiQueryPort;
        this.articleIngestionPort = articleIngestionPort;
    }

    /**
     * Renders the Corrections Daily Post page.
     *
     * @param model Thymeleaf model
     * @return the "corrections-post" view name
     */
    @GetMapping("/dashboard/corrections")
    public String correctionsPost(Model model) {
        log.debug("correctionsPost() | entry");

        Instant since = Instant.now().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);
        List<Contradiction> all = wikiQueryPort.recentContradictions(since);

        List<CorrectionEntry> entries = buildEntries(all);
        String dateLabel  = DATE_FMT.format(LocalDate.now(ZoneId.of("America/Chicago")));
        String postBody   = buildPostBody(entries, dateLabel);
        String linksBlock = buildLinksBlock(entries);

        model.addAttribute("entries",          entries);
        model.addAttribute("entryCount",       entries.size());
        model.addAttribute("dateLabel",        dateLabel);
        model.addAttribute("lookbackDays",     LOOKBACK_DAYS);
        model.addAttribute("postBody",         postBody);
        model.addAttribute("linksBlock",       linksBlock);
        model.addAttribute("postBodyLength",   postBody.length());
        model.addAttribute("linksBlockLength", linksBlock.length());

        log.debug("correctionsPost() | return=corrections-post, entries={}", entries.size());
        return "corrections-post";
    }

    // ------------------------------------------------------------------
    // Post body builder
    // ------------------------------------------------------------------

    private String buildPostBody(List<CorrectionEntry> entries, String dateLabel) {
        log.debug("buildPostBody() | entries={}", entries.size());

        String header = "AI Healthcare — Corrections & Reversals\n"
                + dateLabel + "\n\n"
                + "These are AI wiki-detected contradictions sourced from published healthcare "
                + "articles. They have NOT been individually verified.\n"
                + "Full wiki: " + APP_URL + "/wiki\n";

        String footer = "\n―――\n\n"
                + "Follow for daily AI healthcare intelligence.\n"
                + "7-day free demo: " + SITE_URL;

        StringBuilder body = new StringBuilder(header);

        for (CorrectionEntry entry : entries) {
            String block = "\n―――\n\n"
                    + "ORIGINAL CLAIM:\n"
                    + "“" + cleanText(entry.priorClaim()) + "”\n\n"
                    + "CORRECTED BY:\n"
                    + "“" + cleanText(entry.correctedClaim()) + "”\n";

            if (body.length() + block.length() + footer.length() > POST_BODY_LIMIT) {
                break;
            }
            body.append(block);
        }

        body.append(footer);

        String result = body.toString();
        if (result.length() > POST_BODY_LIMIT) {
            int cut = result.lastIndexOf('\n', POST_BODY_LIMIT - footer.length());
            result = (cut > 0 ? result.substring(0, cut) : result.substring(0, POST_BODY_LIMIT - footer.length()))
                    + footer;
        }

        log.debug("buildPostBody() | return length={}", result.length());
        return result;
    }

    // ------------------------------------------------------------------
    // Links block builder
    // ------------------------------------------------------------------

    private String buildLinksBlock(List<CorrectionEntry> entries) {
        log.debug("buildLinksBlock() | entries={}", entries.size());

        StringBuilder sb = new StringBuilder();
        sb.append("Sources & Wiki References:\n\n");

        for (CorrectionEntry entry : entries) {
            String wikiLine = "Wiki: " + APP_URL + "/wiki/" + entry.pageSlug() + "\n";
            if (sb.length() + wikiLine.length() > LINKS_BLOCK_LIMIT) {
                sb.append("More: ").append(APP_URL).append("/wiki\n");
                break;
            }
            sb.append(wikiLine);

            for (NewsArticle a : entry.priorArticles()) {
                String link = cleanUrlForDisplay(a.url().toString()) + "\n";
                if (sb.length() + link.length() > LINKS_BLOCK_LIMIT) {
                    break;
                }
                sb.append(link);
            }
            for (NewsArticle a : entry.newArticles()) {
                String link = cleanUrlForDisplay(a.url().toString()) + "\n";
                if (sb.length() + link.length() > LINKS_BLOCK_LIMIT) {
                    break;
                }
                sb.append(link);
            }
        }

        String siteFooter = "\n" + SITE_URL + " — 7-day free demo";
        if (sb.length() + siteFooter.length() <= LINKS_BLOCK_LIMIT) {
            sb.append(siteFooter);
        }

        String result = sb.toString();
        log.debug("buildLinksBlock() | return length={}", result.length());
        return result;
    }

    // ------------------------------------------------------------------
    // Entry building
    // ------------------------------------------------------------------

    private List<CorrectionEntry> buildEntries(List<Contradiction> contradictions) {
        log.debug("buildEntries() | contradictions={}", contradictions.size());
        List<CorrectionEntry> result = new ArrayList<>();

        for (Contradiction c : contradictions) {
            if (result.size() >= MAX_ENTRIES) {
                break;
            }

            List<NewsArticle> priorArticles = lookupArticles(extractIds(c.priorSources()));
            List<NewsArticle> newArticles   = lookupArticles(extractIds(c.newSources()));

            if (!isPriority(c, priorArticles, newArticles)) {
                continue;
            }

            NewsArticle best = bestArticle(newArticles);
            if (best == null) {
                best = bestArticle(priorArticles);
            }
            if (best == null) {
                continue;
            }

            result.add(new CorrectionEntry(
                    c.pageSlug(),
                    c.priorClaim(),
                    c.newClaim(),
                    priorArticles,
                    newArticles
            ));
        }

        log.debug("buildEntries() | return={}", result.size());
        return result;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private List<String> extractIds(List<SourceRef> sources) {
        List<String> ids = new ArrayList<>();
        for (SourceRef s : sources) {
            ids.add(s.articleId());
        }
        return ids;
    }

    private List<NewsArticle> lookupArticles(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<NewsArticle> found = articleIngestionPort.fetchArticlesByIds(ids);
        return found != null ? found : List.of();
    }

    /**
     * Returns true if the contradiction's claims or any high-weight source article
     * is LEGAL/POLICY/FDA-relevant. Claim text is the primary signal.
     */
    private boolean isPriority(Contradiction c,
                                List<NewsArticle> priorArticles,
                                List<NewsArticle> newArticles) {
        String claimText = (c.priorClaim() + " " + c.newClaim()).toLowerCase();
        if (containsAny(claimText, PRIORITY_KEYWORDS)) {
            return true;
        }

        List<NewsArticle> all = new ArrayList<>();
        all.addAll(priorArticles);
        all.addAll(newArticles);

        for (NewsArticle a : all) {
            if (a.sourceWeight() >= MIN_WEIGHT) {
                String text = ((a.title() != null ? a.title() : "") + " "
                        + (a.bodyText() != null ? a.bodyText() : "")).toLowerCase();
                if (containsAny(text, PRIORITY_KEYWORDS)) {
                    return true;
                }
            }
        }

        return false;
    }

    private NewsArticle bestArticle(List<NewsArticle> articles) {
        NewsArticle best = null;
        for (NewsArticle a : articles) {
            if (best == null || a.sourceWeight() > best.sourceWeight()) {
                best = a;
            }
        }
        return best;
    }

    private boolean containsAny(String text, String[] keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Strips UTM/tracking query params and #snapshot fragments to shorten URLs
     * for the LinkedIn comment block.
     */
    private String cleanUrlForDisplay(String url) {
        log.debug("cleanUrlForDisplay() | url={}", url);
        if (url == null || url.isBlank()) {
            return url;
        }
        int hashIdx = url.indexOf('#');
        if (hashIdx > 0 && url.substring(hashIdx).startsWith("#snapshot")) {
            url = url.substring(0, hashIdx);
        }
        int queryIdx = url.indexOf('?');
        if (queryIdx > 0) {
            String query = url.substring(queryIdx + 1);
            String[] params = query.split("&");
            boolean allTracking = true;
            for (String param : params) {
                String name = param.split("=")[0].toLowerCase();
                if (!TRACKING_PARAMS.contains(name)) {
                    allTracking = false;
                    break;
                }
            }
            if (allTracking) {
                url = url.substring(0, queryIdx);
            }
        }
        log.debug("cleanUrlForDisplay() | return={}", url);
        return url;
    }

    /**
     * Strips HTML tags, decodes common HTML entities, and collapses whitespace.
     */
    String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;",   " ")
                .replace("&amp;",    "&")
                .replace("&lt;",     "<")
                .replace("&gt;",     ">")
                .replace("&quot;",   "\"")
                .replace("&apos;",   "'")
                .replace("&mdash;",  "—")
                .replace("&ndash;",  "–")
                .replace("&hellip;", "…")
                .replace("&ldquo;",  "“")
                .replace("&rdquo;",  "”")
                .replace("&lsquo;",  "‘")
                .replace("&rsquo;",  "’")
                .replaceAll("&#\\d+;", " ")
                .replaceAll("&[a-zA-Z]{2,8};", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Extracts a clean snippet from article body text, capped at SNIPPET_MAX_CHARS.
     */
    String extractSnippet(String bodyText) {
        String cleaned = cleanText(bodyText);
        if (cleaned.length() <= SNIPPET_MAX_CHARS) {
            return cleaned;
        }
        String truncated = cleaned.substring(0, SNIPPET_MAX_CHARS);
        int lastSpace = truncated.lastIndexOf(' ');
        if (lastSpace > 0) {
            truncated = truncated.substring(0, lastSpace);
        }
        return truncated + "…";
    }

    /**
     * A paired correction entry: prior claim + corrected claim, each with source articles.
     *
     * @param pageSlug       wiki page slug (used for wiki deep-link)
     * @param priorClaim     the original claim that was contradicted (display bold)
     * @param correctedClaim the new/corrected claim (display bold)
     * @param priorArticles  source articles supporting the prior claim
     * @param newArticles    source articles supporting the correction
     */
    public record CorrectionEntry(
            String pageSlug,
            String priorClaim,
            String correctedClaim,
            List<NewsArticle> priorArticles,
            List<NewsArticle> newArticles
    ) {}
}
