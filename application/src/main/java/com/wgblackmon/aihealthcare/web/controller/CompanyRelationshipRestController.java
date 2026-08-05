package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.CompanyRelationship;
import com.wgblackmon.aihealthcare.domain.port.inbound.MapCompanyRelationshipsUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for company relationship graph endpoints.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
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

    @PostMapping("/detect")
    public ResponseEntity<?> triggerDetection() {
        log.debug("triggerDetection()");
        List<CompanyRelationship> relationships = mapRelationshipsUseCase.detectRelationships();
        log.debug("triggerDetection() | return={} new relationships", relationships.size());
        return ResponseEntity.ok(Map.of("detected", relationships.size()));
    }
}
