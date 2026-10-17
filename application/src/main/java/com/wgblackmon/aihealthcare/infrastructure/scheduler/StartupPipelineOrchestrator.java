package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.marketanalysis.port.ProduceMarketDigestUseCase;
import com.wgblackmon.aihealthcare.domain.model.DealSignal;
import com.wgblackmon.aihealthcare.domain.model.PipelineErrorType;
import com.wgblackmon.aihealthcare.domain.model.PipelineRunEvent;
import com.wgblackmon.aihealthcare.domain.model.PipelineStepStatus;
import com.wgblackmon.aihealthcare.domain.model.WebhookEventType;
import com.wgblackmon.aihealthcare.domain.port.inbound.AnalyzeCompanySentimentUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.AnalyzeFrameworksUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectDealSignalsUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.MapCompanyRelationshipsUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectLegalTrendsUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.TrackFrontierClaimsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectTrendsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.PipelinePausePort;
import com.wgblackmon.aihealthcare.domain.service.OutreachSyncService;
import com.wgblackmon.aihealthcare.domain.port.outbound.PipelineRunEventPort;
import com.wgblackmon.aihealthcare.infrastructure.scheduler.EmbeddingScheduler;
import com.wgblackmon.aihealthcare.infrastructure.delivery.WebhookDispatcher;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.web.WebMonitoringScheduler;
import com.wgblackmon.aihealthcare.infrastructure.research.ResearchHarvestScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

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
 *   <li>Market digest generation</li>
 * </ol>
 *
 * <p>Safe-stop: before each step, checks the {@link PipelinePausePort} kill switch
 * (key {@code "pipeline-cascade"}, toggled from the Pipeline Management admin page,
 * shared with the standalone deal-detection cron). When paused, every remaining step
 * is skipped and recorded as {@code SKIPPED} rather than run — an admin can halt a
 * misbehaving cascade mid-run without a redeploy. There is no rollback/undo of steps
 * that already completed: every step here is an idempotent harvest, classification,
 * or append (never a transactional write with a meaningful compensating action), so
 * halting before the next step is the correct unit of control, not unwinding prior
 * ones.
 *
 * @author  Bill Blackmon
 * @version 1.3
 * @since   2026-08-03
 * @updated 2026-10-18
 */
@Slf4j
@Component
public class StartupPipelineOrchestrator {

