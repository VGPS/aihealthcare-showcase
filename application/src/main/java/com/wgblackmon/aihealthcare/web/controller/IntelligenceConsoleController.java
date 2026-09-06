package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.UsageRecord;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.UsageTrackingPort;
import com.wgblackmon.aihealthcare.domain.service.TierGatingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.security.Principal;
import java.time.YearMonth;
import java.util.Optional;
import java.util.Set;

/**
 * Customer-facing Intelligence Console for the Claude Healthcare Intelligence Service.
 *
 * <p>Serves the Intelligence Console at {@code /admin/intelligence} with 9 tabs
 * covering the Claude Intelligence Service endpoint surface. Tabs are tier-gated:
 * SUBSCRIBER sees 5 tabs; ENTERPRISE and DEMO see all 9 tabs. Generic REST proxy
 * at {@code /admin/intelligence/api/**} forwards AJAX requests to the Claude service.
 *
 * <p>API-level enforcement mirrors UI gating: Enterprise-only API paths return 403
 * for non-enterprise users. LLM-triggering POST paths count against monthly query
 * quota and return 429 when the limit is reached.
 *
 * <p>Admins ({@code ROLE_ADMIN}) bypass all tier and usage checks.
 *
 * @author  Bill Blackmon
 * @version 3.0
 * @since   2026-08-08
 * @updated 2026-09-06
 */
@Slf4j
@Controller
@RequestMapping("/admin/intelligence")
public class IntelligenceConsoleController {

    /** API paths that require ENTERPRISE or DEMO tier. */
    private static final Set<String> ENTERPRISE_PATHS = Set.of(
            "/api/v1/intelligence/verify",
            "/api/v1/intelligence/synthesis/trending"
    );

    /** POST paths that trigger LLM work and count against monthly quota. */
    private static final Set<String> COUNTING_PATHS = Set.of(
            "/api/v1/intelligence/chat",
            "/api/v1/intelligence/analyze",
            "/api/v1/intelligence/synthesis",
            "/api/v1/intelligence/platform-race",
            "/api/v1/intelligence/wiki/ask",
            "/api/v1/intelligence/verify"
    );

    private final RestClient restClient;
    private final String baseUrl;
    private final String apiKey;
    private final SubscriberPort subscriberPort;
    private final TierGatingService tierGatingService;
    private final UsageTrackingPort usageTrackingPort;

