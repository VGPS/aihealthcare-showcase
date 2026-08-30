package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.CompanyRelationship;
import com.wgblackmon.aihealthcare.domain.port.inbound.MapCompanyRelationshipsUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Thymeleaf controller for the company relationship graph page at
 * {@code /dashboard/relationships}.
 *
 * <p>Displays inter-company relationships (partnerships, acquisitions,
 * investments, integrations) detected from harvested articles.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-30
 */
@Slf4j
@Controller
public class CompanyRelationshipController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneOffset.UTC);

    private final MapCompanyRelationshipsUseCase mapRelationshipsUseCase;

    public CompanyRelationshipController(MapCompanyRelationshipsUseCase mapRelationshipsUseCase) {
        log.debug("CompanyRelationshipController() | mapRelationshipsUseCase={}",
                mapRelationshipsUseCase.getClass().getSimpleName());
        this.mapRelationshipsUseCase = mapRelationshipsUseCase;
    }

    @GetMapping("/dashboard/relationships/graph")
    public String relationshipGraphPage(Model model) {
        log.debug("relationshipGraphPage()");
        model.addAttribute("activePage", "relationships");
        log.debug("relationshipGraphPage() | return=relationship-graph");
        return "relationship-graph";
    }

    @GetMapping("/dashboard/relationships")
    public String relationshipsPage(@RequestParam(required = false) String company,
                                     @RequestParam(required = false, defaultValue = "detected_desc") String sort,
                                     Model model) {
        log.debug("relationshipsPage() | company={}, sort={}", company, sort);

        List<CompanyRelationship> relationships;
        if (company != null && !company.isBlank()) {
            relationships = mapRelationshipsUseCase.getRelationshipsForCompany(company);
        } else {
            relationships = mapRelationshipsUseCase.getAllRelationships();
        }

        List<CompanyRelationship> deduped = dedup(relationships);
        deduped = sortRelationships(deduped, sort);

        List<Map<String, Object>> relList = new ArrayList<>();
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        Set<String> companyNames = new LinkedHashSet<>();

        for (CompanyRelationship rel : deduped) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("relationshipId", rel.relationshipId());
            entry.put("sourceCompany", rel.sourceCompany());
            entry.put("targetCompany", rel.targetCompany());
            entry.put("relationshipType", rel.relationshipType().name());
            entry.put("evidenceArticleId", rel.evidenceArticleId());
            entry.put("summary", rel.summary());
            entry.put("confidence", String.format("%.0f%%", rel.confidence() * 100));
            entry.put("detectedAt", DISPLAY_FMT.format(rel.detectedAt()));
            relList.add(entry);

            String typeName = rel.relationshipType().name();
            typeCounts.put(typeName, typeCounts.getOrDefault(typeName, 0) + 1);

            companyNames.add(rel.sourceCompany());
            companyNames.add(rel.targetCompany());
        }

        model.addAttribute("relationships", relList);
        model.addAttribute("typeCounts", typeCounts);
        model.addAttribute("totalRelationships", relList.size());
        model.addAttribute("companyCount", companyNames.size());
        List<String> sortedCompanyNames = new ArrayList<>(companyNames);
        Collections.sort(sortedCompanyNames);
        model.addAttribute("companyNames", sortedCompanyNames);
        model.addAttribute("filterCompany", company);
        model.addAttribute("sort", sort);
        model.addAttribute("activePage", "relationships");

        log.debug("relationshipsPage() | return=relationships ({} entries, {} deduped)", relationships.size(), relList.size());
        return "relationships";
    }

    private List<CompanyRelationship> dedup(List<CompanyRelationship> relationships) {
        log.debug("dedup() | input={}", relationships.size());
        Set<String> seen = new HashSet<>();
        List<CompanyRelationship> result = new ArrayList<>();
        for (CompanyRelationship rel : relationships) {
            String dedupKey = rel.sourceCompany().toLowerCase(Locale.ENGLISH) + "|"
                    + rel.targetCompany().toLowerCase(Locale.ENGLISH) + "|"
                    + rel.relationshipType().name();
            if (!seen.contains(dedupKey)) {
                seen.add(dedupKey);
                result.add(rel);
            }
        }
        log.debug("dedup() | return={}", result.size());
        return result;
    }

    private List<CompanyRelationship> sortRelationships(List<CompanyRelationship> list, String sort) {
        log.debug("sortRelationships() | sort={}, size={}", sort, list.size());
        if (list.isEmpty()) {
            log.debug("sortRelationships() | return=empty list");
            return list;
        }

        Comparator<CompanyRelationship> comparator;
        switch (sort) {
            case "type":
                comparator = Comparator.comparing(r -> r.relationshipType().name());
                break;
            case "type_desc":
                comparator = Comparator.comparing((CompanyRelationship r) -> r.relationshipType().name()).reversed();
                break;
            case "source":
                comparator = Comparator.comparing(r -> r.sourceCompany().toLowerCase(Locale.ENGLISH));
                break;
            case "source_desc":
                comparator = Comparator.comparing((CompanyRelationship r) -> r.sourceCompany().toLowerCase(Locale.ENGLISH)).reversed();
                break;
            case "target":
                comparator = Comparator.comparing(r -> r.targetCompany().toLowerCase(Locale.ENGLISH));
                break;
            case "target_desc":
                comparator = Comparator.comparing((CompanyRelationship r) -> r.targetCompany().toLowerCase(Locale.ENGLISH)).reversed();
                break;
            case "confidence":
                comparator = Comparator.comparingDouble(CompanyRelationship::confidence);
                break;
            case "confidence_desc":
                comparator = Comparator.comparingDouble(CompanyRelationship::confidence).reversed();
                break;
            case "detected":
                comparator = Comparator.comparing(CompanyRelationship::detectedAt);
                break;
            case "detected_desc":
                comparator = Comparator.comparing(CompanyRelationship::detectedAt).reversed();
                break;
            default:
                comparator = Comparator.comparing(CompanyRelationship::detectedAt).reversed();
                break;
        }

        List<CompanyRelationship> sorted = new ArrayList<>(list);
        sorted.sort(comparator);
        log.debug("sortRelationships() | return=sorted list, size={}", sorted.size());
        return sorted;
    }
}
