package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AiSearchResult;
import com.wgblackmon.aihealthcare.domain.model.AiSearchSynthesis;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.UsageRecord;
import com.wgblackmon.aihealthcare.domain.port.inbound.ConductAiSearchUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.UsageTrackingPort;
import com.wgblackmon.aihealthcare.domain.service.TierGatingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.security.Principal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Thymeleaf controller for the AI-Enhanced Search page.
 *
 * <p>Serves {@code GET /research/ai-search} — allows authenticated MEMBER-tier
 * subscribers to perform AI-enhanced searches that retrieve articles via vector
 * similarity and synthesize them through multiple LLM models (Claude and GPT)
 * for side-by-side comparison.
 *
 * <p>FREE-tier users see an upgrade banner instead of the search form.
 * MEMBER-tier users who have exhausted their monthly query limit see a
 * limit-reached warning.  Each successful search increments the subscriber's
 * monthly usage counter via {@link UsageTrackingPort}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-02
 * @updated 2026-06-02
 */
@Slf4j
@Controller
@RequestMapping("/research/ai-search")
public class AiSearchController {


    private static final int DEFAULT_TOP_K = 10;
    private static final int MAX_TOP_K = 50;

    private static final DateTimeFormatter RESULT_DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a z").withZone(ZoneId.of("America/New_York"));

    private final ConductAiSearchUseCase aiSearchUseCase;
    private final SubscriberPort         subscriberPort;
    private final TierGatingService      tierGatingService;
    private final UsageTrackingPort      usageTrackingPort;

    public AiSearchController(ConductAiSearchUseCase aiSearchUseCase,
                               SubscriberPort subscriberPort,
                               TierGatingService tierGatingService,
                               UsageTrackingPort usageTrackingPort) {
        log.debug("AiSearchController() | aiSearchUseCase={}, subscriberPort={}, tierGatingService={}, usageTrackingPort={}",
                  aiSearchUseCase.getClass().getSimpleName(),
                  subscriberPort.getClass().getSimpleName(),
                  tierGatingService.getClass().getSimpleName(),
                  usageTrackingPort.getClass().getSimpleName());
        this.aiSearchUseCase  = aiSearchUseCase;
        this.subscriberPort   = subscriberPort;
        this.tierGatingService = tierGatingService;
        this.usageTrackingPort = usageTrackingPort;
    }

    /**
     * Renders the AI-enhanced search page.
     *
     * <p>If the authenticated user is FREE tier, sets {@code accessDenied=true}
     * and renders an upgrade banner.  If the user is MEMBER tier, accepts an
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
            Principal principal,
            Model model) {
        log.debug("search() | q={}, topK={}, principal={}", q, topK,
                  principal != null ? principal.getName() : "anonymous");

        boolean admin = isAdmin(principal);

        if (!admin) {
            SubscriptionTier tier = resolveTier(principal);

            // FREE tier — show upgrade banner, no search
            if (tier != SubscriptionTier.MEMBER) {
                model.addAttribute("accessDenied", true);
                log.debug("search() | return=ai-search (accessDenied)");
                return "ai-search";
            }

            // MEMBER tier — check usage limit
            String email = principal.getName();
            String currentMonth = YearMonth.now().toString();
            UsageRecord usage = usageTrackingPort.getOrCreateUsage(email, currentMonth);

            if (!tierGatingService.canQuery(usage)) {
                log.warn("search() | Monthly query limit reached: email={}, used={}, limit={}",
                         email, usage.queryCount(), usage.queryLimit());
                model.addAttribute("limitReached", true);
                model.addAttribute("used", usage.queryCount());
                model.addAttribute("limit", usage.queryLimit());
                log.debug("search() | return=ai-search (limitReached)");
                return "ai-search";
            }
        }

        // Execute search if query is provided
        if (q != null && !q.isBlank()) {
            int resolvedTopK = resolveTopK(topK);
            log.info("search() | executing AI-enhanced search: q='{}', topK={}", q, resolvedTopK);

            AiSearchResult result = aiSearchUseCase.search(q.trim(), resolvedTopK);

            // Increment usage after successful search (admins are unmetered)
            if (!admin && principal != null) {
                String email = principal.getName();
                String currentMonth = YearMonth.now().toString();
                usageTrackingPort.incrementAndGet(email, currentMonth);
            }

            // Build date display map for source articles
            Map<String, String> articleDates = new HashMap<>();
            for (NewsArticle article : result.articles()) {
                if (article.publishedAt() != null) {
                    articleDates.put(article.articleId(), RESULT_DATE_FMT.format(article.publishedAt()));
                }
            }

            // Build snippet map (first 200 chars of bodyText)
            Map<String, String> articleSnippets = new HashMap<>();
            for (NewsArticle article : result.articles()) {
                if (article.bodyText() != null && !article.bodyText().isBlank()) {
                    String snippet = article.bodyText().length() > 200
                            ? article.bodyText().substring(0, 200) + "..."
                            : article.bodyText();
                    articleSnippets.put(article.articleId(), snippet);
                }
            }

            model.addAttribute("searchResult", result);
            model.addAttribute("syntheses", result.syntheses());
            model.addAttribute("articles", result.articles());
            model.addAttribute("articleDates", articleDates);
            model.addAttribute("articleSnippets", articleSnippets);
            model.addAttribute("articleCount", result.articles().size());
            model.addAttribute("q", q);
            model.addAttribute("topK", resolvedTopK);

            log.info("search() | found {} articles, {} syntheses for query '{}'",
                     result.articles().size(), result.syntheses().size(), q);
        }

        log.debug("search() | return=ai-search");
        return "ai-search";
    }

    /**
     * Returns {@code true} if the authenticated user has the {@code ROLE_ADMIN} authority.
     *
     * @param principal the Spring Security principal; may be {@code null}
     * @return whether the user is an admin
     */
    private boolean isAdmin(Principal principal) {
        log.debug("isAdmin() | principal={}", principal != null ? principal.getName() : "null");

        if (principal instanceof Authentication auth) {
            for (GrantedAuthority authority : auth.getAuthorities()) {
                if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                    log.debug("isAdmin() | return=true");
                    return true;
                }
            }
        }

        log.debug("isAdmin() | return=false");
        return false;
    }

    /**
     * Resolves the subscription tier for the currently authenticated user.
     *
     * @param principal the Spring Security principal; may be {@code null}
     * @return the subscriber's tier, defaulting to FREE
     */
    private SubscriptionTier resolveTier(Principal principal) {
        log.debug("resolveTier() | principal={}", principal != null ? principal.getName() : "null");

        if (principal == null) {
            log.debug("resolveTier() | return={}", SubscriptionTier.FREE);
            return SubscriptionTier.FREE;
        }

        Optional<Subscriber> subscriber = subscriberPort.findByEmail(principal.getName());
        SubscriptionTier result = subscriber.map(Subscriber::tier).orElse(SubscriptionTier.FREE);

        log.debug("resolveTier() | return={}", result);
        return result;
    }

    /**
     * Resolves the topK parameter, applying defaults and caps.
     *
     * @param topK the requested topK value; may be {@code null}
     * @return resolved topK between 1 and {@value MAX_TOP_K}
     */
    private int resolveTopK(Integer topK) {
        log.debug("resolveTopK() | topK={}", topK);

        if (topK == null || topK < 1) {
            log.debug("resolveTopK() | return={} (default)", DEFAULT_TOP_K);
            return DEFAULT_TOP_K;
        }

        int result = Math.min(topK, MAX_TOP_K);
        log.debug("resolveTopK() | return={}", result);
        return result;
    }
}
