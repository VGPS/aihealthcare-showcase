package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AiSearchResult;
import com.wgblackmon.aihealthcare.domain.service.LogSanitizer;
import com.wgblackmon.aihealthcare.domain.model.AiSearchSynthesis;
import com.wgblackmon.aihealthcare.domain.model.ModelInfo;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.UsageRecord;
import com.wgblackmon.aihealthcare.domain.port.inbound.ConductAiSearchUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleSearchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.UsageTrackingPort;
import com.wgblackmon.aihealthcare.domain.service.TierGatingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.ArrayList;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified Thymeleaf controller for AI-Enhanced Search (Slice 40 merge).
 *
 * <p>Serves {@code GET /research/ai-search} — the single search page that
 * retrieves articles via vector similarity and synthesizes them through
 * multiple LLM models (Claude, GPT, Perplexity) for side-by-side comparison.
 * Replaces the former separate Semantic Search and AI Search pages.
 *
 * <p>FREE-tier users see an upgrade banner instead of the search form.
 * SUBSCRIBER-tier users who have exhausted their monthly query limit see a
 * limit-reached warning.  Each successful search increments the subscriber's
 * monthly usage counter via {@link UsageTrackingPort}.
 *
 * <p>If AI synthesis fails, the controller falls back to vector-only results
 * via {@link ArticleSearchPort#findSimilar(String, int)}.
 *
 * @author  Bill Blackmon
 * @version 2.1
 * @since   2026-06-02
 * @updated 2026-10-03 — credit-weighted throttling: calculateCreditCost(), canQueryWithCost(), tier-based topK caps
 */
@Slf4j
@Controller
@RequestMapping("/research/ai-search")
public class AiSearchController {

    private static final int DEFAULT_TOP_K        = 20;
    private static final int MAX_TOP_K            = 50;
    private static final int MAX_TOP_K_SUBSCRIBER = 20;

    private static final DateTimeFormatter RESULT_DATE_FMT =
            DisplayFormats.TIMESTAMP_Z.withZone(ZoneId.of("America/New_York"));

    private final ConductAiSearchUseCase aiSearchUseCase;
    private final ArticleSearchPort      articleSearchPort;
    private final TierGatingService      tierGatingService;
    private final UsageTrackingPort      usageTrackingPort;
    private final TierResolver           tierResolver;

    public AiSearchController(ConductAiSearchUseCase aiSearchUseCase,
                               ArticleSearchPort articleSearchPort,
                               TierGatingService tierGatingService,
                               UsageTrackingPort usageTrackingPort,
                               TierResolver tierResolver) {
        log.debug("AiSearchController() | aiSearchUseCase={}, articleSearchPort={}, tierGatingService={}, usageTrackingPort={}, tierResolver={}",
                  aiSearchUseCase.getClass().getSimpleName(),
                  articleSearchPort.getClass().getSimpleName(),
                  tierGatingService.getClass().getSimpleName(),
                  usageTrackingPort.getClass().getSimpleName(),
                  tierResolver.getClass().getSimpleName());
        this.aiSearchUseCase   = aiSearchUseCase;
        this.articleSearchPort = articleSearchPort;
        this.tierGatingService = tierGatingService;
        this.usageTrackingPort = usageTrackingPort;
        this.tierResolver      = tierResolver;
    }

    /**
     * Renders the AI-enhanced search page.
     *
     * <p>If the authenticated user is FREE tier, sets {@code accessDenied=true}
     * and renders an upgrade banner.  If the user is SUBSCRIBER tier, accepts an
     * optional query parameter {@code q} and returns multi-model AI syntheses.
     *
     * @param q         optional search query string
     * @param topK      optional max articles to retrieve (default 10, max 50)
     * @param principal the authenticated user
     * @param model     Thymeleaf model
     * @return the "ai-search" view name
     */
    @GetMapping
    public String search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer topK,
            @RequestParam(required = false) List<String> models,
            Principal principal,
            Model model) {
        log.debug("search() | q={}, topK={}, models={}, principal={}", q, topK, models,
                  principal != null ? principal.getName() : "anonymous");

        // Anonymous visitors — show teaser without running any AI calls
        if (principal == null) {
            model.addAttribute("guestTeaser", true);
            if (q != null && !q.isBlank()) {
                model.addAttribute("q", q);
            }
            log.debug("search() | return=ai-search (guestTeaser)");
            return "ai-search";
        }

        boolean admin = tierResolver.isAdmin(principal);
        SubscriptionTier tier = admin ? null : tierResolver.resolveTier(principal);

        if (!admin) {
            // FREE/FREE_PENDING tier — show upgrade banner, no search
            if (tier == SubscriptionTier.FREE || tier == SubscriptionTier.FREE_PENDING) {
                model.addAttribute("accessDenied", true);
                log.debug("search() | return=ai-search (accessDenied)");
                return "ai-search";
            }

            // Credit limit check — enforce before running expensive AI calls
            if (q != null && !q.isBlank()) {
                int creditCost = calculateCreditCost(models);
                String email = principal.getName();
                String currentMonth = YearMonth.now().toString();
                UsageRecord usage = usageTrackingPort.getOrCreateUsage(email, currentMonth);

                if (!tierGatingService.canQueryWithCost(usage, creditCost)) {
                    log.warn("search() | Credit limit reached: email={}, used={}, limit={}, cost={}",
                             LogSanitizer.maskEmail(email), usage.queryCount(), usage.queryLimit(), creditCost);
                    model.addAttribute("limitReached", true);
                    model.addAttribute("used", usage.queryCount());
                    model.addAttribute("limit", usage.queryLimit());
                    model.addAttribute("creditCost", creditCost);
                    log.debug("search() | return=ai-search (limitReached)");
                    return "ai-search";
                }
            }
        }

        // Execute search if query is provided
        if (q != null && !q.isBlank()) {
            int creditCost = calculateCreditCost(models);
            int resolvedTopK = resolveTopKForTier(topK, tier, admin);
            log.info("search() | executing AI-enhanced search: q='{}', topK={}, creditCost={}", q, resolvedTopK, creditCost);

            List<NewsArticle> articles;
            List<AiSearchSynthesis> syntheses = Collections.emptyList();
            List<String> noMatchModels = Collections.emptyList();

            // Try AI-enhanced search (vector + synthesis); fall back to vector-only
            try {
                AiSearchResult result = aiSearchUseCase.search(q.trim(), resolvedTopK, models);
                articles = result.articles();
                syntheses = result.syntheses();
                noMatchModels = result.noMatchModelNames();
                log.info("search() | AI search returned {} articles, {} syntheses, {} no-match",
                         articles.size(), syntheses.size(), noMatchModels.size());
            } catch (Exception ex) {
                log.warn("search() | AI synthesis failed, falling back to vector-only: {}",
                         ex.getMessage());
                articles = articleSearchPort.findSimilar(q.trim(), resolvedTopK);
            }

            // Deduct credits after successful search (admins are unmetered)
            if (!admin) {
                String email = principal.getName();
                String currentMonth = YearMonth.now().toString();
                usageTrackingPort.incrementByCredits(email, currentMonth, creditCost);
            }

            // Build date display map for source articles
            Map<String, String> articleDates = new HashMap<>();
            for (NewsArticle article : articles) {
                if (article.publishedAt() != null) {
                    articleDates.put(article.articleId(), RESULT_DATE_FMT.format(article.publishedAt()));
                }
            }

            model.addAttribute("syntheses", syntheses);
            model.addAttribute("noMatchModels", noMatchModels);
            model.addAttribute("articles", articles);
            model.addAttribute("articleDates", articleDates);
            model.addAttribute("articleCount", articles.size());
            model.addAttribute("q", q);
            model.addAttribute("topK", resolvedTopK);
            model.addAttribute("selectedModels", models != null ? models : List.of("Claude"));

            log.info("search() | found {} articles, {} syntheses for query '{}'",
                     articles.size(), syntheses.size(), q);
        }

        // Populate available models for dynamic checkbox rendering
        List<String> availableModelNames = new ArrayList<>();
        for (ModelInfo mi : aiSearchUseCase.availableModels()) {
            availableModelNames.add(mi.providerName());
        }
        model.addAttribute("availableModels", availableModelNames);

        log.debug("search() | return=ai-search");
        return "ai-search";
    }

    /**
     * Calculates the total credit cost for one search based on the selected models.
     *
     * <p>Credit costs: Deep Research = 10, Claude/GPT/Gemini = 3, standard Perplexity = 1.
     * When no models are selected the default single-model cost of 3 is returned.
     *
     * @param selectedModels list of provider names from the model checkboxes; may be {@code null}
     * @return total credit cost for the planned query; always &ge; 1
     */
    private int calculateCreditCost(List<String> selectedModels) {
        log.debug("calculateCreditCost() | selectedModels={}", selectedModels);

        if (selectedModels == null || selectedModels.isEmpty()) {
            log.debug("calculateCreditCost() | return=3 (default)");
            return 3;
        }

        int total = 0;
        for (String name : selectedModels) {
            total += creditCostFor(name);
        }
        int result = Math.max(1, total);
        log.debug("calculateCreditCost() | return={}", result);
        return result;
    }

    private static int creditCostFor(String modelName) {
        if (modelName == null) return 1;
        String lower = modelName.toLowerCase();
        if (lower.contains("deep"))                                                  return 10;
        if (lower.contains("claude") || lower.contains("gpt")
                || lower.contains("gemini"))                                         return 3;
        return 1;
    }

    /**
     * Resolves the topK parameter with tier-based caps.
     * ENTERPRISE and admin users get up to 50; all other authenticated tiers are capped at 20.
     *
     * @param topK  requested topK; may be {@code null}
     * @param tier  the subscriber's tier; {@code null} for admins
     * @param admin {@code true} if the caller has admin authority
     * @return resolved topK capped by tier
     */
    private int resolveTopKForTier(Integer topK, SubscriptionTier tier, boolean admin) {
        log.debug("resolveTopKForTier() | topK={}, tier={}, admin={}", topK, tier, admin);

        int cap = (admin || tier == SubscriptionTier.ENTERPRISE) ? MAX_TOP_K : MAX_TOP_K_SUBSCRIBER;
        int resolved = (topK == null || topK < 1) ? DEFAULT_TOP_K : topK;
        int result = Math.min(resolved, cap);

        log.debug("resolveTopKForTier() | return={}", result);
        return result;
    }
}
