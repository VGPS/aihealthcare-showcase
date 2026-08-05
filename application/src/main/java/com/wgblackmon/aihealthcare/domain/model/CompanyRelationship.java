package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * A detected relationship between two companies in the AI healthcare
 * ecosystem, extracted from articles and deal signals.
 *
 * <p>Directional: {@code sourceCompany} acts upon {@code targetCompany}
 * (e.g. "Google acquires DeepMind" → source=Google, target=DeepMind,
 * type=ACQUISITION).
 *
 * @param relationshipId  unique identifier (UUID)
 * @param sourceCompany   the initiating company name
 * @param targetCompany   the receiving company name
 * @param relationshipType classification of the relationship
 * @param evidenceArticleId article ID that evidences this relationship
 * @param summary         one-sentence description of the relationship
 * @param confidence      detection confidence [0.0, 1.0]
 * @param detectedAt      when this relationship was detected
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public record CompanyRelationship(
        String relationshipId,
        String sourceCompany,
        String targetCompany,
        CompanyRelationshipType relationshipType,
        String evidenceArticleId,
        String summary,
        double confidence,
        Instant detectedAt
) {

    public CompanyRelationship {
        if (relationshipId == null || relationshipId.isBlank()) {
            throw new IllegalArgumentException("relationshipId must not be blank");
        }
        if (sourceCompany == null || sourceCompany.isBlank()) {
            throw new IllegalArgumentException("sourceCompany must not be blank");
        }
        if (targetCompany == null || targetCompany.isBlank()) {
            throw new IllegalArgumentException("targetCompany must not be blank");
        }
        if (relationshipType == null) {
            throw new IllegalArgumentException("relationshipType must not be null");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        if (detectedAt == null) {
            throw new IllegalArgumentException("detectedAt must not be null");
        }
    }
}
