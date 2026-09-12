package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.RegulatoryBody;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEventType;
import com.wgblackmon.aihealthcare.domain.port.inbound.MonitorRegulatoryEventsUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * Thymeleaf controller that renders the regulatory alerts dashboard page.
 *
 * <p>Serves {@code GET /dashboard/regulatory} by loading recent
 * {@link RegulatoryEvent} records and populating the Thymeleaf model
 * with events grouped and filtered by regulatory body and type.
 *
 * <p>Tier gating: SUBSCRIBER and DEMO users see all events;
 * FREE users see only the 5 most recent.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-09-12
 */
@Slf4j
@Controller
public class RegulatoryController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DisplayFormats.SHORT_DATE
                    .withZone(ZoneId.of("America/New_York"));

    private static final int FREE_EVENT_LIMIT = 5;
    private static final int FULL_EVENT_LIMIT = 50;

    private final MonitorRegulatoryEventsUseCase regulatoryUseCase;
    private final TierResolver tierResolver;

    public RegulatoryController(MonitorRegulatoryEventsUseCase regulatoryUseCase,
                                TierResolver tierResolver) {
        log.debug("RegulatoryController() | regulatoryUseCase={}, tierResolver={}",
                  regulatoryUseCase, tierResolver);
        this.regulatoryUseCase = regulatoryUseCase;
        this.tierResolver = tierResolver;
    }

    /**
     * Renders the regulatory alerts page.
     *
     * @param filter    optional filter: "fda", "cms", or null for all
     * @param sort      optional sort column with optional _desc suffix
     * @param principal the authenticated user, or null for anonymous
     * @param model     Thymeleaf model
     * @return the "regulatory" view name
     */
    @GetMapping("/dashboard/regulatory")
    public String regulatory(@RequestParam(required = false) String filter,
                              @RequestParam(required = false, defaultValue = "published_desc") String sort,
                              Principal principal,
                              Model model) {
        log.debug("regulatory() | filter={}, sort={}, principal={}", filter, sort,
                  principal != null ? principal.getName() : "anonymous");

        boolean fullAccess = tierResolver.hasFullAccess(principal);
        int limit = fullAccess ? FULL_EVENT_LIMIT : FREE_EVENT_LIMIT;

        List<RegulatoryEvent> events;
        if ("fda".equalsIgnoreCase(filter)) {
            events = regulatoryUseCase.getEventsByBody(RegulatoryBody.FDA, limit);
        } else if ("cms".equalsIgnoreCase(filter)) {
            events = regulatoryUseCase.getEventsByBody(RegulatoryBody.CMS, limit);
        } else {
            events = regulatoryUseCase.getRecentEvents(limit);
        }

        // Sort the results
        events = sortEvents(events, sort);

        // Format dates server-side
        Map<String, String> eventDates = new HashMap<>();
        Map<String, String> discoveredDates = new HashMap<>();
        for (RegulatoryEvent event : events) {
            if (event.publishedAt() != null) {
                eventDates.put(event.eventId(), DISPLAY_FMT.format(event.publishedAt()));
            }
            discoveredDates.put(event.eventId(), DISPLAY_FMT.format(event.discoveredAt()));
        }

        // Type counts for summary badges
        int fda510kCount = 0;
        int deNovoCount = 0;
        int cmsCount = 0;
        int otherCount = 0;
        for (RegulatoryEvent event : events) {
            if (event.eventType() == RegulatoryEventType.FDA_510K_CLEARANCE) {
                fda510kCount++;
            } else if (event.eventType() == RegulatoryEventType.FDA_DE_NOVO_CLASSIFICATION) {
                deNovoCount++;
            } else if (event.eventType() == RegulatoryEventType.CMS_PROPOSED_RULE
                    || event.eventType() == RegulatoryEventType.CMS_FINAL_RULE
                    || event.eventType() == RegulatoryEventType.CMS_NCD) {
                cmsCount++;
            } else {
                otherCount++;
            }
        }

        model.addAttribute("events", events);
        model.addAttribute("eventDates", eventDates);
        model.addAttribute("discoveredDates", discoveredDates);
        model.addAttribute("eventCount", events.size());
        model.addAttribute("fda510kCount", fda510kCount);
        model.addAttribute("deNovoCount", deNovoCount);
        model.addAttribute("cmsCount", cmsCount);
        model.addAttribute("otherCount", otherCount);
        model.addAttribute("filter", filter);
        model.addAttribute("sort", sort);
        model.addAttribute("fullAccess", fullAccess);

        log.debug("regulatory() | return=regulatory, eventCount={}", events.size());
        return "regulatory";
    }

    /**
     * Sorts the event list by the specified column.
     */
    private List<RegulatoryEvent> sortEvents(List<RegulatoryEvent> events, String sort) {
        log.debug("sortEvents() | sort={}, size={}", sort, events.size());
        if (events.isEmpty()) {
            log.debug("sortEvents() | return=empty list");
            return events;
        }

        boolean descending = sort != null && sort.endsWith("_desc");
        String column = descending ? sort.substring(0, sort.length() - 5) : sort;

        Comparator<RegulatoryEvent> comparator;
        if ("type".equalsIgnoreCase(column)) {
            comparator = Comparator.comparing(e -> e.eventType().name());
        } else if ("title".equalsIgnoreCase(column)) {
            comparator = Comparator.comparing(RegulatoryEvent::title, String.CASE_INSENSITIVE_ORDER);
        } else if ("applicant".equalsIgnoreCase(column)) {
            comparator = Comparator.comparing(
                    e -> e.applicantName() != null ? e.applicantName() : "",
                    String.CASE_INSENSITIVE_ORDER);
        } else if ("reference".equalsIgnoreCase(column)) {
            comparator = Comparator.comparing(
                    e -> e.referenceNumber() != null ? e.referenceNumber() : "",
                    String.CASE_INSENSITIVE_ORDER);
        } else if ("outcome".equalsIgnoreCase(column)) {
            comparator = Comparator.comparing(
                    e -> e.outcomeStatus() != null ? e.outcomeStatus().name() : "",
                    String.CASE_INSENSITIVE_ORDER);
        } else {
            // Default: published date
            comparator = Comparator.comparing(
                    e -> e.publishedAt() != null ? e.publishedAt() : e.discoveredAt());
        }

        if (descending) {
            comparator = comparator.reversed();
        }

        List<RegulatoryEvent> sorted = new ArrayList<>(events);
        sorted.sort(comparator);
        log.debug("sortEvents() | return=sorted list, size={}", sorted.size());
        return sorted;
    }

}