    static final String PIPELINE_CASCADE_PAUSE_KEY = "pipeline-cascade";

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
    private final PipelineRunEventPort pipelineRunEventPort;
    private final WebhookDispatcher webhookDispatcher;
    private final DetectDealSignalsUseCase detectDealSignalsUseCase;
    private final MapCompanyRelationshipsUseCase mapRelationshipsUseCase;
    private final ProduceMarketDigestUseCase marketDigestService;
    private final TrackFrontierClaimsUseCase trackFrontierClaimsUseCase;
    private final ArticleIngestionPort articleIngestionPort;
    private final OutreachSyncService outreachSyncService;
    private final PipelinePausePort pausePort;

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
            @Autowired(required = false) ResearchHarvestScheduler researchHarvestScheduler,
            @Autowired(required = false) PipelineRunEventPort pipelineRunEventPort,
            @Autowired(required = false) WebhookDispatcher webhookDispatcher,
            @Autowired(required = false) DetectDealSignalsUseCase detectDealSignalsUseCase,
            @Autowired(required = false) MapCompanyRelationshipsUseCase mapRelationshipsUseCase,
            @Autowired(required = false) ProduceMarketDigestUseCase marketDigestService,
            @Autowired(required = false) TrackFrontierClaimsUseCase trackFrontierClaimsUseCase,
            @Autowired(required = false) ArticleIngestionPort articleIngestionPort,
            @Autowired(required = false) OutreachSyncService outreachSyncService,
            @Autowired(required = false) PipelinePausePort pausePort) {
        log.debug("StartupPipelineOrchestrator() | initializing with {} available pipelines",
                countNonNull(webMonitoringScheduler, regulatoryHarvestScheduler,
                        clinicalTrialHarvestScheduler, embeddingScheduler,
                        analyzeFrameworksUseCase, companyDiscoveryScheduler,
                        sentimentUseCase, detectTrendsUseCase,
                        detectLegalTrendsUseCase, researchHarvestScheduler,
                        pipelineRunEventPort, marketDigestService));
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
        this.pipelineRunEventPort = pipelineRunEventPort;
        this.webhookDispatcher = webhookDispatcher;
        this.detectDealSignalsUseCase = detectDealSignalsUseCase;
        this.mapRelationshipsUseCase = mapRelationshipsUseCase;
        this.marketDigestService = marketDigestService;
        this.trackFrontierClaimsUseCase = trackFrontierClaimsUseCase;
        this.articleIngestionPort = articleIngestionPort;
        this.outreachSyncService = outreachSyncService;
        this.pausePort = pausePort;
    }

    /**
     * Standalone deal signal detection cron — runs every 6 hours independently
     * of the full pipeline cascade. Ensures industry-tier articles harvested
     * every 4 hours are checked for deal signals without waiting for the
     * daily 04:00 UTC full pipeline run.
     */
    @Scheduled(cron = "${aihealthcare.deals.schedule}", zone = "UTC")
    public void runDealDetectionCron() {
        log.info("runDealDetectionCron() | starting standalone deal signal detection");
        if (detectDealSignalsUseCase == null) {
            log.debug("runDealDetectionCron() | detectDealSignalsUseCase not available — skipping");
            log.debug("runDealDetectionCron() | return=void");
            return;
        }
        runStep("Deal signal detection (standalone)", () -> {
            java.util.List<DealSignal> signals = detectDealSignalsUseCase.detectSignals();
            log.info("runDealDetectionCron() | {} new signals detected", signals.size());
            if (webhookDispatcher != null && !signals.isEmpty()) {
                webhookDispatcher.dispatch(
                        WebhookEventType.WATCHLIST_MATCH,
                        signals.size() + " Deal Signal" + (signals.size() == 1 ? "" : "s") + " Detected",
                        "Detected " + signals.size() + " new deal signal" + (signals.size() == 1 ? "" : "s") + " in recent articles.",
                        "/dashboard/deals"
                );
            }
        });
        log.debug("runDealDetectionCron() | return=void");
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

        runStep("Frontier claim detection", () -> {
            if (trackFrontierClaimsUseCase != null && articleIngestionPort != null) {
                java.util.List<com.wgblackmon.aihealthcare.domain.model.NewsArticle> recentArticles =
                        articleIngestionPort.fetchRecentArticles(1);
                java.util.List<com.wgblackmon.aihealthcare.domain.model.FrontierClaim> claims =
                        trackFrontierClaimsUseCase.detectClaims(recentArticles);
                log.info("runAllPipelines() | frontier claims detected={}", claims.size());
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

        runStep("Deal signal detection", () -> {
            if (detectDealSignalsUseCase != null) {
                java.util.List<DealSignal> signals = detectDealSignalsUseCase.detectSignals();
                if (webhookDispatcher != null && !signals.isEmpty()) {
                    webhookDispatcher.dispatch(
                            WebhookEventType.WATCHLIST_MATCH,
                            signals.size() + " Deal Signal" + (signals.size() == 1 ? "" : "s") + " Detected",
                            "Detected " + signals.size() + " new deal signal" + (signals.size() == 1 ? "" : "s") + " in recent articles.",
                            "/dashboard/deals"
                    );
                }
            }
        });

        runStep("Company relationships", () -> {
            if (mapRelationshipsUseCase != null) {
                mapRelationshipsUseCase.detectRelationships();
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

        runStep("Market digest", () -> {
            if (marketDigestService != null) {
                marketDigestService.generateDailyDigest(java.time.LocalDate.now());
            }
        });

        runStep("Outreach sync", () -> {
            if (outreachSyncService != null) {
                int created = outreachSyncService.syncOutreachRows();
                log.info("runAllPipelines() | outreach sync created {} new rows", created);
            }
        });

        long elapsed = (System.currentTimeMillis() - start) / 1000;
        log.info("runAllPipelines() | full pipeline cascade complete in {}s", elapsed);

        if (webhookDispatcher != null) {
            try {
                webhookDispatcher.dispatch(
                        WebhookEventType.PIPELINE_COMPLETE,
                        "Pipeline Run Complete",
                        "All 16 data pipelines completed in " + elapsed + " seconds.",
                        "/admin/pipeline"
                );
            } catch (Exception e) {
                log.warn("runAllPipelines() | webhook dispatch failed: {}", e.getMessage());
            }
        }

        log.debug("runAllPipelines() | return=void");
    }

    private void runStep(String name, Runnable step) {
        if (pausePort != null && pausePort.isPaused(PIPELINE_CASCADE_PAUSE_KEY)) {
            log.warn("runAllPipelines() | >>> {} SKIPPED — pipeline cascade paused by admin kill switch", name);
            Instant now = Instant.now();
            persistEvent(name, PipelineStepStatus.SKIPPED, now, now, null, null, null, null);
            return;
        }
        log.info("runAllPipelines() | >>> {}", name);
        Instant startedAt = Instant.now();
        try {
            step.run();
            Instant completedAt = Instant.now();
            log.info("runAllPipelines() | <<< {} complete", name);
            persistEvent(name, PipelineStepStatus.SUCCESS, startedAt, completedAt,
                    null, null, null, null);
        } catch (Exception e) {
            Instant completedAt = Instant.now();
            PipelineErrorType errorType = classifyError(e);
            String errorProvider = detectProvider(e);
            String errorDetail = extractStackTrace(e);
            log.warn("runAllPipelines() | <<< {} FAILED [{}{}] — continuing: {}",
                    name, errorType,
                    errorProvider != null ? "/" + errorProvider : "",
                    e.getMessage());
            persistEvent(name, PipelineStepStatus.FAILED, startedAt, completedAt,
                    e.getMessage(), errorType, errorProvider, errorDetail);
        }
    }

    private void persistEvent(String stepName, PipelineStepStatus status,
                              Instant startedAt, Instant completedAt,
                              String errorMessage, PipelineErrorType errorType,
                              String errorProvider, String errorDetail) {
        if (pipelineRunEventPort == null) {
            return;
        }
        try {
            String pipelineId = toPipelineId(stepName);
            long durationMs = Duration.between(startedAt, completedAt).toMillis();
            PipelineRunEvent event = new PipelineRunEvent(
                    null, pipelineId, stepName, status,
                    startedAt, completedAt, durationMs,
                    errorMessage, 0, "ORCHESTRATOR",
                    errorType, errorProvider, errorDetail);
            pipelineRunEventPort.save(event);
        } catch (Exception e) {
            log.warn("persistEvent() | failed to persist pipeline event for step={}: {}",
                    stepName, e.getMessage());
        }
    }

    /**
     * Classifies an exception into a {@link PipelineErrorType}.
     *
     * @see com.wgblackmon.aihealthcare.domain.service.PipelineErrorClassifier
     */
    private PipelineErrorType classifyError(Exception e) {
        return com.wgblackmon.aihealthcare.domain.service.PipelineErrorClassifier.classify(e);
    }

    /**
     * Detects which LLM provider is responsible for the failure, or null if unrelated.
     *
     * @see com.wgblackmon.aihealthcare.domain.service.PipelineErrorClassifier
     */
    private String detectProvider(Exception e) {
        return com.wgblackmon.aihealthcare.domain.service.PipelineErrorClassifier.detectProvider(e);
    }

    /** Extracts the first 500 characters of a stack trace for display in the UI. */
    private String extractStackTrace(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        String full = sw.toString();
        if (full.length() <= 500) {
            return full;
        }
        return full.substring(0, 500) + "…";
    }

    private String toPipelineId(String stepName) {
        switch (stepName) {
            case "Competitor pages":
                return "competitor";
            case "HuggingFace models":
                return "huggingface";
            case "Regulatory events":
                return "regulatory";
            case "Clinical trials":
                return "clinical-trials";
            case "Vector embeddings":
                return "embedding";
            case "Framework analysis":
                return "framework-analysis";
            case "Frontier claim detection":
                return "frontier-claims";
            case "Company discovery":
                return "company-discovery";
            case "Sentiment analysis":
                return "sentiment-analysis";
            case "Deal signal detection":
                return "deal-signals";
            case "Company relationships":
                return "company-relationships";
            case "Trend detection":
                return "trend-detection";
            case "Legal trend detection":
                return "legal-trends";
            case "Research harvest":
                return "research-harvest";
            case "Market digest":
                return "market-digest";
            case "Outreach sync":
                return "outreach-sync";
            default:
                return stepName.toLowerCase().replace(' ', '-');
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
