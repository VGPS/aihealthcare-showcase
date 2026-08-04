package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code framework_analyses} table.
 *
 * <p>Stores the latest framework competitive analysis snapshot for a single
 * company. The companySlug is the natural key (upsert by slug). Structured
 * fields (dimensions, strengths, weaknesses, recent developments) are stored
 * as JSON TEXT columns and deserialized by {@link FrameworkAnalysisPersistenceAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
@Entity
@Table(name = "framework_analyses")
public class FrameworkAnalysisEntity {

    @Id
    @Column(name = "company_slug", length = 200)
    private String companySlug;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "overall_assessment", columnDefinition = "TEXT")
    private String overallAssessment;

    @Column(name = "dimensions_json", columnDefinition = "TEXT")
    private String dimensionsJson;

    @Column(name = "strengths_json", columnDefinition = "TEXT")
    private String strengthsJson;

    @Column(name = "weaknesses_json", columnDefinition = "TEXT")
    private String weaknessesJson;

    @Column(name = "recent_developments_json", columnDefinition = "TEXT")
    private String recentDevelopmentsJson;

    @Column(name = "overall_score")
    private int overallScore;

    @Column(name = "article_count")
    private int articleCount;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    /** Required no-arg constructor for JPA. */
    public FrameworkAnalysisEntity() {}

    public String getCompanySlug() { return companySlug; }
    public void setCompanySlug(String companySlug) { this.companySlug = companySlug; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getOverallAssessment() { return overallAssessment; }
    public void setOverallAssessment(String overallAssessment) { this.overallAssessment = overallAssessment; }

    public String getDimensionsJson() { return dimensionsJson; }
    public void setDimensionsJson(String dimensionsJson) { this.dimensionsJson = dimensionsJson; }

    public String getStrengthsJson() { return strengthsJson; }
    public void setStrengthsJson(String strengthsJson) { this.strengthsJson = strengthsJson; }

    public String getWeaknessesJson() { return weaknessesJson; }
    public void setWeaknessesJson(String weaknessesJson) { this.weaknessesJson = weaknessesJson; }

    public String getRecentDevelopmentsJson() { return recentDevelopmentsJson; }
    public void setRecentDevelopmentsJson(String recentDevelopmentsJson) { this.recentDevelopmentsJson = recentDevelopmentsJson; }

    public int getOverallScore() { return overallScore; }
    public void setOverallScore(int overallScore) { this.overallScore = overallScore; }

    public int getArticleCount() { return articleCount; }
    public void setArticleCount(int articleCount) { this.articleCount = articleCount; }

    public Instant getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(Instant analyzedAt) { this.analyzedAt = analyzedAt; }
}
