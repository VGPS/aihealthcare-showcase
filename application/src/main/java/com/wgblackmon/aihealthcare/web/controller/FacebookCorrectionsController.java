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
 * Thymeleaf controller that generates a Facebook-ready corrections post from
 * recent wiki-detected contradictions.
 *
 * <p>Serves {@code GET /dashboard/facebook-corrections}. Produces two copy blocks:
 * <ul>
 *   <li>Post body (≤ 390 chars) — fits above Facebook's "See more" fold; shows the
 *       single most important correction. Links are allowed on Facebook without a
 *       reach penalty, but are kept in the comment for clean formatting.</li>
 *   <li>Comment block — full correction list, wiki deep-links, source article URLs,
 *       and app CTA. Post this as the first comment after publishing.</li>
 * </ul>
 *
 * <p>Priority filtering matches {@link CorrectionsPostController}: LEGAL/POLICY/FDA
 * claim keywords are the primary signal; high-weight article keywords are secondary.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-22
 * @updated 2026-09-12
 */
@Slf4j
@Controller
public class FacebookCorrectionsController {

    private static final int    LOOKBACK_DAYS    = 30;
    private static final int    MAX_ENTRIES      = 10;
    private static final double MIN_WEIGHT       = 0.7;
    private static final int    POST_BODY_LIMIT  = 390;  // Facebook "See more" fold
    private static final int    COMMENT_LIMIT    = 7900; // Facebook comment max ~8,000

    private static final String APP_URL  = "https://app.bigskylabs.ai";
    private static final String SITE_URL = "https://bigskylabs.ai";

    private static final DateTimeFormatter DATE_FMT =
            DisplayFormats.LONG_DATE;

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

    public FacebookCorrectionsController(WikiQueryPort wikiQueryPort,
                                         ArticleIngestionPort articleIngestionPort) {
        log.debug("FacebookCorrectionsController() | wikiQueryPort={}, articleIngestionPort={}",
                wikiQueryPort, articleIngestionPort);
        this.wikiQueryPort = wikiQueryPort;
        this.articleIngestionPort = articleIngestionPort;
    }

    /**
     * Renders the Facebook Corrections Post page.
     *
     * @param model Thymeleaf model
     * @return the "facebook-corrections" view name
     */
    @GetMapping("/dashboard/facebook-corrections")
    public String facebookCorrections(Model model) {
        log.debug("facebookCorrections() | entry");

        Instant since = Instant.now().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);
        List<Contradiction> all = wikiQueryPort.recentContradictions(since);

        List<FbEntry> entries = buildEntries(all);
        String dateLabel    = DATE_FMT.format(LocalDate.now(ZoneId.of("America/Chicago")));
        String postBody     = buildPostBody(entries, dateLabel);
        String commentBlock = buildCommentBlock(entries);

        model.addAttribute("entries",           entries);
        model.addAttribute("entryCount",        entries.size());
        model.addAttribute("dateLabel",         dateLabel);
        model.addAttribute("lookbackDays",      LOOKBACK_DAYS);
        model.addAttribute("postBody",          postBody);
        model.addAttribute("commentBlock",      commentBlock);
        model.addAttribute("postBodyLength",    postBody.length());
        model.addAttribute("commentBlockLength", commentBlock.length());

