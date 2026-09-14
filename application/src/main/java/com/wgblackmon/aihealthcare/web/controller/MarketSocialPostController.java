package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.marketanalysis.AffectedCompany;
import com.wgblackmon.aihealthcare.domain.marketanalysis.FactClassification;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigest;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestEntry;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.ProduceMarketDigestUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.net.URI;
import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Combined LinkedIn + Facebook post generator sourced from the daily market digest.
 *
 * <p>Serves {@code GET /dashboard/social} with side-by-side post previews for both
 * platforms, each with platform-specific formatting rules:
 * <ul>
 *   <li><b>LinkedIn</b>: no links in body (algorithm penalty), links in first comment,
 *       professional tone, hashtags, up to 2,900 chars body.</li>
 *   <li><b>Facebook</b>: links OK in body, shorter body (&le;390 chars above "See more"
 *       fold), additional headlines as bullets, casual tone.</li>
 * </ul>
 *
 * <p>No LLM calls — templates market digest entries directly. Pulls the latest digest
 * via {@link ProduceMarketDigestUseCase} and formats the top entries by rank.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-14
 * @updated 2026-09-14
 */
@Slf4j
@Controller
public class MarketSocialPostController {

    private static final int MAX_ENTRIES = 5;
    private static final int LINKEDIN_BODY_LIMIT = 2900;
    private static final int LINKEDIN_COMMENT_LIMIT = 1050;
    private static final int FACEBOOK_FOLD_LIMIT = 390;
    private static final String SITE_URL = "https://app.bigskylabs.ai";
    private static final String HASHTAGS = "#AIHealthcare #HealthcareAI #DigitalHealth #HealthTech #MedicalInnovation";

    private final ProduceMarketDigestUseCase digestUseCase;
    private final TierResolver tierResolver;

    public MarketSocialPostController(ProduceMarketDigestUseCase digestUseCase,
                                      TierResolver tierResolver) {
        log.debug("MarketSocialPostController() | digestUseCase={}, tierResolver={}", digestUseCase, tierResolver);
        this.digestUseCase = digestUseCase;
        this.tierResolver = tierResolver;
    }

    @GetMapping("/dashboard/social")
    public String socialPosts(Model model, Principal principal) {
        log.debug("socialPosts() | principal={}", principal != null ? principal.getName() : "anonymous");

        boolean fullAccess = tierResolver.hasFullAccess(principal);
        model.addAttribute("fullAccess", fullAccess);

        if (!fullAccess) {
            model.addAttribute("dateLabel", DisplayFormats.LONG_DATE.format(
                    LocalDate.now(ZoneId.of("America/Chicago"))));
            model.addAttribute("entryCount", 0);
            log.debug("socialPosts() | return=social-post, upgrade required");
            return "social-post";
        }

        String dateLabel = DisplayFormats.LONG_DATE.format(LocalDate.now(ZoneId.of("America/Chicago")));
        Optional<MarketDigest> latestOpt = digestUseCase.findLatest();

        if (latestOpt.isEmpty() || latestOpt.get().entries().isEmpty()) {
            model.addAttribute("dateLabel", dateLabel);
            model.addAttribute("entryCount", 0);
            model.addAttribute("linkedinBody", "No market digest available. Run the Market Digest pipeline first.");
            model.addAttribute("linkedinComment", "");
            model.addAttribute("facebookBody", "No market digest available. Run the Market Digest pipeline first.");
            model.addAttribute("facebookComment", "");
            model.addAttribute("linkedinBodyLength", 0);
            model.addAttribute("linkedinCommentLength", 0);
            model.addAttribute("facebookBodyLength", 0);
            model.addAttribute("facebookCommentLength", 0);
            log.debug("socialPosts() | return=social-post, no digest");
            return "social-post";
        }

        MarketDigest digest = latestOpt.get();
        List<MarketDigestEntry> entries = digest.entries();
        List<MarketDigestEntry> top = entries.size() > MAX_ENTRIES
                ? entries.subList(0, MAX_ENTRIES) : entries;

        String digestDate = DisplayFormats.LONG_DATE.format(digest.date());

        String linkedinBody = buildLinkedInBody(top, digestDate);
        String linkedinComment = buildLinkedInComment(top, digestDate);
        String facebookBody = buildFacebookBody(top, digestDate);
        String facebookComment = buildFacebookComment(top, digestDate);

        model.addAttribute("dateLabel", digestDate);
        model.addAttribute("entryCount", top.size());
        model.addAttribute("totalEntries", entries.size());
        model.addAttribute("linkedinBody", linkedinBody);
        model.addAttribute("linkedinComment", linkedinComment);
        model.addAttribute("facebookBody", facebookBody);
        model.addAttribute("facebookComment", facebookComment);
        model.addAttribute("linkedinBodyLength", linkedinBody.length());
        model.addAttribute("linkedinCommentLength", linkedinComment.length());
        model.addAttribute("facebookBodyLength", facebookBody.length());
        model.addAttribute("facebookCommentLength", facebookComment.length());

        log.debug("socialPosts() | return=social-post, entries={}", top.size());
        return "social-post";
    }

