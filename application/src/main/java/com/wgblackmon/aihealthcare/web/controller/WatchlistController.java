package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItem;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItemType;
import com.wgblackmon.aihealthcare.domain.model.WatchlistMatch;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistMatchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistPort;
import lombok.extern.slf4j.Slf4j;
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
 * @updated 2026-07-22
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
    private final AppUserPort appUserPort;

    public WatchlistController(WatchlistPort watchlistPort,
                                WatchlistMatchPort watchlistMatchPort,
                                AppUserPort appUserPort) {
        log.debug("WatchlistController() | watchlistPort={}, watchlistMatchPort={}, appUserPort={}",
                watchlistPort.getClass().getSimpleName(),
                watchlistMatchPort.getClass().getSimpleName(),
                appUserPort.getClass().getSimpleName());
        this.watchlistPort = watchlistPort;
        this.watchlistMatchPort = watchlistMatchPort;
        this.appUserPort = appUserPort;
    }

    /**
     * Renders the watchlist page with the user's tracked items and recent matches.
     */
    @GetMapping("/watchlist")
    public String index(Model model, Principal principal) {
        log.debug("index() | principal={}", principal != null ? principal.getName() : "anonymous");

        String email = principal != null ? principal.getName() : "";

        // Tier gate — only SUBSCRIBER and DEMO can access
        if (!hasWatchlistAccess(email)) {
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

        // Format match dates
        Map<String, String> matchDates = new HashMap<>();
        for (WatchlistMatch match : matches) {
            matchDates.put(match.matchId(), DISPLAY_FMT.format(match.matchedOn()));
        }

        model.addAttribute("companyItems", companyItems);
        model.addAttribute("keywordItems", keywordItems);
        model.addAttribute("topicItems", topicItems);
        model.addAttribute("itemDates", itemDates);
        model.addAttribute("itemCount", items.size());
        model.addAttribute("matches", matches);
        model.addAttribute("matchCount", matches.size());
        model.addAttribute("matchDates", matchDates);
        model.addAttribute("itemLabels", itemLabels);
        model.addAttribute("itemTypes", itemTypes);

        log.debug("index() | return=watchlist, items={}, matches={}", items.size(), matches.size());
        return "watchlist";
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

        if (!hasWatchlistAccess(email)) {
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
            log.info("addItem() | saved watchlist item: type={}, value={}, user={}", type, value, email);
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
     * Returns true if the user has SUBSCRIBER or DEMO tier (or is ADMIN).
     */
    private boolean hasWatchlistAccess(String email) {
        Optional<AppUser> userOpt = appUserPort.findByEmail(email);
        if (userOpt.isEmpty()) {
            return false;
        }
        AppUser user = userOpt.get();
        if ("ADMIN".equals(user.role())) {
            return true;
        }
        SubscriptionTier tier = user.tier() != null ? user.tier() : SubscriptionTier.FREE;
        return tier == SubscriptionTier.SUBSCRIBER || tier == SubscriptionTier.DEMO;
    }
}
