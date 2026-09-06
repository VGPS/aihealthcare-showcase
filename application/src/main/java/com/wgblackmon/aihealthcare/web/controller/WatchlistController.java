package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.service.LogSanitizer;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItem;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItemType;
import com.wgblackmon.aihealthcare.domain.model.WatchlistMatch;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistMatchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistPort;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Thymeleaf controller serving the subscriber watchlist page at
 * {@code GET /watchlist}.
 *
 * <p>Allows SUBSCRIBER and DEMO tier users to add companies, keywords,
 * and topics to a personal watchlist. Recent article matches are displayed
 * below the watchlist items.
 *
 * <p>FREE and FREE_PENDING users are redirected to the pricing page.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-09-06
 */
@Slf4j
@Controller
public class WatchlistController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy")
                    .withZone(ZoneId.of("America/New_York"));

    private static final int MAX_RECENT_MATCHES = 50;

    private final WatchlistPort watchlistPort;
    private final WatchlistMatchPort watchlistMatchPort;
    private final SubscriberPort subscriberPort;
    private final NewsArticleRepository articleRepository;

    public WatchlistController(WatchlistPort watchlistPort,
                                WatchlistMatchPort watchlistMatchPort,
                                SubscriberPort subscriberPort,
                                NewsArticleRepository articleRepository) {
        log.debug("WatchlistController() | watchlistPort={}, watchlistMatchPort={}, subscriberPort={}, articleRepository={}",
                watchlistPort.getClass().getSimpleName(),
                watchlistMatchPort.getClass().getSimpleName(),
                subscriberPort.getClass().getSimpleName(),
                articleRepository.getClass().getSimpleName());
        this.watchlistPort = watchlistPort;
        this.watchlistMatchPort = watchlistMatchPort;
        this.subscriberPort = subscriberPort;
        this.articleRepository = articleRepository;
    }

    /**
     * Renders the watchlist page with the user's tracked items and recent matches.
     */
    @GetMapping("/watchlist")
    public String index(
            @RequestParam(required = false, defaultValue = "matched_desc") String sort,
            Model model, Principal principal) {
        log.debug("index() | sort={}, principal={}", sort, principal != null ? principal.getName() : "anonymous");

        String email = principal != null ? principal.getName() : "";

        // Tier gate — only SUBSCRIBER, DEMO, ENTERPRISE, and ADMIN can access
        if (!hasWatchlistAccess(principal)) {
            log.debug("index() | return=redirect:/pricing (tier gate)");
            return "redirect:/pricing";
        }

        List<WatchlistItem> items = watchlistPort.findByUser(email);

        // Group items by type
        List<WatchlistItem> companyItems = new ArrayList<>();
        List<WatchlistItem> keywordItems = new ArrayList<>();
        List<WatchlistItem> topicItems = new ArrayList<>();
        for (WatchlistItem item : items) {
            if (item.itemType() == WatchlistItemType.COMPANY) {
                companyItems.add(item);
            } else if (item.itemType() == WatchlistItemType.KEYWORD) {
                keywordItems.add(item);
            } else if (item.itemType() == WatchlistItemType.TOPIC) {
                topicItems.add(item);
            }
        }

        // Format dates
        Map<String, String> itemDates = new HashMap<>();
        for (WatchlistItem item : items) {
            itemDates.put(item.itemId(), DISPLAY_FMT.format(item.createdAt()));
        }

        // Recent matches
        List<WatchlistMatch> matches = watchlistMatchPort.findByUser(email, MAX_RECENT_MATCHES);

        // Build item label lookup for display
        Map<String, String> itemLabels = new HashMap<>();
        Map<String, String> itemTypes = new HashMap<>();
        for (WatchlistItem item : items) {
            itemLabels.put(item.itemId(), item.label());
            itemTypes.put(item.itemId(), item.itemType().name());
        }

        // Sort matches
        matches = sortMatches(matches, sort, itemLabels);

        // Format match dates + strip HTML from snippets
        Map<String, String> matchDates = new HashMap<>();
        Map<String, String> matchSnippets = new HashMap<>();
        for (WatchlistMatch match : matches) {
            matchDates.put(match.matchId(), DISPLAY_FMT.format(match.matchedOn()));
            matchSnippets.put(match.matchId(), stripHtml(match.snippet()));
        }

        // Look up article titles and URLs for matched articles
        Map<String, String> articleTitles = new HashMap<>();
        Map<String, String> articleUrls = new HashMap<>();
        if (!matches.isEmpty()) {
            List<String> articleIds = new ArrayList<>();
            for (WatchlistMatch match : matches) {
                articleIds.add(match.articleId());
            }
            List<NewsArticleEntity> articles = articleRepository.findByArticleIdIn(articleIds);
            for (NewsArticleEntity article : articles) {
                articleTitles.put(article.getArticleId(), article.getTitle());
                if (article.getUrl() != null) {
                    articleUrls.put(article.getArticleId(), article.getUrl());
                }
            }
        }

        model.addAttribute("companyItems", companyItems);
        model.addAttribute("keywordItems", keywordItems);
        model.addAttribute("topicItems", topicItems);
        model.addAttribute("itemDates", itemDates);
        model.addAttribute("itemCount", items.size());
        model.addAttribute("matches", matches);
        model.addAttribute("matchCount", matches.size());
        model.addAttribute("matchDates", matchDates);
        model.addAttribute("matchSnippets", matchSnippets);
        model.addAttribute("itemLabels", itemLabels);
        model.addAttribute("itemTypes", itemTypes);
        model.addAttribute("articleTitles", articleTitles);
        model.addAttribute("articleUrls", articleUrls);
        model.addAttribute("sort", sort);

        log.debug("index() | return=watchlist, items={}, matches={}", items.size(), matches.size());
        return "watchlist";
    }

    private String stripHtml(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String stripped = text.replaceAll("<[^>]+>", " ");
        stripped = stripped.replaceAll("<[^>]*$", "");
        stripped = stripped.replace("&amp;", "&")
                          .replace("&lt;", "<")
                          .replace("&gt;", ">")
                          .replace("&quot;", "\"")
                          .replace("&#39;", "'")
                          .replace("&nbsp;", " ");
        stripped = stripped.replaceAll("<[^>]+>", " ");
        stripped = stripped.replaceAll("<[^>]*$", "");
        return stripped.replaceAll("\\s+", " ").trim();
    }

    /**
     * Sorts the matches list by the specified column.
     */
    private List<WatchlistMatch> sortMatches(List<WatchlistMatch> matches, String sort,
                                              Map<String, String> itemLabels) {
        log.debug("sortMatches() | sort={}, size={}", sort, matches.size());
        if (matches.isEmpty()) {
            log.debug("sortMatches() | return=empty list");
            return matches;
        }

        boolean descending = sort != null && sort.endsWith("_desc");
        String column = descending ? sort.substring(0, sort.length() - 5) : sort;

        Comparator<WatchlistMatch> comparator;
        if ("item".equalsIgnoreCase(column)) {
            comparator = Comparator.comparing(
                    m -> itemLabels.getOrDefault(m.itemId(), ""),
                    String.CASE_INSENSITIVE_ORDER);
        } else if ("article".equalsIgnoreCase(column)) {
            comparator = Comparator.comparing(WatchlistMatch::articleId, String.CASE_INSENSITIVE_ORDER);
        } else if ("snippet".equalsIgnoreCase(column)) {
            comparator = Comparator.comparing(
                    m -> m.snippet() != null ? m.snippet() : "",
                    String.CASE_INSENSITIVE_ORDER);
        } else {
            // Default: matched date
            comparator = Comparator.comparing(WatchlistMatch::matchedOn);
        }

        if (descending) {
            comparator = comparator.reversed();
        }

        List<WatchlistMatch> sorted = new ArrayList<>(matches);
        sorted.sort(comparator);
        log.debug("sortMatches() | return=sorted list, size={}", sorted.size());
        return sorted;
    }

    /**
     * Adds a new item to the user's watchlist.
     */
    @PostMapping("/watchlist")
    public String addItem(@RequestParam String itemType,
                          @RequestParam String value,
                          @RequestParam(required = false) String label,
                          Principal principal,
                          RedirectAttributes redirectAttributes) {
        log.debug("addItem() | itemType={}, value={}, label={}", itemType, value, label);

        String email = principal != null ? principal.getName() : "";

        if (!hasWatchlistAccess(principal)) {
            log.debug("addItem() | return=redirect:/pricing (tier gate)");
            return "redirect:/pricing";
        }

        try {
            WatchlistItemType type = WatchlistItemType.valueOf(itemType.toUpperCase());
            String displayLabel = (label != null && !label.isBlank()) ? label : value;

            WatchlistItem item = new WatchlistItem(
                    UUID.randomUUID().toString(),
                    email,
                    type,
                    value,
                    displayLabel,
                    Instant.now()
            );
            watchlistPort.save(item);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Added \"" + displayLabel + "\" to your watchlist");
            log.info("addItem() | saved watchlist item: type={}, value={}, user={}", type, value, LogSanitizer.maskEmail(email));
        } catch (Exception e) {
            log.error("addItem() | failed to add watchlist item", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Failed to add item: " + e.getMessage());
        }

        log.debug("addItem() | return=redirect:/watchlist");
        return "redirect:/watchlist";
    }

    /**
     * Deletes an item from the user's watchlist.
     */
    @PostMapping("/watchlist/{itemId}/delete")
    public String deleteItem(@PathVariable String itemId,
                             Principal principal,
                             RedirectAttributes redirectAttributes) {
        log.debug("deleteItem() | itemId={}", itemId);

        String email = principal != null ? principal.getName() : "";
        watchlistPort.delete(itemId, email);
        redirectAttributes.addFlashAttribute("successMessage", "Item removed from watchlist");

        log.debug("deleteItem() | return=redirect:/watchlist");
        return "redirect:/watchlist";
    }

    /**
     * Returns true if the user has SUBSCRIBER, DEMO, or ENTERPRISE tier, or is ADMIN.
     */
    private boolean hasWatchlistAccess(Principal principal) {
        log.debug("hasWatchlistAccess() | principal={}", principal != null ? principal.getName() : "null");
        if (principal == null) {
            log.debug("hasWatchlistAccess() | return=false (no principal)");
            return false;
        }
        if (isAdmin(principal)) {
            log.debug("hasWatchlistAccess() | return=true (admin)");
            return true;
        }
        SubscriptionTier tier = subscriberPort.findByEmail(principal.getName())
                .map(Subscriber::tier).orElse(SubscriptionTier.FREE);
        boolean result = tier == SubscriptionTier.SUBSCRIBER || tier == SubscriptionTier.DEMO
                || tier == SubscriptionTier.ENTERPRISE;
        log.debug("hasWatchlistAccess() | return={} (tier={})", result, tier);
        return result;
    }

    private boolean isAdmin(Principal principal) {
        if (principal instanceof Authentication auth) {
            return auth.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        }
        return false;
    }
}
