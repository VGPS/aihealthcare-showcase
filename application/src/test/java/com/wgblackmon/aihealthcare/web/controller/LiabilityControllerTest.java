package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.HarmType;
import com.wgblackmon.aihealthcare.domain.model.IncidentStatus;
import com.wgblackmon.aihealthcare.domain.model.IncidentType;
import com.wgblackmon.aihealthcare.domain.model.LiabilityIncident;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.LiabilityIncidentPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc slice tests for the public {@link LiabilityController}.
 *
 * <p>No authentication required — the route is permitAll(). Verifies that the
 * registry page renders correctly and that PENDING_REVIEW incidents are excluded
 * from the public view.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-11-15
 * @updated 2026-11-15
 */
@Import(SecurityConfig.class)
@WebMvcTest(LiabilityController.class)
class LiabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private LiabilityIncidentPort port;

    private LiabilityIncident incident(String id, IncidentStatus status) {
        Instant now = Instant.now();
        return new LiabilityIncident(id, IncidentType.LAWSUIT, HarmType.MISDIAGNOSIS, status,
                "Test incident " + id, null, "OpenAI", null, null, null,
                null, null, null, "https://example.com/" + id, null, now, now);
    }

    @Test
    void get_registry_returns200WithModelAttributes() throws Exception {
        when(port.findAll()).thenReturn(List.of(
                incident("pub-001", IncidentStatus.ACTIVE),
                incident("pub-002", IncidentStatus.FILED)));

        mockMvc.perform(get("/liability"))
               .andExpect(status().isOk())
               .andExpect(view().name("liability-public"))
               .andExpect(model().attributeExists("incidents"))
               .andExpect(model().attributeExists("totalCount"))
               .andExpect(model().attributeExists("incidentTypes"))
               .andExpect(model().attributeExists("harmTypes"))
               .andExpect(model().attributeExists("statuses"))
               .andExpect(model().attribute("sort", "dateDesc"));
    }

    @Test
    void get_registry_excludesPendingReviewFromPublicView() throws Exception {
        when(port.findAll()).thenReturn(List.of(
                incident("pub-001", IncidentStatus.ACTIVE),
                incident("pending-001", IncidentStatus.PENDING_REVIEW)));

        mockMvc.perform(get("/liability"))
               .andExpect(status().isOk())
               .andExpect(model().attribute("totalCount", 1));
    }

    @Test
    void get_registry_withTypeFilter_passesFilterToModel() throws Exception {
        when(port.findAll()).thenReturn(List.of(incident("pub-001", IncidentStatus.ACTIVE)));

        mockMvc.perform(get("/liability").param("type", "LAWSUIT"))
               .andExpect(status().isOk())
               .andExpect(model().attribute("activeType", "LAWSUIT"));
    }

    @Test
    void get_registry_noAuth_returns200() throws Exception {
        when(port.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/liability"))
               .andExpect(status().isOk());
    }
}
