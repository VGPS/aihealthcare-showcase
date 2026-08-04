package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.port.inbound.AnalyzeCompanySentimentUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.AnalyzeFrameworksUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectLegalTrendsUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectTrendsUseCase;
import com.wgblackmon.aihealthcare.infrastructure.ai.EmbeddingScheduler;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.web.WebMonitoringScheduler;
import com.wgblackmon.aihealthcare.infrastructure.research.ResearchHarvestScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Sequences all post-harvest analysis pipelines to run in succession.
 *
 * <p>Called by {@link com.wgblackmon.aihealthcare.infrastructure.ingestion.feed.FeedHarvestScheduler}
 * after startup harvest and after each daily harvest. Each pipeline is wrapped in
 * try-catch so a single failure never aborts the remaining pipelines.
 *
 * <p>Pipeline execution order:
 * <ol>
 *   <li>Competitor page scraping</li>
 *   <li>HuggingFace model discovery</li>
 *   <li>Regulatory event harvest</li>
 *   <li>Clinical trial harvest</li>
 *   <li>Embedding (vector store)</li>
 *   <li>Framework competitive analysis</li>
 *   <li>Company discovery</li>
 *   <li>Sentiment analysis</li>
 *   <li>Trend detection</li>
 *   <li>Legal trend detection</li>
 *   <li>Research harvest</li>
 * </ol>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
@Slf4j
@Component
public class StartupPipelineOrchestrator {

    private final WebMonitoringScheduler webMonitoringScheduler;
    private final RegulatoryHarvestScheduler regulatoryHarvestScheduler;
    private final ClinicalTrialHarvestScheduler clinicalTrialHarvestScheduler;
    private final EmbeddingScheduler embeddingScheduler;
    private final AnalyzeFrameworksUseCase analyzeFrameworksUseCase;
    private final CompanyDiscoveryScheduler companyDiscoveryScheduler;
    private final AnalyzeCompanySentimentUseCase sentimentUseCase;
    private final DetectTrendsUseCase detectTrendsUseCase;
    private final DetectLegalTrendsUseCase detectLegalTrendsUseCase;
    private final ResearchHarvestScheduler researchHarvestScheduler;

    public StartupPipelineOrchestrator(
            @Autowired(required = false) WebMonitoringScheduler webMonitoringScheduler,
            @Autowired(required = false) RegulatoryHarvestScheduler regulatoryHarvestScheduler,
            @Autowired(required = false) ClinicalTrialHarvestScheduler clinicalTrialHarvestScheduler,
            @Autowired(required = false) EmbeddingScheduler embeddingScheduler,
            @Autowired(required = false) AnalyzeFrameworksUseCase analyzeFrameworksUseCase,
            @Autowired(required = false) CompanyDiscoveryScheduler companyDiscoveryScheduler,
            @Autowired(required = false) AnalyzeCompanySentimentUseCase sentimentUseCase,
            @Autowired(required = false) DetectTrendsUseCase detectTrendsUseCase,
            @Autowired(required = false) DetectLegalTrendsUseCase detectLegalTrendsUseCase,
            @Autowired(required = false) ResearchHarvestScheduler researchHarvestScheduler) {
        log.debug("StartupPipelineOrchestrator() | initializing with {} available pipelines",
                countNonNull(webMonitoringScheduler, regulatoryHarvestScheduler,
                        clinicalTrialHarvestScheduler, embeddingScheduler,
                        analyzeFrameworksUseCase, companyDiscoveryScheduler,
                        sentimentUseCase, detectTrendsUseCase,
                        detectLegalTrendsUseCase, researchHarvestScheduler));
        this.webMonitoringScheduler = webMonitoringScheduler;
        this.regulatoryHarvestScheduler = regulatoryHarvestScheduler;
        this.clinicalTrialHarvestScheduler = clinicalTrialHarvestScheduler;
        this.embeddingScheduler = embeddingScheduler;
        this.analyzeFrameworksUseCase = analyzeFrameworksUseCase;
        this.companyDiscoveryScheduler = companyDiscoveryScheduler;
        this.sentimentUseCase = sentimentUseCase;
        this.detectTrendsUseCase = detectTrendsUseCase;
        this.detectLegalTrendsUseCase = detectLegalTrendsUseCase;
        this.researchHarvestScheduler = researchHarvestScheduler;
    }

    /**
     * Runs all post-harvest pipelines in succession. Each step is isolated —
     * a failure in one pipeline does not prevent subsequent pipelines from running.
     */
    public void runAllPipelines() {
        log.info("runAllPipelines() | starting full pipeline cascade");
        long start = System.currentTimeMillis();

        runStep("Competitor pages", () -> {
            if (webMonitoringScheduler != null) {
                webMonitoringScheduler.harvestCompetitorPages();
            }
        });

        runStep("HuggingFace models", () -> {
            if (webMonitoringScheduler != null) {
                webMonitoringScheduler.harvestHuggingFaceModels();
            }
        });

        runStep("Regulatory events", () -> {
            if (regulatoryHarvestScheduler != null) {
                regulatoryHarvestScheduler.runDailyRegulatoryHarvest();
            }
        });

        runStep("Clinical trials", () -> {
            if (clinicalTrialHarvestScheduler != null) {
                clinicalTrialHarvestScheduler.runDailyClinicalTrialHarvest();
            }
        });

        runStep("Vector embeddings", () -> {
            if (embeddingScheduler != null) {
                embeddingScheduler.embedArticles();
            }
        });

        runStep("Framework analysis", () -> {
            if (analyzeFrameworksUseCase != null) {
                analyzeFrameworksUseCase.analyzeAll();
            }
        });

        runStep("Company discovery", () -> {
            if (companyDiscoveryScheduler != null) {
                companyDiscoveryScheduler.runWeeklyCompanyDiscovery();
            }
        });

        runStep("Sentiment analysis", () -> {
            if (sentimentUseCase != null) {
                sentimentUseCase.analyzeAll();
            }
        });

        runStep("Trend detection", () -> {
            if (detectTrendsUseCase != null) {
                detectTrendsUseCase.detectTrends();
            }
        });

        runStep("Legal trend detection", () -> {
            if (detectLegalTrendsUseCase != null) {
                detectLegalTrendsUseCase.detectLegalTrends();
            }
        });

        runStep("Research harvest", () -> {
            if (researchHarvestScheduler != null) {
                researchHarvestScheduler.harvestResearchTopics();
            }
        });

        long elapsed = (System.currentTimeMillis() - start) / 1000;
        log.info("runAllPipelines() | full pipeline cascade complete in {}s", elapsed);
        log.debug("runAllPipelines() | return=void");
    }

    private void runStep(String name, Runnable step) {
        log.info("runAllPipelines() | >>> {}", name);
        try {
            step.run();
            log.info("runAllPipelines() | <<< {} complete", name);
        } catch (Exception e) {
            log.warn("runAllPipelines() | <<< {} FAILED — continuing: {}", name, e.getMessage());
        }
    }

    private int countNonNull(Object... objects) {
        int count = 0;
        for (Object obj : objects) {
            if (obj != null) {
                count++;
            }
        }
        return count;
    }
}
