package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.HarmType;
import com.wgblackmon.aihealthcare.domain.model.IncidentStatus;
import com.wgblackmon.aihealthcare.domain.model.IncidentType;
import com.wgblackmon.aihealthcare.domain.model.LiabilityIncident;
import com.wgblackmon.aihealthcare.domain.port.outbound.LiabilityIncidentPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Public read-only view of the AI Healthcare Liability Registry.
 *
 * <p>Displays all approved incidents (status != PENDING_REVIEW). No authentication
 * required — this page is part of the public archive. Supports optional
 * {@code type}, {@code harmType}, {@code status}, and {@code sort} query-param filters.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-11-15
 * @updated 2026-11-15
 */
@Slf4j
@Controller
@RequestMapping("/liability")
public class LiabilityController {

    private final LiabilityIncidentPort port;

    public LiabilityController(LiabilityIncidentPort port) {
        log.debug("LiabilityController() | port={}", port.getClass().getSimpleName());
        this.port = port;
    }

    @GetMapping
    public String registry(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String harmType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "dateDesc") String sort,
            Model model) {
        log.debug("registry() | type={}, harmType={}, status={}, sort={}", type, harmType, status, sort);

        List<LiabilityIncident> all = port.findAll().stream()
                .filter(i -> i.status() != IncidentStatus.PENDING_REVIEW)
                .collect(Collectors.toList());

        List<LiabilityIncident> filtered = all.stream()
                .filter(i -> type     == null || type.isBlank()     || i.incidentType().name().equals(type))
                .filter(i -> harmType == null || harmType.isBlank() || i.harmType().name().equals(harmType))
                .filter(i -> status   == null || status.isBlank()   || i.status().name().equals(status))
                .sorted(sortComparator(sort))
                .collect(Collectors.toList());

        model.addAttribute("incidents",     filtered);
        model.addAttribute("totalCount",    all.size());
        model.addAttribute("incidentTypes", IncidentType.values());
        model.addAttribute("harmTypes",     HarmType.values());
        model.addAttribute("statuses",
                Arrays.stream(IncidentStatus.values())
                      .filter(s -> s != IncidentStatus.PENDING_REVIEW)
                      .collect(Collectors.toList()));
        model.addAttribute("activeType",     type);
        model.addAttribute("activeHarmType", harmType);
        model.addAttribute("activeStatus",   status);
        model.addAttribute("sort",           sort);

        log.debug("registry() | return=liability-public, filtered={} of {}, sort={}", filtered.size(), all.size(), sort);
        return "liability-public";
    }

    private Comparator<LiabilityIncident> sortComparator(String sort) {
        Comparator<LocalDate> dateOrder = "dateAsc".equals(sort)
                ? Comparator.naturalOrder()
                : Comparator.reverseOrder();
        return Comparator.comparing(
                LiabilityIncident::filingDate,
                Comparator.nullsLast(dateOrder));
    }
}