    // ─── LinkedIn ───────────────────────────────────────────────────────────

    private String buildLinkedInBody(List<MarketDigestEntry> entries, String dateLabel) {
        log.debug("buildLinkedInBody() | entries={}", entries.size());
        StringBuilder sb = new StringBuilder();

        sb.append("AI did what to whom. When, where, and why.\n");
        sb.append("AI in Healthcare — Market Intelligence · ").append(dateLabel).append("\n\n");

        for (int i = 0; i < entries.size(); i++) {
            MarketDigestEntry e = entries.get(i);
            String badge = categoryEmoji(e) + " [" + formatCategory(e) + "] ";
            String factTag = e.factClassification() == FactClassification.CONFIRMED ? "" : " ⚠️ speculative";
            sb.append(i + 1).append(". ").append(badge)
              .append(e.newsItem().headline()).append(factTag).append("\n");

            String amount = formatDealAmount(e);
            String companies = formatCompanies(e);
            if (!amount.isEmpty() || !companies.isEmpty()) {
                sb.append("   ");
                if (!amount.isEmpty()) sb.append(amount);
                if (!amount.isEmpty() && !companies.isEmpty()) sb.append(" · ");
                if (!companies.isEmpty()) sb.append(companies);
                sb.append("\n");
            }
            sb.append("\n");
        }

        sb.append("Source links in the first comment below.\n\n");
        sb.append("Follow for daily AI healthcare market intelligence.\n");
        sb.append("→ Full digest: ").append(SITE_URL).append("/dashboard/market\n\n");
        sb.append(HASHTAGS);

        String result = sb.toString();
        if (result.length() > LINKEDIN_BODY_LIMIT) {
            result = result.substring(0, LINKEDIN_BODY_LIMIT);
            int lastNewline = result.lastIndexOf('\n');
            if (lastNewline > LINKEDIN_BODY_LIMIT - 200) {
                result = result.substring(0, lastNewline);
            }
        }
        log.debug("buildLinkedInBody() | return=length:{}", result.length());
        return result;
    }

    private String buildLinkedInComment(List<MarketDigestEntry> entries, String dateLabel) {
        log.debug("buildLinkedInComment() | entries={}", entries.size());
        StringBuilder sb = new StringBuilder();
        sb.append("Sources (").append(dateLabel).append("):\n\n");

        for (int i = 0; i < entries.size(); i++) {
            MarketDigestEntry e = entries.get(i);
            List<String> urls = e.newsItem().sourceUrls();
            String url = (urls != null && !urls.isEmpty()) ? urls.get(0) : "";
            String item = (i + 1) + ". " + e.newsItem().headline() + "\n"
                    + (url.isEmpty() ? "" : url + "\n") + "\n";
            if (sb.length() + item.length() > LINKEDIN_COMMENT_LIMIT) {
                sb.append("Full digest: ").append(SITE_URL).append("/dashboard/market\n");
                break;
            }
            sb.append(item);
        }

        sb.append("\n").append(HASHTAGS);
        String result = sb.toString().trim();
        log.debug("buildLinkedInComment() | return=length:{}", result.length());
        return result;
    }

    // ─── Facebook ───────────────────────────────────────────────────────────

