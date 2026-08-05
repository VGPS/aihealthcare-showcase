package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.CompanyRelationship;
import com.wgblackmon.aihealthcare.domain.port.inbound.MapCompanyRelationshipsUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
 * @updated 2026-08-04
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

    @GetMapping("/dashboard/relationships")
    public String relationshipsPage(@RequestParam(required = false) String company,
                                     Model model) {
        log.debug("relationshipsPage() | company={}", company);

        List<CompanyRelationship> relationships;
        if (company != null && !company.isBlank()) {
            relationships = mapRelationshipsUseCase.getRelationshipsForCompany(company);
        } else {
            relationships = mapRelationshipsUseCase.getAllRelationships();
        }

        List<Map<String, Object>> relList = new ArrayList<>();
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        Set<String> companyNames = new LinkedHashSet<>();

        for (CompanyRelationship rel : relationships) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("relationshipId", rel.relationshipId());
            entry.put("sourceCompany", rel.sourceCompany());
            entry.put("targetCompany", rel.targetCompany());
            entry.put("relationshipType", rel.relationshipType().name());
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
        model.addAttribute("totalRelationships", relationships.size());
        model.addAttribute("companyCount", companyNames.size());
        model.addAttribute("companyNames", new ArrayList<>(companyNames));
        model.addAttribute("filterCompany", company);
        model.addAttribute("activePage", "relationships");

        log.debug("relationshipsPage() | return=relationships ({} entries)", relationships.size());
        return "relationships";
    }
}