    public IntelligenceConsoleController(
            @Value("${claude.intelligence.base-url:http://localhost:8081}") String baseUrl,
            @Value("${intelligence.api-key:}") String apiKey,
            SubscriberPort subscriberPort,
            TierGatingService tierGatingService,
            UsageTrackingPort usageTrackingPort) {
        log.debug("IntelligenceConsoleController() | baseUrl={}", baseUrl);
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.subscriberPort = subscriberPort;
        this.tierGatingService = tierGatingService;
        this.usageTrackingPort = usageTrackingPort;
        RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl);
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("X-API-Key", apiKey);
        }
        this.restClient = builder.build();
    }

    /**
     * Renders the Intelligence Console.
     *
     * <p>Sets {@code isEnterprise} and {@code isSubscriber} model attributes used
     * by Thymeleaf to gate tab content. Admins see all tabs regardless of tier.
     */
    @GetMapping
    public String console(Model model, Principal principal) {
        log.debug("console() | principal={}", principal != null ? principal.getName() : "anonymous");
        SubscriptionTier tier = resolveTier(principal);
        boolean isEnterprise = isEnterpriseTier(tier) || isAdmin(principal);
        boolean isSubscriber = isSubscriberOrAbove(tier);
        model.addAttribute("baseUrl", baseUrl);
        model.addAttribute("activeTab", "chat");
        model.addAttribute("isEnterprise", isEnterprise);
        model.addAttribute("isSubscriber", isSubscriber);
        model.addAttribute("tier", tier.name());
        log.debug("console() | return=intelligence-console, isEnterprise={}", isEnterprise);
        return "intelligence-console";
    }

    /**
     * Proxies POST requests to the Claude Intelligence Service.
     *
     * <p>Enforces enterprise-path tier gating and LLM-path usage counting for
     * non-admin users. Returns 403 on wrong tier, 429 on quota exhaustion.
     */
    @PostMapping("/api/**")
    @ResponseBody
    public ResponseEntity<String> proxyPost(HttpServletRequest request,
                                             @RequestBody(required = false) String body,
                                             Principal principal) {
        String forwardPath = request.getRequestURI().substring("/admin/intelligence".length());
        log.debug("proxyPost() | path={}", forwardPath);

        boolean admin = isAdmin(principal);

        if (!admin && requiresEnterprise(forwardPath)) {
            SubscriptionTier tier = resolveTier(principal);
            if (!isEnterpriseTier(tier)) {
                log.warn("proxyPost() | enterprise path blocked for tier={}, path={}", tier, forwardPath);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"Enterprise subscription required\",\"upgradeUrl\":\"/pricing#enterprise\"}");
            }
        }

        if (!admin && principal != null && isCountingPath(forwardPath)) {
            String email = principal.getName();
            String yearMonth = YearMonth.now().toString();
            UsageRecord usage = usageTrackingPort.getOrCreateUsage(email, yearMonth);
            if (!tierGatingService.canQuery(usage)) {
                log.warn("proxyPost() | query limit reached: email={}, used={}, limit={}",
                         email, usage.queryCount(), usage.queryLimit());
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"Monthly query limit reached\",\"used\":" + usage.queryCount() +
                              ",\"limit\":" + usage.queryLimit() + ",\"upgradeUrl\":\"/pricing\"}");
            }
            ResponseEntity<String> result = doProxyPost(forwardPath, body);
            if (result.getStatusCode().is2xxSuccessful()) {
                usageTrackingPort.incrementAndGet(email, yearMonth);
            }
            log.debug("proxyPost() | counted path return={}", result.getStatusCode());
            return result;
        }

        return doProxyPost(forwardPath, body);
    }

    /**
     * Proxies GET requests to the Claude Intelligence Service.
     *
     * <p>Enforces enterprise-path tier gating for non-admin users. GET paths are
     * not counted against the usage quota (they retrieve cached/computed data).
     */
    @GetMapping("/api/**")
    @ResponseBody
    public ResponseEntity<String> proxyGet(HttpServletRequest request, Principal principal) {
        String forwardPath = request.getRequestURI().substring("/admin/intelligence".length());
        String queryString = request.getQueryString();
        String fullPath = queryString != null ? forwardPath + "?" + queryString : forwardPath;
        log.debug("proxyGet() | path={}", fullPath);

        boolean admin = isAdmin(principal);

        if (!admin && requiresEnterprise(forwardPath)) {
            SubscriptionTier tier = resolveTier(principal);
            if (!isEnterpriseTier(tier)) {
                log.warn("proxyGet() | enterprise path blocked for tier={}, path={}", tier, forwardPath);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"Enterprise subscription required\",\"upgradeUrl\":\"/pricing#enterprise\"}");
            }
        }

        boolean isHtml = forwardPath.contains("/history/files/") && !forwardPath.endsWith("/files");
        try {
            String result = restClient.get()
                    .uri(URI.create(baseUrl + fullPath))
                    .retrieve()
                    .body(String.class);
            log.debug("proxyGet() | return=200, length={}", result != null ? result.length() : 0);
            MediaType contentType = isHtml ? MediaType.TEXT_HTML : MediaType.APPLICATION_JSON;
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .body(result);
        } catch (Exception e) {
            log.error("proxyGet() | path={}, error={}", fullPath, e.getMessage());
            return ResponseEntity.status(502)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"Claude Intelligence Service error: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private ResponseEntity<String> doProxyPost(String forwardPath, String body) {
        try {
            String result = restClient.post()
                    .uri(forwardPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body != null ? body : "")
                    .retrieve()
                    .body(String.class);
            log.debug("doProxyPost() | return=200, length={}", result != null ? result.length() : 0);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(result);
        } catch (Exception e) {
            log.error("doProxyPost() | path={}, error={}", forwardPath, e.getMessage());
            return ResponseEntity.status(502)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"Claude Intelligence Service error: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private boolean requiresEnterprise(String forwardPath) {
        log.debug("requiresEnterprise() | forwardPath={}", forwardPath);
        for (String path : ENTERPRISE_PATHS) {
            if (forwardPath.startsWith(path)) {
                log.debug("requiresEnterprise() | return=true");
                return true;
            }
        }
        log.debug("requiresEnterprise() | return=false");
        return false;
    }

    private boolean isCountingPath(String forwardPath) {
        log.debug("isCountingPath() | forwardPath={}", forwardPath);
        for (String path : COUNTING_PATHS) {
            if (forwardPath.startsWith(path)) {
                log.debug("isCountingPath() | return=true");
                return true;
            }
        }
        log.debug("isCountingPath() | return=false");
        return false;
    }

    private boolean isEnterpriseTier(SubscriptionTier tier) {
        return tier == SubscriptionTier.ENTERPRISE || tier == SubscriptionTier.DEMO;
    }

    private boolean isSubscriberOrAbove(SubscriptionTier tier) {
        return isEnterpriseTier(tier) || tier == SubscriptionTier.SUBSCRIBER;
    }

    private boolean isAdmin(Principal principal) {
        if (principal instanceof Authentication auth) {
            for (GrantedAuthority authority : auth.getAuthorities()) {
                if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                    return true;
                }
            }
        }
        return false;
    }

    private SubscriptionTier resolveTier(Principal principal) {
        if (principal == null) return SubscriptionTier.FREE;
        if (isAdmin(principal)) return SubscriptionTier.SUBSCRIBER;
        return subscriberPort.findByEmail(principal.getName())
                .map(Subscriber::tier).orElse(SubscriptionTier.FREE);
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n")
                     .replace("\r", "\\r")
                     .replace("\t", "\\t");
    }
}
