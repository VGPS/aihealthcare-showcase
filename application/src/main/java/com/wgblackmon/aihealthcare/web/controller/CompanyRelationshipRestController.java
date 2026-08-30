package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.CompanyRelationship;
import com.wgblackmon.aihealthcare.domain.model.CompanyRelationshipType;
import com.wgblackmon.aihealthcare.domain.port.inbound.MapCompanyRelationshipsUseCase;
import com.wgblackmon.aihealthcare.web.dto.RelationshipGraphResponse;
import com.wgblackmon.aihealthcare.web.dto.VisEdge;
import com.wgblackmon.aihealthcare.web.dto.VisNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * REST controller for company relationship graph endpoints.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-30
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/relationships")
public class CompanyRelationshipRestController {

    private final MapCompanyRelationshipsUseCase mapRelationshipsUseCase;

    public CompanyRelationshipRestController(MapCompanyRelationshipsUseCase mapRelationshipsUseCase) {
        log.debug("CompanyRelationshipRestController() | mapRelationshipsUseCase={}",
                mapRelationshipsUseCase.getClass().getSimpleName());
        this.mapRelationshipsUseCase = mapRelationshipsUseCase;
    }

    @GetMapping
    public ResponseEntity<?> getAllRelationships(
            @RequestParam(required = false) String company) {
        log.debug("getAllRelationships() | company={}", company);

        List<CompanyRelationship> relationships;
        if (company != null && !company.isBlank()) {
            relationships = mapRelationshipsUseCase.getRelationshipsForCompany(company);
        } else {
            relationships = mapRelationshipsUseCase.getAllRelationships();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (CompanyRelationship rel : relationships) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("relationshipId", rel.relationshipId());
            entry.put("sourceCompany", rel.sourceCompany());
            entry.put("targetCompany", rel.targetCompany());
            entry.put("relationshipType", rel.relationshipType().name());
            entry.put("evidenceArticleId", rel.evidenceArticleId());
            entry.put("summary", rel.summary());
            entry.put("confidence", rel.confidence());
            entry.put("detectedAt", rel.detectedAt().toString());
            result.add(entry);
        }

        log.debug("getAllRelationships() | return={} relationships", result.size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/graph")
    public ResponseEntity<RelationshipGraphResponse> getGraphData() {
        log.debug("getGraphData()");

        List<CompanyRelationship> all = mapRelationshipsUseCase.getAllRelationships();
        List<CompanyRelationship> deduped = dedup(all);

        // degree map: company name → edge count
        Map<String, Integer> degreeMap = new LinkedHashMap<>();
        for (CompanyRelationship rel : deduped) {
            degreeMap.put(rel.sourceCompany(),
                    degreeMap.getOrDefault(rel.sourceCompany(), 0) + 1);
            degreeMap.put(rel.targetCompany(),
                    degreeMap.getOrDefault(rel.targetCompany(), 0) + 1);
        }

        List<VisNode> nodes = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : degreeMap.entrySet()) {
            String name = entry.getKey();
            int degree = entry.getValue();
            nodes.add(new VisNode(name, name, degree));
        }

        List<VisEdge> edges = new ArrayList<>();
        for (CompanyRelationship rel : deduped) {
            edges.add(new VisEdge(
                    rel.relationshipId(),
                    rel.sourceCompany(),
                    rel.targetCompany(),
                    rel.relationshipType().name(),
                    colorForType(rel.relationshipType()),
                    rel.confidence(),
                    rel.evidenceArticleId(),
                    rel.summary(),
                    formatDate(rel.detectedAt())));
        }

        RelationshipGraphResponse response = new RelationshipGraphResponse(nodes, edges);
        log.debug("getGraphData() | return={} nodes, {} edges", nodes.size(), edges.size());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/detect")
    public ResponseEntity<?> triggerDetection() {
        log.debug("triggerDetection()");
        List<CompanyRelationship> relationships = mapRelationshipsUseCase.detectRelationships();
        log.debug("triggerDetection() | return={} new relationships", relationships.size());
        return ResponseEntity.ok(Map.of("detected", relationships.size()));
    }

    private List<CompanyRelationship> dedup(List<CompanyRelationship> relationships) {
        log.debug("dedup() | input={}", relationships.size());
        Set<String> seen = new HashSet<>();
        List<CompanyRelationship> result = new ArrayList<>();
        for (CompanyRelationship rel : relationships) {
            String key = rel.sourceCompany().toLowerCase(Locale.ENGLISH) + "|"
                    + rel.targetCompany().toLowerCase(Locale.ENGLISH) + "|"
                    + rel.relationshipType().name();
            if (!seen.contains(key)) {
                seen.add(key);
                result.add(rel);
            }
        }
        log.debug("dedup() | return={}", result.size());
        return result;
    }

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneOffset.UTC);

    private String formatDate(Instant instant) {
        log.debug("formatDate() | instant={}", instant);
        if (instant == null) {
            log.debug("formatDate() | return=null");
            return null;
        }
        String result = DATE_FMT.format(instant);
        log.debug("formatDate() | return={}", result);
        return result;
    }

    private String colorForType(CompanyRelationshipType type) {
        switch (type) {
            case PARTNERSHIP:  return "#3B82F6";
            case ACQUISITION:  return "#EF4444";
            case COMPETITOR:   return "#F97316";
            case INVESTMENT:   return "#22C55E";
            case SUPPLIER:     return "#6B7280";
            case INTEGRATION:  return "#A855F7";
            default:           return "#6B7280";
        }
    }

}
