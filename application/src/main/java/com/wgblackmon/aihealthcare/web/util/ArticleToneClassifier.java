package com.wgblackmon.aihealthcare.web.util;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Shared tone classifier for AI healthcare articles, used by social-media post
 * generators (LinkedIn, Facebook) and any other web-layer component that needs
 * per-article tone signals.
 *
 * <p>Classifies each article into one of five tone tiers based purely on keyword
 * matching — no LLM calls are made:
 * <ul>
 *   <li><strong>UGLY</strong> 🚨 — immediate patient safety risk or legal jeopardy
 *       (recall, fraud, death, data breach, ban)</li>
 *   <li><strong>BAD</strong> ⚠️ — regulatory concern or active investigation
 *       (penalty, warning, investigation, denied)</li>
 *   <li><strong>GOOD</strong> ✅ — positive development (clearance, approved,
 *       breakthrough, effective)</li>
 *   <li><strong>PROMO</strong> 🙄 — marketing or advertising language
 *       (proud to announce, best-in-class, press release)</li>
 *   <li><strong>NEUTRAL</strong> ℹ️ — informational, no strong signal</li>
 * </ul>
 *
 * <p>Precedence: UGLY &gt; BAD &gt; GOOD &gt; PROMO &gt; NEUTRAL, so genuine
 * news (e.g. an FDA clearance) always beats a marketing press release.
 *
 * <p>Also generates platform-appropriate hashtag blocks. Base tags are always
 * included; one dynamic tag is appended based on the dominant category or tone
 * of the article batch.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-23
 * @updated 2026-08-23
 */
@Slf4j
@Component
public class ArticleToneClassifier {

    // ------------------------------------------------------------------
    // Tone keyword sets
    // ------------------------------------------------------------------

    static final String[] UGLY_KEYWORDS = {
        "recall", "recalled", "safety alert", "adverse event",
        "lawsuit", "litigation", "criminal", "indictment", "fraud",
        "death", "died", "harm", "harmful", "injury", "injuries",
        "breach", "hack", "data leak", "data breach",
        "ban", "banned", "revoked", "unsafe"
    };

    static final String[] BAD_KEYWORDS = {
        "investigation", "subpoena", "penalty", "fine", "violation",
        "warning letter", "warning", "denied", "rejected",
        "delay", "delayed", "failure", "failed",
        "scandal", "misleading", "concern", "concerns",
        "lawsuit", "court", "class action", "settlement",
        "risk", "danger", "adverse"
    };

    static final String[] GOOD_KEYWORDS = {
        "approved", "clearance", "cleared", "authorized",
        "fda clears", "fda approves", "breakthrough",
        "promising", "effective", "efficacy", "successful", "success",
        "improves", "improvement", "better outcomes", "reduces", "prevents",
        "launched", "innovation", "advance", "advances",
        "study shows", "trial shows", "evidence shows", "results show",
        "saves", "saving", "benefit", "benefits", "cure", "treatment",
        "funding", "investment", "partnership", "collaboration"
    };

    static final String[] PROMO_KEYWORDS = {
        "proud to announce", "excited to announce", "thrilled to announce",
        "pleased to announce", "best-in-class", "industry-leading",
        "award-winning", "leading provider", "cutting-edge solution",
        "game-changing", "transformative solution", "revolutionary solution",
        "free trial", "request a demo", "sign up today", "contact us today",
        "press release", "pr newswire", "business wire", "globe newswire",
        "sponsorship", "sponsored by", "whitepaper", "ebook download",
        "webinar registration"
    };

    // ------------------------------------------------------------------
    // Category signals — used only for dynamic hashtag selection
    // ------------------------------------------------------------------

    private static final String[] LEGAL_SIGNALS = {
        "lawsuit", "litigation", "court", "indictment", "criminal charges",
        "hipaa violation", "patent infringement", "antitrust"
    };

    private static final String[] MARKETPLACE_SIGNALS = {
        "acqui", "merger", "acquisition", "funding round",
        "raised", "series a", "series b", "series c", "ipo", "buyout"
    };

    private static final String[] POLICY_SIGNALS = {
        "fda clears", "fda approves", "fda guidance",
        "cms rule", "cms proposes", "legislation", "signed into law",
        "510(k)", "de novo", "pma approval"
    };

    // ------------------------------------------------------------------
    // Hashtag base blocks
    // ------------------------------------------------------------------

    private static final String LINKEDIN_BASE =
            "#HealthcareAI #AIinHealthcare #DigitalHealth #HealthTech #MedicalInnovation";

