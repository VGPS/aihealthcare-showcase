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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link ResearchRunController}.
 *
 * <p>Verifies HTTP status codes, response body structure, and 404 behaviour
 * for the research run history endpoints.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-06
 * @updated 2026-05-06
 */
@WebMvcTest(ResearchRunController.class)
class ResearchRunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResearchRunPort researchRunPort;

    @Test
    void listRuns_returnsEmptyList() throws Exception {
        when(researchRunPort.findAll()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/research/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listRuns_returnsMappedRuns() throws Exception {
        ResearchRun run = new ResearchRun(
                "run-abc", "AI diagnostics", "STAGED_RESEARCH", 5, Instant.parse("2026-05-06T10:00:00Z"));
        when(researchRunPort.findAll()).thenReturn(List.of(run));

        mockMvc.perform(get("/api/v1/research/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].runId").value("run-abc"))
                .andExpect(jsonPath("$[0].query").value("AI diagnostics"))
                .andExpect(jsonPath("$[0].mode").value("STAGED_RESEARCH"))
                .andExpect(jsonPath("$[0].citationCount").value(5));
    }

    @Test
    void getRun_existingId_returnsRun() throws Exception {
        ResearchRun run = new ResearchRun(
                "run-xyz", "AI surgery", "LEGACY_GOOGLE", 3, Instant.parse("2026-05-06T11:00:00Z"));
        when(researchRunPort.findByRunId("run-xyz")).thenReturn(Optional.of(run));

        mockMvc.perform(get("/api/v1/research/runs/run-xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("run-xyz"))
                .andExpect(jsonPath("$.query").value("AI surgery"))
                .andExpect(jsonPath("$.mode").value("LEGACY_GOOGLE"))
                .andExpect(jsonPath("$.citationCount").value(3));
    }

    @Test
    void getRun_unknownId_returns404() throws Exception {
        when(researchRunPort.findByRunId("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/research/runs/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listRuns_multipleRuns_allIncluded() throws Exception {
        List<ResearchRun> runs = List.of(
                new ResearchRun("r1", "query one", "STAGED_RESEARCH", 10, Instant.now()),
                new ResearchRun("r2", "query two", "LEGACY_GOOGLE", 2, Instant.now()),
                new ResearchRun("r3", "query three", "STAGED_RESEARCH", 7, Instant.now()));
        when(researchRunPort.findAll()).thenReturn(runs);

        mockMvc.perform(get("/api/v1/research/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].runId").value("r1"))
                .andExpect(jsonPath("$[1].runId").value("r2"))
                .andExpect(jsonPath("$[2].runId").value("r3"));
    }
}
