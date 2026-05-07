package com.wgblackmon.aihealthcare.infrastructure.research;

import com.wgblackmon.aihealthcare.domain.model.ResearchMode;
import com.wgblackmon.aihealthcare.domain.model.ResearchRequest;
import com.wgblackmon.aihealthcare.domain.port.inbound.ConductResearchUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled driver that proactively harvests research results for all configured
 * topics using the {@code COMBINED} pipeline (Perplexity + DB merge).
 *
 * <p>Runs on the cron schedule defined by
 * {@code aihealthcare.research.harvest.cron} (default: 06:00 UTC daily).
 * For each topic in {@code aihealthcare.research.harvest.topics}, the full
 * {@code COMBINED} research pipeline is executed:
 * <ol>
 *   <li>AI query planning via {@link com.wgblackmon.aihealthcare.domain.service.ResearchPlanningService}</li>
 *   <li>Perplexity Sonar retrieval for current, AI-curated web sources</li>
 *   <li>Legacy DB retrieval for historical depth</li>
 *   <li>Merge + URL deduplication + AI synthesis</li>
 *   <li>New Perplexity articles persisted to {@code news_articles}</li>
 *   <li>Articles exported to the NotebookLM on-disk corpus</li>
 *   <li>A {@code ResearchRun} audit record written to {@code research_runs}</li>
 * </ol>
 *
 * <p>Topics are configured as a plain YAML list — add, remove, or reorder in
 * {@code application.yml} without touching any Java code:
 * <pre>
 * aihealthcare:
 *   research:
 *     harvest:
 *       topics:
 *         - "AI Healthcare Software Development"
 *         - "Healthcare Outsourcing"
 * </pre>
 *
 * <p>Per-topic failures are caught, logged, and swallowed so that a single
 * failing topic does not abort the remainder of the harvest run.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-07
 * @updated 2026-05-07
 */
@Slf4j
@Component
public class ResearchHarvestScheduler {

    private final ConductResearchUseCase    conductResearchUseCase;
    private final ResearchHarvestProperties harvestProperties;

    /**
     * Constructs the scheduler with its use-case and configuration dependencies.
     *
     * @param conductResearchUseCase Use case driving the COMBINED research pipeline.
     * @param harvestProperties      Externalized config: topics, cron, maxSources.
     */
    public ResearchHarvestScheduler(ConductResearchUseCase conductResearchUseCase,
                                    ResearchHarvestProperties harvestProperties) {
        log.debug("ResearchHarvestScheduler() | conductResearchUseCase={}, topics={}",
                  conductResearchUseCase.getClass().getSimpleName(),
                  harvestProperties.getTopics().size());
        this.conductResearchUseCase = conductResearchUseCase;
        this.harvestProperties      = harvestProperties;
        log.debug("ResearchHarvestScheduler() | return=void");
    }

    /**
     * Runs the COMBINED research pipeline for each configured topic.
     *
     * <p>Schedule is controlled by {@code aihealthcare.research.harvest.cron}
     * (default: {@code 0 0 6 * * *} = 06:00 UTC daily).
     *
     * <p>Per-topic exceptions are swallowed so a single failure does not abort
     * subsequent topics in the same run.
     */
    @Scheduled(cron = "${aihealthcare.research.harvest.cron:0 0 6 * * *}", zone = "UTC")
    public void harvestResearchTopics() {
        log.debug("harvestResearchTopics() | topicCount={}", harvestProperties.getTopics().size());

        if (harvestProperties.getTopics().isEmpty()) {
            log.info("harvestResearchTopics() | no topics configured — skipping");
            log.debug("harvestResearchTopics() | return=void");
            return;
        }

        int success = 0;
        int failed  = 0;

        for (String topic : harvestProperties.getTopics()) {
            log.info("harvestResearchTopics() | harvesting topic='{}'", topic);
            try {
                ResearchRequest request = new ResearchRequest(
                        topic,
                        ResearchMode.COMBINED,
                        null,
                        harvestProperties.getMaxSourcesPerTopic());
                conductResearchUseCase.conduct(request);
                success++;
                log.info("harvestResearchTopics() | topic='{}' complete", topic);
            } catch (Exception ex) {
                failed++;
                log.error("harvestResearchTopics() | topic='{}' failed — skipping: {}",
                          topic, ex.getMessage());
            }
        }

        log.info("harvestResearchTopics() | harvest run complete: {} succeeded, {} failed",
                 success, failed);
        log.debug("harvestResearchTopics() | return=void");
    }
}