    private static final String FACEBOOK_BASE =
            "#HealthcareAI #AIinHealthcare #DigitalHealth #HealthTech #BigSkyLabs";

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Classifies the tone of an article.
     *
     * @param article the article to classify
     * @return one of UGLY | BAD | GOOD | PROMO | NEUTRAL
     */
    public String classifyTone(NewsArticle article) {
        log.debug("classifyTone() | articleId={}", article.articleId());
        String text = buildSearchText(article);
        String result;
        if      (containsAny(text, UGLY_KEYWORDS))  { result = "UGLY"; }
        else if (containsAny(text, BAD_KEYWORDS))   { result = "BAD"; }
        else if (containsAny(text, GOOD_KEYWORDS))  { result = "GOOD"; }
        else if (containsAny(text, PROMO_KEYWORDS)) { result = "PROMO"; }
        else                                        { result = "NEUTRAL"; }
        log.debug("classifyTone() | return={}", result);
        return result;
    }

    /**
     * Maps a tone string to its display emoji with a trailing space.
     *
     * @param tone UGLY | BAD | GOOD | PROMO | NEUTRAL (or any other value → ℹ️)
     * @return emoji string with trailing space, ready to prepend to a title
     */
    public String toneEmoji(String tone) {
        log.debug("toneEmoji() | tone={}", tone);
        String result;
        if      ("UGLY".equals(tone))  { result = "🚨 "; }
        else if ("BAD".equals(tone))   { result = "⚠️ "; }
        else if ("GOOD".equals(tone))  { result = "✅ "; }
        else if ("PROMO".equals(tone)) { result = "🙄 "; }
        else                           { result = "ℹ️ "; }
        log.debug("toneEmoji() | return={}", result);
        return result;
    }

    /**
     * Convenience: classify then map to emoji.
     *
     * @param article the article to classify
     * @return emoji string with trailing space
     */
    public String toneEmoji(NewsArticle article) {
        log.debug("toneEmoji(article) | articleId={}", article.articleId());
        String result = toneEmoji(classifyTone(article));
        log.debug("toneEmoji(article) | return={}", result);
        return result;
    }

    /**
     * Returns a LinkedIn hashtag block for a batch of articles.
     * Five base tags always included; one dynamic tag appended based on the
     * dominant category or tone of the batch.
     *
     * @param articles the selected article batch (already filtered and sorted)
     * @return hashtag string ready to append to a LinkedIn post or comment
     */
    public String linkedInHashtags(List<NewsArticle> articles) {
        log.debug("linkedInHashtags() | articles={}", articles.size());
        String dynamic = dynamicHashtag(articles);
        String result  = dynamic.isBlank() ? LINKEDIN_BASE : LINKEDIN_BASE + " " + dynamic;
        log.debug("linkedInHashtags() | return={}", result);
        return result;
    }

    /**
     * Returns a Facebook hashtag block for a batch of articles.
     * Five base tags always included; one dynamic tag appended based on the
     * dominant category or tone of the batch.
     *
     * @param articles the selected article batch (already filtered and sorted)
     * @return hashtag string ready to append to a Facebook post or comment
     */
    public String facebookHashtags(List<NewsArticle> articles) {
        log.debug("facebookHashtags() | articles={}", articles.size());
        String dynamic = dynamicHashtag(articles);
        String result  = dynamic.isBlank() ? FACEBOOK_BASE : FACEBOOK_BASE + " " + dynamic;
        log.debug("facebookHashtags() | return={}", result);
        return result;
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Picks one dynamic hashtag by scanning for legal, marketplace, or policy
     * signals first, then falling back to tone. Returns blank when no signal
     * is found.
     */
    private String dynamicHashtag(List<NewsArticle> articles) {
        int legalCount       = 0;
        int marketplaceCount = 0;
        int policyCount      = 0;
        int uglyCount        = 0;
        int goodCount        = 0;

        for (NewsArticle a : articles) {
            String text = buildSearchText(a);
            String tone = classifyTone(a);
            if (containsAny(text, LEGAL_SIGNALS))       { legalCount++; }
            if (containsAny(text, MARKETPLACE_SIGNALS)) { marketplaceCount++; }
            if (containsAny(text, POLICY_SIGNALS))      { policyCount++; }
            if ("UGLY".equals(tone))                    { uglyCount++; }
            if ("GOOD".equals(tone))                    { goodCount++; }
        }

        // Category signals take priority over tone signals
        if (legalCount > 0 && legalCount >= marketplaceCount && legalCount >= policyCount) {
            return "#HealthcareLaw";
        }
        if (marketplaceCount > 0 && marketplaceCount >= policyCount) {
            return "#HealthTechDeals";
        }
        if (policyCount > 0) {
            return "#HealthPolicy";
        }
        if (uglyCount > 0) {
            return "#PatientSafety";
        }
        if (goodCount > 0) {
            return "#MedTech";
        }
        return "";
    }

    private String buildSearchText(NewsArticle article) {
        StringBuilder sb = new StringBuilder();
        if (article.title() != null)    { sb.append(article.title()).append(" "); }
        if (article.bodyText() != null) { sb.append(article.bodyText()); }
        return sb.toString().toLowerCase();
    }

    boolean containsAny(String text, String[] keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) { return true; }
        }
        return false;
    }
}
