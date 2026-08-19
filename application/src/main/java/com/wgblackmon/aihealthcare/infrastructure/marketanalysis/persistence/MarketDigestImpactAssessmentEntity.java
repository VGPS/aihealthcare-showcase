package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code market_digest_impact_assessment} table.
 *
 * <p>Each {@link MarketDigestEntryEntity} has exactly five associated assessments
 * (one per {@link com.wgblackmon.aihealthcare.domain.marketanalysis.ImpactDimension}),
 * keyed by {@code entry_id}. Loaded separately by the adapter — no {@code @OneToMany}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Entity
@Table(name = "market_digest_impact_assessment")
public class MarketDigestImpactAssessmentEntity {

    @Id
    @Column(name = "assessment_id", nullable = false, length = 36)
    private String assessmentId;

    @Column(name = "entry_id", nullable = false, length = 36)
    private String entryId;

    @Column(name = "dimension", nullable = false, length = 32)
    private String dimension;

    @Column(name = "direction", nullable = false, length = 16)
    private String direction;

    @Column(name = "rationale", nullable = false, columnDefinition = "TEXT")
    private String rationale;

    protected MarketDigestImpactAssessmentEntity() {}

    public MarketDigestImpactAssessmentEntity(String assessmentId, String entryId,
                                               String dimension, String direction,
                                               String rationale) {
        this.assessmentId = assessmentId;
        this.entryId = entryId;
        this.dimension = dimension;
        this.direction = direction;
        this.rationale = rationale;
    }

    public String getAssessmentId() { return assessmentId; }
    public String getEntryId() { return entryId; }
    public String getDimension() { return dimension; }
    public String getDirection() { return direction; }
    public String getRationale() { return rationale; }
}
