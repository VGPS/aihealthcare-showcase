package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ResearchRun;
import com.wgblackmon.aihealthcare.domain.port.outbound.ResearchRunPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc slice tests for {@link ResearchDashboardController}.
 *
 * <p>Verifies that the Thymeleaf controller returns the correct view names,
 * populates model attributes, and handles 404 correctly for unknown run IDs.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-12
 * @updated 2026-05-12
 */
@WebMvcTest(ResearchDashboardController.class)
class ResearchDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResearchRunPort researchRunPort;

    @Test
    void listRuns_returnsCorrectView() throws Exception {
        when(researchRunPort.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/research/runs"))
                .andExpect(status().isOk())
                .andExpect(view().name("research-runs"));
    }

    @Test
    void listRuns_modelContainsRunsList() throws Exception {
        List<ResearchRun> runs = List.of(
                new ResearchRun("r1", "AI diagnostics", "COMBINED", 8,
                        Instant.parse("2026-05-10T06:00:00Z")));
        when(researchRunPort.findAll()).thenReturn(runs);

        mockMvc.perform(get("/research/runs"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("runs"))
                .andExpect(model().attribute("runs", runs))
                .andExpect(model().attributeExists("runTimestamps"));
    }

    @Test
    void runDetail_knownId_returnsDetailView() throws Exception {
        ResearchRun run = new ResearchRun(
                "run-abc", "AI in radiology", "STAGED_RESEARCH", 5,
                Instant.parse("2026-05-11T10:00:00Z"));
        when(researchRunPort.findByRunId("run-abc")).thenReturn(Optional.of(run));

        mockMvc.perform(get("/research/runs/run-abc"))
                .andExpect(status().isOk())
                .andExpect(view().name("research-run-detail"));
    }

    @Test
    void runDetail_knownId_modelContainsRunAndDisplayTime() throws Exception {
        ResearchRun run = new ResearchRun(
                "run-abc", "AI in radiology", "STAGED_RESEARCH", 5,
                Instant.parse("2026-05-11T10:00:00Z"));
        when(researchRunPort.findByRunId("run-abc")).thenReturn(Optional.of(run));

        mockMvc.perform(get("/research/runs/run-abc"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("run"))
                .andExpect(model().attributeExists("displayTime"));
    }

    @Test
    void runDetail_unknownId_returns404() throws Exception {
        when(researchRunPort.findByRunId("unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/research/runs/unknown"))
                .andExpect(status().isNotFound());
    }
}
