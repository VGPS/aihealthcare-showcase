package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.RegulatoryBody;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEventType;
import com.wgblackmon.aihealthcare.domain.port.inbound.MonitorRegulatoryEventsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link RegulatoryController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
@WebMvcTest(RegulatoryController.class)
class RegulatoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MonitorRegulatoryEventsUseCase regulatoryUseCase;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @Test
    @WithMockUser
    void regulatoryPage_rendersWithEvents() throws Exception {
        RegulatoryEvent event = event("e1", RegulatoryEventType.FDA_510K_CLEARANCE, "K241234");
        when(regulatoryUseCase.getRecentEvents(5)).thenReturn(List.of(event));

        mockMvc.perform(get("/dashboard/regulatory"))
                .andExpect(status().isOk())
                .andExpect(view().name("regulatory"))
                .andExpect(model().attribute("eventCount", 1))
                .andExpect(model().attribute("fda510kCount", 1));
    }

    @Test
    @WithMockUser
    void regulatoryPage_rendersEmptyState() throws Exception {
        when(regulatoryUseCase.getRecentEvents(5)).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/regulatory"))
                .andExpect(status().isOk())
                .andExpect(view().name("regulatory"))
                .andExpect(model().attribute("eventCount", 0));
    }

    @Test
    @WithMockUser
    void regulatoryPage_fdaFilterCallsGetEventsByBody() throws Exception {
        when(regulatoryUseCase.getEventsByBody(RegulatoryBody.FDA, 5)).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/regulatory").param("filter", "fda"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filter", "fda"));

        verify(regulatoryUseCase).getEventsByBody(RegulatoryBody.FDA, 5);
    }

    @Test
    @WithMockUser
    void regulatoryPage_cmsFilterCallsGetEventsByBody() throws Exception {
        when(regulatoryUseCase.getEventsByBody(RegulatoryBody.CMS, 5)).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/regulatory").param("filter", "cms"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filter", "cms"));

        verify(regulatoryUseCase).getEventsByBody(RegulatoryBody.CMS, 5);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void regulatoryPage_adminGetsFullAccess() throws Exception {
        when(regulatoryUseCase.getRecentEvents(50)).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/regulatory"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("fullAccess", true));
    }

    @Test
    @WithMockUser
    void regulatoryPage_freeUserLimitedAccess() throws Exception {
        when(regulatoryUseCase.getRecentEvents(5)).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/regulatory"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("fullAccess", false));
    }

    @Test
    void regulatoryPage_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/dashboard/regulatory"))
                .andExpect(status().isUnauthorized());
    }

    // --- Helper ---

    private RegulatoryEvent event(String id, RegulatoryEventType type, String refNumber) {
        return new RegulatoryEvent(id, type, RegulatoryBody.FDA,
                "510(k) Clearance: AI Device", "Summary text",
                refNumber, "Applicant Inc", "AI Scanner",
                "https://fda.gov/" + id, null,
                Instant.now(), Instant.now(), List.of("AI", "radiology"),
                null, null, null, null);
    }
}
