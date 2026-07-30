package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.infrastructure.ingestion.legal.LegalBackfillService;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.legal.LegalBackfillService.BackfillResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller providing a manual trigger for the legal and regulatory
 * historical backfill pipeline.
 *
 * <p>Delegates to {@link LegalBackfillService#runBackfill(int)} to harvest
 * court opinions, PubMed legal/policy articles, and regulatory events
 * across an extended lookback window (default 1095 days / 3 years).
 *
 * <p>Endpoint is under {@code /monitoring/} which is {@code permitAll()} in
 * the security configuration — no authentication required. This is
 * intentional for internal operations endpoints.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-07-30
 */
@Slf4j
@RestController
public class LegalBackfillController {

    private static final int DEFAULT_LOOKBACK_DAYS = 1095;

    private final LegalBackfillService legalBackfillService;

    public LegalBackfillController(LegalBackfillService legalBackfillService) {
        log.debug("LegalBackfillController() | legalBackfillService={}", legalBackfillService);
        this.legalBackfillService = legalBackfillService;
    }

    /**
     * Manually triggers a legal and regulatory historical backfill.
     *
     * @param days lookback window in days (default 1095 = 3 years)
     * @return JSON response with per-source counts and any errors
     */
    @PostMapping("/monitoring/legal-backfill")
    public ResponseEntity<Map<String, Object>> triggerBackfill(
            @RequestParam(defaultValue = "1095") int days) {
        log.debug("triggerBackfill() | days={}", days);

        BackfillResult result = legalBackfillService.runBackfill(days);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "Backfill completed");
        response.put("lookbackDays", days);
        response.put("courtListenerCount", result.courtListenerCount());
        response.put("pubmedCount", result.pubmedCount());
        response.put("regulatoryCount", result.regulatoryCount());
        response.put("totalCount", result.courtListenerCount() + result.pubmedCount() + result.regulatoryCount());
        if (!result.errors().isEmpty()) {
            response.put("errors", result.errors());
        }

        log.info("triggerBackfill() | backfill completed: {}", response);
        log.debug("triggerBackfill() | return={}", response);
        return ResponseEntity.ok(response);
    }
}
