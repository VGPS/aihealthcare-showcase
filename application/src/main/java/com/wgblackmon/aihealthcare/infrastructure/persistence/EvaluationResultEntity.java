package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code evaluation_results} table.
 *
 * <p>Stores a denormalized snapshot of a single prompt evaluation run,
 * including the AI-generated section output and all five quality scores.
 * Section and score fields are stored flat (not in separate tables) because
 * evaluations are write-once, read-many artifacts with low cardinality.
 *
 * <p>The {@code comparison_id} column is nullable — it links this evaluation
 * to a {@link ComparisonResultEntity} when the evaluation was run as part of
 * a side-by-side comparison.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-17
 * @updated 2026-04-17
 */
@Entity
@Table(name = "evaluation_results")
public class EvaluationResultEntity {

    @Id
    @Column(name = "evaluation_id")
    private String evaluationId;

    @Column(name = "variant_id")
    private String variantId;

    @Column(name = "variant_name")
    private String variantName;

    @Column(name = "article_ids", columnDefinition = "TEXT")
    private String articleIdsJoined;

    private String topic;

    private String tone;

    // -- Section fields (denormalized) --

    @Column(name = "section_id")
    private String sectionId;

    @Column(name = "section_type")
    private String sectionType;

    private String headline;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "section_article_ids", columnDefinition = "TEXT")
    private String sectionArticleIdsJoined;

    // -- Score fields --

    private double relevance;
    private double conciseness;

    @Column(name = "attribution_quality")
    private double attributionQuality;

    @Column(name = "tone_match")
    private double toneMatch;

    private double completeness;
    private double overall;

    @Column(name = "scoring_notes", columnDefinition = "TEXT")
    private String scoringNotes;

    // -- Metadata --

    @Column(name = "comparison_id")
    private String comparisonId;

    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

    /** Required no-arg constructor for JPA. */
    public EvaluationResultEntity() {}

    public String getEvaluationId() { return evaluationId; }
    public void setEvaluationId(String evaluationId) { this.evaluationId = evaluationId; }

    public String getVariantId() { return variantId; }
    public void setVariantId(String variantId) { this.variantId = variantId; }

    public String getVariantName() { return variantName; }
    public void setVariantName(String variantName) { this.variantName = variantName; }

    public String getArticleIdsJoined() { return articleIdsJoined; }
    public void setArticleIdsJoined(String articleIdsJoined) { this.articleIdsJoined = articleIdsJoined; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getTone() { return tone; }
    public void setTone(String tone) { this.tone = tone; }

    public String getSectionId() { return sectionId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }

    public String getSectionType() { return sectionType; }
    public void setSectionType(String sectionType) { this.sectionType = sectionType; }

    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getSectionArticleIdsJoined() { return sectionArticleIdsJoined; }
    public void setSectionArticleIdsJoined(String sectionArticleIdsJoined) { this.sectionArticleIdsJoined = sectionArticleIdsJoined; }

    public double getRelevance() { return relevance; }
    public void setRelevance(double relevance) { this.relevance = relevance; }

    public double getConciseness() { return conciseness; }
    public void setConciseness(double conciseness) { this.conciseness = conciseness; }

    public double getAttributionQuality() { return attributionQuality; }
    public void setAttributionQuality(double attributionQuality) { this.attributionQuality = attributionQuality; }

    public double getToneMatch() { return toneMatch; }
    public void setToneMatch(double toneMatch) { this.toneMatch = toneMatch; }

    public double getCompleteness() { return completeness; }
    public void setCompleteness(double completeness) { this.completeness = completeness; }

    public double getOverall() { return overall; }
    public void setOverall(double overall) { this.overall = overall; }

    public String getScoringNotes() { return scoringNotes; }
    public void setScoringNotes(String scoringNotes) { this.scoringNotes = scoringNotes; }

    public String getComparisonId() { return comparisonId; }
    public void setComparisonId(String comparisonId) { this.comparisonId = comparisonId; }

    public Instant getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(Instant evaluatedAt) { this.evaluatedAt = evaluatedAt; }
}
