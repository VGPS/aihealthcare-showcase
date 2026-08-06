package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code deal_signals} table.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-06
 */
@Entity
@Table(name = "deal_signals")
public class DealSignalEntity {

    @Id
    @Column(name = "signal_id", nullable = false, length = 36)
    private String signalId;

    @Column(name = "article_id", nullable = false, length = 500)
    private String articleId;

    @Column(name = "title", nullable = false, length = 1000)
    private String title;

    @Column(name = "signal_type", nullable = false, length = 30)
    private String signalType;

    @Column(name = "company_name", length = 500)
    private String companyName;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "confidence", nullable = false)
    private double confidence;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "deal_amount", length = 100)
    private String dealAmount;

    @Column(name = "counterparty_name", length = 500)
    private String counterpartyName;

    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    @Column(name = "llm_analysis", columnDefinition = "TEXT")
    private String llmAnalysis;

    public DealSignalEntity() {}

    public String getSignalId()                          { return signalId; }
    public void setSignalId(String signalId)             { this.signalId = signalId; }

    public String getArticleId()                         { return articleId; }
    public void setArticleId(String articleId)           { this.articleId = articleId; }

    public String getTitle()                             { return title; }
    public void setTitle(String title)                   { this.title = title; }

    public String getSignalType()                        { return signalType; }
    public void setSignalType(String signalType)         { this.signalType = signalType; }

    public String getCompanyName()                       { return companyName; }
    public void setCompanyName(String companyName)       { this.companyName = companyName; }

    public String getSummary()                           { return summary; }
    public void setSummary(String summary)               { this.summary = summary; }

    public double getConfidence()                        { return confidence; }
    public void setConfidence(double confidence)         { this.confidence = confidence; }

    public Instant getDetectedAt()                       { return detectedAt; }
    public void setDetectedAt(Instant detectedAt)        { this.detectedAt = detectedAt; }

    public String getDealAmount()                        { return dealAmount; }
    public void setDealAmount(String dealAmount)          { this.dealAmount = dealAmount; }

    public String getCounterpartyName()                  { return counterpartyName; }
    public void setCounterpartyName(String counterpartyName) { this.counterpartyName = counterpartyName; }

    public String getSourceUrl()                         { return sourceUrl; }
    public void setSourceUrl(String sourceUrl)            { this.sourceUrl = sourceUrl; }

    public String getLlmAnalysis()                       { return llmAnalysis; }
    public void setLlmAnalysis(String llmAnalysis)        { this.llmAnalysis = llmAnalysis; }
}