        log.debug("facebookCorrections() | return=facebook-corrections, entries={}", entries.size());
        return "facebook-corrections";
    }

    // ------------------------------------------------------------------
    // Post body — single lead correction, fits above "See more" fold
    // ------------------------------------------------------------------

    private String buildPostBody(List<FbEntry> entries, String dateLabel) {
        log.debug("buildPostBody() | entries={}", entries.size());

        if (entries.isEmpty()) {
            String result = "No AI healthcare corrections found for " + dateLabel + ".\n\n"
                    + SITE_URL + " — 7-day free demo";
            log.debug("buildPostBody() | return={}", result.length());
            return result;
        }

        FbEntry lead = entries.get(0);
        String prior     = truncate(cleanText(lead.priorClaim()),     140);
        String corrected = truncate(cleanText(lead.correctedClaim()), 140);

        String more = entries.size() > 1
                ? "\n+" + (entries.size() - 1) + " more corrections in comment ↓"
                : "\nSources in comment ↓";

        String body = "⚠️ AI Healthcare Correction — " + dateLabel + "\n\n"
                + "“" + prior + "”\n\n"
                + "↓ corrected to:\n\n"
                + "“" + corrected + "”"
                + more + "\n" + SITE_URL;

        // Trim to hard limit if claims were longer than expected
        if (body.length() > POST_BODY_LIMIT) {
            body = body.substring(0, POST_BODY_LIMIT - 3) + "...";
        }

        log.debug("buildPostBody() | return length={}", body.length());
        return body;
    }

    // ------------------------------------------------------------------
    // Comment block — all entries with wiki links + source URLs
    // ------------------------------------------------------------------

    private String buildCommentBlock(List<FbEntry> entries) {
        log.debug("buildCommentBlock() | entries={}", entries.size());

        StringBuilder sb = new StringBuilder();
        sb.append("Sources & Wiki References:\n\n");
        sb.append("⚠️ DISCLAIMER: These are AI wiki-detected contradictions sourced from "
                + "published healthcare articles. They have NOT been individually verified.\n\n");

        int num = 1;
        for (FbEntry entry : entries) {
            String header = num + ". " + truncate(cleanText(entry.priorClaim()), 100)
                    + " → " + truncate(cleanText(entry.correctedClaim()), 100) + "\n";
            if (sb.length() + header.length() > COMMENT_LIMIT) {
                break;
            }
            sb.append(header);

            String wikiLink = "   Wiki: " + APP_URL + "/wiki/" + entry.pageSlug() + "\n";
            if (sb.length() + wikiLink.length() <= COMMENT_LIMIT) {
                sb.append(wikiLink);
            }

            for (NewsArticle a : entry.priorArticles()) {
                String link = "   " + a.url() + "\n";
                if (sb.length() + link.length() > COMMENT_LIMIT) {
                    break;
                }
                sb.append(link);
            }
            for (NewsArticle a : entry.newArticles()) {
                String link = "   " + a.url() + "\n";
                if (sb.length() + link.length() > COMMENT_LIMIT) {
                    break;
                }
                sb.append(link);
            }
            sb.append("\n");
            num++;
        }

        String footer = SITE_URL + " — Daily AI healthcare intelligence. 7-day free demo.";
        if (sb.length() + footer.length() <= COMMENT_LIMIT) {
            sb.append(footer);
        }

        String result = sb.toString();
        log.debug("buildCommentBlock() | return length={}", result.length());
        return result;
    }

    // ------------------------------------------------------------------
    // Entry building — same priority logic as CorrectionsPostController
    // ------------------------------------------------------------------

    private List<FbEntry> buildEntries(List<Contradiction> contradictions) {
        log.debug("buildEntries() | contradictions={}", contradictions.size());
        List<FbEntry> result = new ArrayList<>();

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

            result.add(new FbEntry(
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

    String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text != null ? text : "";
        }
        int cut = text.lastIndexOf(' ', maxChars - 1);
        return (cut > 0 ? text.substring(0, cut) : text.substring(0, maxChars - 1)) + "…";
    }

    /**
     * A correction entry for Facebook output.
     *
     * @param pageSlug       wiki page slug for deep-link
     * @param priorClaim     original claim
     * @param correctedClaim corrected claim
     * @param priorArticles  source articles for the prior claim
     * @param newArticles    source articles for the correction
     */
    public record FbEntry(
            String pageSlug,
            String priorClaim,
            String correctedClaim,
            List<NewsArticle> priorArticles,
            List<NewsArticle> newArticles
    ) {}
}
