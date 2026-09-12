package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.marketanalysis.DealTerms;
import com.wgblackmon.aihealthcare.domain.marketanalysis.PrivateFundingRound;
import com.wgblackmon.aihealthcare.domain.marketanalysis.RegulatoryTracker;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.DealTermsPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.PrivateFundingPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.RegulatoryTrackerRepository;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Thymeleaf controller for the Market Enrichment page.
 *
 * <p>Renders a tabbed view at {@code /dashboard/market/enrichment} showing
 * regulatory trackers, private funding rounds, and deal terms extracted
 * from the daily market digest pipeline.
 *
 * <p>Tier gating: FREE users see a limited view (3 items per tab);
 * SUBSCRIBER/DEMO/ADMIN users see the full dataset.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-07
 * @updated 2026-09-12
 */
@Slf4j
@Controller
public class MarketEnrichmentController {

    private static final int FREE_LIMIT = 3;
    private static final DateTimeFormatter DISPLAY_FMT =
            DisplayFormats.SHORT_DATE;

    private final RegulatoryTrackerRepository regulatoryTrackerRepository;
    private final PrivateFundingPort privateFundingPort;
    private final DealTermsPort dealTermsPort;
    private final SubscriberPort subscriberPort;
    private final TierResolver tierResolver;

    public MarketEnrichmentController(
            @Autowired(required = false) RegulatoryTrackerRepository regulatoryTrackerRepository,
            @Autowired(required = false) PrivateFundingPort privateFundingPort,
            @Autowired(required = false) DealTermsPort dealTermsPort,
            SubscriberPort subscriberPort,
            TierResolver tierResolver) {
        log.debug("MarketEnrichmentController() | regulatoryPresent={}, fundingPresent={}, dealTermsPresent={}",
                regulatoryTrackerRepository != null, privateFundingPort != null, dealTermsPort != null);
        this.regulatoryTrackerRepository = regulatoryTrackerRepository;
        this.privateFundingPort = privateFundingPort;
        this.dealTermsPort = dealTermsPort;
        this.subscriberPort = subscriberPort;
        this.tierResolver = tierResolver;
        log.debug("MarketEnrichmentController() | return=void");
    }



    @GetMapping("/dashboard/market/enrichment")
    public String enrichment(Model model, Principal principal) {
        log.debug("enrichment() | principal={}", principal != null ? principal.getName() : "null");

        boolean fullAccess = tierResolver != null && tierResolver.hasFullAccess(principal);

        List<RegulatoryTracker> trackers = regulatoryTrackerRepository != null
                ? regulatoryTrackerRepository.findAll() : List.of();
        List<RegulatoryTracker> approaching = regulatoryTrackerRepository != null
                ? regulatoryTrackerRepository.findApproachingDeadlines(LocalDate.now().plusDays(30))
                : List.of();

        List<PrivateFundingRound> fundingRounds = privateFundingPort != null
                ? privateFundingPort.findRecentRounds(Instant.now().minus(180, ChronoUnit.DAYS), null)
                : List.of();

        Map<String, DealTerms> dealTermsMap = dealTermsPort != null
                ? dealTermsPort.findAllWithHeadlines() : Map.of();

        int totalTrackers = trackers.size();
        int totalFunding = fundingRounds.size();
        int totalDealTerms = dealTermsMap.size();

        if (!fullAccess) {
            if (trackers.size() > FREE_LIMIT) {
                trackers = trackers.subList(0, FREE_LIMIT);
            }
            if (fundingRounds.size() > FREE_LIMIT) {
                fundingRounds = fundingRounds.subList(0, FREE_LIMIT);
            }
        }

        Map<Integer, String> trackerDates = new java.util.HashMap<>();
        for (int i = 0; i < trackers.size(); i++) {
            trackerDates.put(i, formatInstant(trackers.get(i).lastUpdatedAt()));
        }
        Map<Integer, String> trackerDeadlines = new java.util.HashMap<>();
        for (int i = 0; i < trackers.size(); i++) {
            trackerDeadlines.put(i, formatLocalDate(trackers.get(i).commentDeadline()));
        }
        Map<Integer, String> fundingDates = new java.util.HashMap<>();
        Map<Integer, String> fundingAmounts = new java.util.HashMap<>();
        Map<Integer, String> fundingInvestors = new java.util.HashMap<>();
        for (int i = 0; i < fundingRounds.size(); i++) {
            fundingDates.put(i, formatInstant(fundingRounds.get(i).announcedAt()));
            fundingAmounts.put(i, formatUsd(fundingRounds.get(i).amountUsd()));
            List<String> investors = fundingRounds.get(i).leadInvestors();
            fundingInvestors.put(i, investors != null && !investors.isEmpty()
                    ? String.join(", ", investors) : "Not disclosed");
        }

        Map<String, String> dealUpfrontFmt = new java.util.HashMap<>();
        Map<String, String> dealMilestoneFmt = new java.util.HashMap<>();
        for (Map.Entry<String, DealTerms> e : dealTermsMap.entrySet()) {
            dealUpfrontFmt.put(e.getKey(), formatUsd(e.getValue().upfrontCashUsd()));
            dealMilestoneFmt.put(e.getKey(), formatUsd(e.getValue().milestonePaymentsUsd()));
        }
        model.addAttribute("dealUpfrontFmt", dealUpfrontFmt);
        model.addAttribute("dealMilestoneFmt", dealMilestoneFmt);

        model.addAttribute("activePage", "market-enrichment");
        model.addAttribute("trackers", trackers);
        model.addAttribute("trackerDates", trackerDates);
        model.addAttribute("trackerDeadlines", trackerDeadlines);
        model.addAttribute("approachingCount", approaching.size());
        model.addAttribute("fundingRounds", fundingRounds);
        model.addAttribute("fundingDates", fundingDates);
        model.addAttribute("fundingAmounts", fundingAmounts);
        model.addAttribute("fundingInvestors", fundingInvestors);
        model.addAttribute("dealTermsMap", dealTermsMap);
        model.addAttribute("totalTrackers", totalTrackers);
        model.addAttribute("totalFunding", totalFunding);
        model.addAttribute("totalDealTerms", totalDealTerms);
        model.addAttribute("fullAccess", fullAccess);
        model.addAttribute("freeLimit", FREE_LIMIT);

        log.debug("enrichment() | return=market-enrichment, trackers={}, funding={}, dealTerms={}",
                trackers.size(), fundingRounds.size(), dealTermsMap.size());
        return "market-enrichment";
    }

    String formatInstant(Instant instant) {
        if (instant == null) return "";
        return DISPLAY_FMT.format(instant.atZone(ZoneId.of("America/Chicago")));
    }

    String formatLocalDate(LocalDate date) {
        if (date == null) return "";
        return DISPLAY_FMT.format(date);
    }

    String formatUsd(Long amount) {
        if (amount == null) return "—";
        if (amount >= 1_000_000_000) {
            return String.format("$%.1fB", amount / 1_000_000_000.0);
        } else if (amount >= 1_000_000) {
            return String.format("$%.1fM", amount / 1_000_000.0);
        } else if (amount >= 1_000) {
            return String.format("$%.0fK", amount / 1_000.0);
        }
        return "$" + amount;
    }
}
