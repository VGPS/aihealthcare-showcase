package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.LawCategory;
import com.wgblackmon.aihealthcare.domain.model.LawStatus;
import com.wgblackmon.aihealthcare.domain.model.StateCode;
import com.wgblackmon.aihealthcare.domain.model.StateLaw;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageStateLawsUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST API controller for the State Health-AI Legislation Registry.
 *
 * <p>Exposes read-only endpoints for querying state laws, filtering by
 * state/category/status, searching, and retrieving upcoming effective
 * dates. Returns domain records directly (no DTOs needed for this
 * read-only slice).
 *
 * <p>All endpoints require authentication via the global security
 * filter chain ({@code /api/**} requires authentication).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/legislation")
public class StateLawRestController {

    private final ManageStateLawsUseCase legislationUseCase;

    public StateLawRestController(ManageStateLawsUseCase legislationUseCase) {
        log.debug("StateLawRestController() | legislationUseCase={}",
                  legislationUseCase.getClass().getSimpleName());
        this.legislationUseCase = legislationUseCase;
    }

    /**
     * Returns all state laws, optionally filtered by state, category, status, or search query.
     *
     * @param state    optional state code filter (e.g. "CA")
     * @param category optional category filter (e.g. "PAYER_UTILIZATION_REVIEW")
     * @param status   optional status filter (e.g. "ENACTED")
     * @param q        optional search query
     * @return list of matching state laws
     */
    @GetMapping("/state-laws")
    public List<StateLaw> getStateLaws(@RequestParam(required = false) String state,
                                       @RequestParam(required = false) String category,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String q) {
        log.debug("getStateLaws() | state={}, category={}, status={}, q={}", state, category, status, q);

        List<StateLaw> result;
        if (q != null && !q.isBlank()) {
            result = legislationUseCase.search(q);
        } else if (state != null && !state.isBlank()) {
            result = legislationUseCase.getByState(StateCode.valueOf(state));
        } else if (category != null && !category.isBlank()) {
            result = legislationUseCase.getByCategory(LawCategory.valueOf(category));
        } else if (status != null && !status.isBlank()) {
            result = legislationUseCase.getByStatus(LawStatus.valueOf(status));
        } else {
            result = legislationUseCase.getAll();
        }

        log.debug("getStateLaws() | return={} laws", result.size());
        return result;
    }

    /**
     * Returns a single state law by its slug id.
     *
     * @param id the law slug id (e.g. "ca-ab-3030")
     * @return the state law, or 404 if not found
     */
    @GetMapping("/state-laws/{id}")
    public ResponseEntity<StateLaw> getStateLawById(@PathVariable String id) {
        log.debug("getStateLawById() | id={}", id);

        Optional<StateLaw> found = legislationUseCase.getById(id);

        if (found.isEmpty()) {
            log.debug("getStateLawById() | return=404 (not found)");
            return ResponseEntity.notFound().build();
        }

        log.debug("getStateLawById() | return={}", found.get().id());
        return ResponseEntity.ok(found.get());
    }

    /**
     * Returns laws with upcoming effective dates within the specified number of days.
     *
     * @param days number of days to look ahead (default 90)
     * @return list of laws with upcoming effective dates
     */
    @GetMapping("/state-laws/upcoming")
    public List<StateLaw> getUpcomingLaws(@RequestParam(defaultValue = "90") int days) {
        log.debug("getUpcomingLaws() | days={}", days);

        List<StateLaw> result = legislationUseCase.getUpcoming(days);

        log.debug("getUpcomingLaws() | return={} laws", result.size());
        return result;
    }

    /**
     * Returns all laws grouped by state code.
     *
     * @return map of state code to list of laws
     */
    @GetMapping("/state-laws/by-state")
    public Map<String, List<StateLaw>> getLawsByState() {
        log.debug("getLawsByState()");

        List<StateLaw> allLaws = legislationUseCase.getAll();
        Map<String, List<StateLaw>> grouped = new HashMap<>();

        for (StateLaw law : allLaws) {
            String code = law.stateCode().name();
            grouped.computeIfAbsent(code, k -> new ArrayList<>()).add(law);
        }

        log.debug("getLawsByState() | return={} states", grouped.size());
        return grouped;
    }
}
