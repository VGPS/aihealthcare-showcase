package com.wgblackmon.aihealthcare.infrastructure.ingestion.legal;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleStoragePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.RegulatoryEventPort;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.regulatory.RegulatoryHarvestProperties;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.regulatory.RegulatorySourceHarvester;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates a one-time historical backfill of legal, policy, and
 * regulatory data from multiple public APIs.
 *
 * <p>Aggregates results from four data sources:
 * <ol>
 *   <li>{@link CourtListenerHarvester} — federal/state court opinions</li>
 *   <li>{@link PubMedLegalHarvester} — legal/policy research articles</li>
 *   <li>Existing {@link RegulatorySourceHarvester} implementations — FDA 510(k),
 *       De Novo, CMS rules with an expanded lookback window</li>
 * </ol>
 *
 * <p>Each source is isolated — failures in one source do not block others.
 * Deduplication is handled by the storage adapters ({@code existsByUrl}
 * for articles, {@code existsByReferenceNumber}/{@code existsBySourceUrl}
 * for regulatory events), so running the backfill multiple times is safe.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-07-30
 */
@Slf4j
@Component
public class LegalBackfillService {

    private final CourtListenerHarvester courtListenerHarvester;
    private final PubMedLegalHarvester pubMedLegalHarvester;
    private final ArticleStoragePort articleStoragePort;
    private final RegulatoryEventPort regulatoryEventPort;
    private final RegulatoryHarvestProperties regulatoryProperties;
    private final List<RegulatorySourceHarvester> regulatoryHarvesters;

    public LegalBackfillService(CourtListenerHarvester courtListenerHarvester,
                                 PubMedLegalHarvester pubMedLegalHarvester,
                                 ArticleStoragePort articleStoragePort,
                                 RegulatoryEventPort regulatoryEventPort,
                                 RegulatoryHarvestProperties regulatoryProperties,
                                 List<RegulatorySourceHarvester> regulatoryHarvesters) {
        log.debug("LegalBackfillService() | courtListenerHarvester={}, pubMedLegalHarvester={}, " +
                  "articleStoragePort={}, regulatoryEventPort={}, regulatoryProperties={}, " +
                  "regulatoryHarvesters={}",
                  courtListenerHarvester, pubMedLegalHarvester, articleStoragePort,
                  regulatoryEventPort, regulatoryProperties, regulatoryHarvesters.size());
        this.courtListenerHarvester = courtListenerHarvester;
        this.pubMedLegalHarvester = pubMedLegalHarvester;
        this.articleStoragePort = articleStoragePort;
        this.regulatoryEventPort = regulatoryEventPort;
        this.regulatoryProperties = regulatoryProperties;
        this.regulatoryHarvesters = regulatoryHarvesters;
    }

    /**
     * Runs the full legal historical backfill across all sources.
     *
     * @param lookbackDays how many days back to harvest (e.g. 1095 for 3 years)
     * @return result summary with per-source counts and any errors
     */
    public BackfillResult runBackfill(int lookbackDays) {
        log.debug("runBackfill() | lookbackDays={}", lookbackDays);

        int courtListenerCount = 0;
        int pubmedCount = 0;
        int regulatoryCount = 0;
        List<String> errors = new ArrayList<>();

        // Source 1: CourtListener court opinions → NewsArticle
        try {
            List<NewsArticle> opinions = courtListenerHarvester.harvest(lookbackDays);
            courtListenerCount = opinions.size();
            if (!opinions.isEmpty()) {
                articleStoragePort.save(opinions);
            }
            log.info("runBackfill() | CourtListener: {} opinions harvested", courtListenerCount);
        } catch (Exception e) {
            String error = "CourtListener failed: " + e.getMessage();
            log.warn("runBackfill() | {}", error);
            errors.add(error);
        }

        // Source 2: PubMed legal/policy articles → NewsArticle
        try {
            List<NewsArticle> articles = pubMedLegalHarvester.harvest(lookbackDays);
            pubmedCount = articles.size();
            if (!articles.isEmpty()) {
                articleStoragePort.save(articles);
            }
            log.info("runBackfill() | PubMed: {} articles harvested", pubmedCount);
        } catch (Exception e) {
            String error = "PubMed failed: " + e.getMessage();
            log.warn("runBackfill() | {}", error);
            errors.add(error);
        }

        // Source 3: Regulatory harvesters with expanded lookback → RegulatoryEvent
        List<String> aiKeywords = regulatoryProperties.getAiKeywords();
        for (RegulatorySourceHarvester harvester : regulatoryHarvesters) {
            try {
                List<RegulatoryEvent> events = harvester.harvest(lookbackDays, aiKeywords);
                log.info("runBackfill() | {} returned {} events", harvester.sourceName(), events.size());

                // Dedup before saving (same pattern as RegulatoryHarvestScheduler)
                List<RegulatoryEvent> newEvents = new ArrayList<>();
                for (RegulatoryEvent event : events) {
                    boolean duplicate = false;
                    if (event.referenceNumber() != null && !event.referenceNumber().isBlank()) {
                        duplicate = regulatoryEventPort.existsByReferenceNumber(event.referenceNumber());
                    }
                    if (!duplicate) {
                        duplicate = regulatoryEventPort.existsBySourceUrl(event.sourceUrl());
                    }
                    if (!duplicate) {
                        newEvents.add(event);
                    }
                }

                if (!newEvents.isEmpty()) {
                    regulatoryEventPort.saveAll(newEvents);
                    regulatoryCount += newEvents.size();
                }
            } catch (Exception e) {
                String error = harvester.sourceName() + " failed: " + e.getMessage();
                log.warn("runBackfill() | {}", error);
                errors.add(error);
            }
        }

        BackfillResult result = new BackfillResult(courtListenerCount, pubmedCount, regulatoryCount, errors);
        log.info("runBackfill() | backfill complete: courtListener={}, pubmed={}, regulatory={}, errors={}",
                 courtListenerCount, pubmedCount, regulatoryCount, errors.size());
        log.debug("runBackfill() | return={}", result);
        return result;
    }

    /**
     * Result summary from a backfill run, reporting per-source counts
     * and any errors encountered.
     */
    public record BackfillResult(int courtListenerCount, int pubmedCount,
                                  int regulatoryCount, List<String> errors) {}
}
