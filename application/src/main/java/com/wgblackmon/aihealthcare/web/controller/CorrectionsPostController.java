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

/**
 * Thymeleaf controller that generates a Corrections Daily Post from recent
 * wiki-detected contradictions, formatted for review and copy-paste use.
 *
 * <p>Serves {@code GET /dashboard/corrections} by fetching contradictions from
 * the last 30 days via {@link WikiQueryPort}, looking up their source articles
 * via {@link ArticleIngestionPort}, filtering for LEGAL/POLICY/FDA relevance and
 * quality (sourceWeight ≥ 0.7), and building a model of paired correction entries.
 *
 * <p>Each entry shows:
 * <ul>
 *   <li>The <strong>prior claim</strong> (bolded) with the original source article title,
 *       snippet, and link.</li>
 *   <li>The <strong>corrected claim</strong> (bolded) with the correcting article title,
 *       snippet, and link.</li>
 * </ul>
 *
 * <p>No LLM calls are made; all filtering uses keyword matching on existing data.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-22
 * @updated 2026-08-22
 */
@Slf4j
@Controller
public class CorrectionsPostController {

    private static final int LOOKBACK_DAYS    = 30;
    private static final int MAX_ENTRIES      = 10;
    private static final int SNIPPET_MAX_CHARS = 200;
    private static final double MIN_WEIGHT    = 0.7;

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
        String dateLabel = DATE_FMT.format(LocalDate.now(ZoneId.of("America/Chicago")));

        model.addAttribute("entries", entries);
        model.addAttribute("entryCount", entries.size());
        model.addAttribute("dateLabel", dateLabel);
        model.addAttribute("lookbackDays", LOOKBACK_DAYS);

        log.debug("correctionsPost() | return=corrections-post, entries={}", entries.size());
        return "corrections-post";
    }

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
                    c.priorClaim(),
                    c.newClaim(),
                    priorArticles,
                    newArticles
            ));
        }

        log.debug("buildEntries() | return={}", result.size());
        return result;
    }

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
     * is LEGAL/POLICY/FDA-relevant. Claim text is the primary signal — if the
     * wiki-detected contradiction itself mentions a priority keyword the entry
     * qualifies regardless of article quality (source articles may be low-weight
     * or may have aged out of the DB).
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
     * A paired correction entry: prior claim + correction claim, each with source articles.
     *
     * @param priorClaim    the original claim that was contradicted (display bold)
     * @param correctedClaim the new/corrected claim (display bold)
     * @param priorArticles source articles supporting the prior claim
     * @param newArticles   source articles supporting the correction
     */
    public record CorrectionEntry(
            String priorClaim,
            String correctedClaim,
            List<NewsArticle> priorArticles,
            List<NewsArticle> newArticles
    ) {}
}
