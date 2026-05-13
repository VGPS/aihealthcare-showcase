package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ResearchRun;
import com.wgblackmon.aihealthcare.domain.port.outbound.ResearchRunPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thymeleaf controller that exposes the research run history UI.
 *
 * <p>Serves two pages:
 * <ul>
 *   <li>{@code GET /research/runs} — lists all persisted {@link ResearchRun} records,
 *       newest first, in a table showing timestamp, truncated query, mode badge, and
 *       citation count.</li>
 *   <li>{@code GET /research/runs/{runId}} — detail page for a single run; returns
 *       HTTP 404 if the run ID is not found.</li>
 * </ul>
 *
 * <p>Reads directly from {@link ResearchRunPort} — no application service layer is
 * needed for these pure query operations that contain no business logic.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-12
 * @updated 2026-05-12
 */
@Slf4j
@Controller
@RequestMapping("/research/runs")
public class ResearchDashboardController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private static final int QUERY_TRUNCATE_LENGTH = 80;

    private final ResearchRunPort researchRunPort;

    /**
     * Constructs the controller with its outbound port dependency.
     *
     * @param researchRunPort Port for querying persisted research run records.
     */
    public ResearchDashboardController(ResearchRunPort researchRunPort) {
        log.debug("ResearchDashboardController() | researchRunPort={}",
                  researchRunPort.getClass().getSimpleName());
        this.researchRunPort = researchRunPort;
        log.debug("ResearchDashboardController() | return=void");
    }

    /**
     * Renders the research run history list page.
     *
     * <p>All persisted runs are fetched newest-first via {@link ResearchRunPort#findAll()}.
     * Query strings longer than {@value QUERY_TRUNCATE_LENGTH} characters are truncated
     * with an ellipsis for display.
     *
     * @param model Thymeleaf model populated with the run list
     * @return Thymeleaf view name {@code "research-runs"}
     */
    @GetMapping
    public String listRuns(Model model) {
        log.debug("listRuns() |");

        List<ResearchRun> runs = researchRunPort.findAll();
        model.addAttribute("runs", runs);

        Map<String, String> runTimestamps = new LinkedHashMap<>();
        for (ResearchRun run : runs) {
            String ts = run.researchedAt() != null
                    ? DISPLAY_FMT.format(run.researchedAt()) + " UTC"
                    : "—";
            runTimestamps.put(run.runId(), ts);
        }
        model.addAttribute("runTimestamps", runTimestamps);

        log.info("listRuns() | rendering {} research runs", runs.size());
        log.debug("listRuns() | return=research-runs");
        return "research-runs";
    }

    /**
     * Renders the detail page for a single research run.
     *
     * @param runId the UUID of the run to display (path variable)
     * @param model Thymeleaf model populated with the matched run and formatted timestamp
     * @return Thymeleaf view name {@code "research-run-detail"}
     * @throws ResponseStatusException HTTP 404 if no run with the given ID exists
     */
    @GetMapping("/{runId}")
    public String runDetail(@PathVariable String runId, Model model) {
        log.debug("runDetail() | runId={}", runId);

        Optional<ResearchRun> run = researchRunPort.findByRunId(runId);
        if (run.isEmpty()) {
            log.warn("runDetail() | run not found: runId={}", runId);
            log.debug("runDetail() | return=404");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Research run not found: " + runId);
        }

        String displayTime = run.get().researchedAt() != null
                ? DISPLAY_FMT.format(run.get().researchedAt()) + " UTC"
                : "—";

        model.addAttribute("run", run.get());
        model.addAttribute("displayTime", displayTime);

        log.debug("runDetail() | return=research-run-detail for runId={}", runId);
        return "research-run-detail";
    }
}