    private String buildFacebookBody(List<MarketDigestEntry> entries, String dateLabel) {
        log.debug("buildFacebookBody() | entries={}", entries.size());
        StringBuilder sb = new StringBuilder();

        MarketDigestEntry lead = entries.get(0);
        String leadEmoji = categoryEmoji(lead);
        sb.append(leadEmoji).append(" ").append(lead.newsItem().headline());
        String amount = formatDealAmount(lead);
        if (!amount.isEmpty()) sb.append(" — ").append(amount);
        sb.append("\n\n");

        if (entries.size() > 1) {
            sb.append("Also today:\n");
            for (int i = 1; i < entries.size(); i++) {
                MarketDigestEntry e = entries.get(i);
                sb.append("• ").append(entries.get(i).newsItem().headline());
                String amt = formatDealAmount(e);
                if (!amt.isEmpty()) sb.append(" (").append(amt).append(")");
                sb.append("\n");
            }
            sb.append("\n");
        }

        sb.append("Full digest → ").append(SITE_URL).append("/dashboard/market");

        String result = sb.toString();
        log.debug("buildFacebookBody() | return=length:{}", result.length());
        return result;
    }

    private String buildFacebookComment(List<MarketDigestEntry> entries, String dateLabel) {
        log.debug("buildFacebookComment() | entries={}", entries.size());
        StringBuilder sb = new StringBuilder();
        sb.append("AI in Healthcare — Market Intelligence · ").append(dateLabel).append("\n\n");

        for (int i = 0; i < entries.size(); i++) {
            MarketDigestEntry e = entries.get(i);
            sb.append(i + 1).append(". ").append(e.newsItem().headline()).append("\n");
            sb.append("   ").append(e.newsItem().summary()).append("\n");
            List<String> urls = e.newsItem().sourceUrls();
            if (urls != null && !urls.isEmpty()) {
                sb.append("   ").append(urls.get(0)).append("\n");
            }
            sb.append("\n");
        }

        sb.append("Full digest: ").append(SITE_URL).append("/dashboard/market\n\n");
        sb.append(HASHTAGS);

        String result = sb.toString().trim();
        log.debug("buildFacebookComment() | return=length:{}", result.length());
        return result;
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private String categoryEmoji(MarketDigestEntry entry) {
        switch (entry.category()) {
            case EARNINGS:          return "📊";
            case REGULATORY:        return "🏛️";
            case FUNDING:           return "💰";
            case M_AND_A:           return "🤝";
            case MAJOR_PARTNERSHIP: return "🔗";
            case LEGAL_ACTION:      return "⚖️";
            case WORKFORCE:         return "👥";
            case PRODUCT_LAUNCH:    return "🚀";
            default:                return "📰";
        }
    }

    private String formatCategory(MarketDigestEntry entry) {
        switch (entry.category()) {
            case EARNINGS:          return "EARNINGS";
            case REGULATORY:        return "REGULATORY";
            case FUNDING:           return "FUNDING";
            case M_AND_A:           return "M&A";
            case MAJOR_PARTNERSHIP: return "PARTNERSHIP";
            case LEGAL_ACTION:      return "LEGAL";
            case WORKFORCE:         return "WORKFORCE";
            case PRODUCT_LAUNCH:    return "PRODUCT";
            default:                return "NEWS";
        }
    }

    private String formatDealAmount(MarketDigestEntry entry) {
        long amount = entry.dealSizeUsd();
        if (amount <= 0) return "";
        if (amount >= 1_000_000_000L) {
            return "$" + String.format("%.1f", amount / 1_000_000_000.0) + "B";
        }
        if (amount >= 1_000_000L) {
            return "$" + (amount / 1_000_000L) + "M";
        }
        return "$" + String.format("%,d", amount);
    }

    private String formatCompanies(MarketDigestEntry entry) {
        List<AffectedCompany> companies = entry.affectedCompanies();
        if (companies == null || companies.isEmpty()) return "";
        List<String> names = new ArrayList<>();
        for (AffectedCompany c : companies) {
            String label = c.name();
            if (c.tickerSymbol() != null && !c.tickerSymbol().isBlank()
                    && !"PRIVATE".equalsIgnoreCase(c.tickerSymbol())) {
                label += " ($" + c.tickerSymbol() + ")";
            }
            names.add(label);
        }
        return String.join(", ", names);
    }

    private String extractDomain(String url) {
        if (url == null || url.isBlank()) return "link";
        try {
            String host = URI.create(url).getHost();
            if (host == null) return "link";
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return "link";
        }
    }
}
