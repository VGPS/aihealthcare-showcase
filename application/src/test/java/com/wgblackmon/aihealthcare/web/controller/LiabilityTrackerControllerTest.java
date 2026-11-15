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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc slice tests for {@link LiabilityTrackerController}.
 *
 * <p>All port calls are mocked — no real persistence occurs.
 * Tests verify routing, model population, flash messages, and POST→redirect behaviour.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-16
 * @updated 2026-11-16
 */
@Import(SecurityConfig.class)
@WithMockUser(username = "admin@test.com", roles = "ADMIN")
@WebMvcTest(LiabilityTrackerController.class)
class LiabilityTrackerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private LiabilityIncidentPort port;

    private LiabilityIncident sampleIncident() {
        Instant now = Instant.now();
        return new LiabilityIncident(
                "test-id-001",
                IncidentType.LAWSUIT,
                HarmType.DELAYED_CARE,
                IncidentStatus.ACTIVE,
                "Sample incident",
                "Plaintiff", "OpenAI", "Product liability",
                "USDC CA", "24-CV-999",
                null, null, null,
                "https://example.com", "Notes here",
                now, now
        );
    }

    @Test
    void get_list_returns200AndModelAttributes() throws Exception {
        when(port.findAll()).thenReturn(List.of(sampleIncident()));
        when(port.findByStatus(any())).thenReturn(List.of());
        when(port.findRelatedArticles(anyString(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/admin/liability"))
               .andExpect(status().isOk())
               .andExpect(view().name("liability-admin"))
               .andExpect(model().attributeExists("incidents"))
               .andExpect(model().attributeExists("incidentTypes"))
               .andExpect(model().attributeExists("harmTypes"))
               .andExpect(model().attributeExists("statuses"))
               .andExpect(model().attributeExists("relatedByIncident"));
    }

    @Test
    void post_add_redirectsToList() throws Exception {
        mockMvc.perform(post("/admin/liability/add").with(csrf())
                .param("title", "New incident")
                .param("incidentType", "LAWSUIT")
                .param("harmType", "MISDIAGNOSIS")
                .param("status", "FILED")
                .param("defendant", "Some Vendor"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/liability"))
               .andExpect(flash().attributeExists("successMsg"));

        verify(port).save(any(LiabilityIncident.class));
    }

    @Test
    void post_add_withBlankTitle_propagatesErrorFlash() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("title must not be blank"))
                .when(port).save(any());

        mockMvc.perform(post("/admin/liability/add").with(csrf())
                .param("title", "")
                .param("incidentType", "LAWSUIT")
                .param("harmType", "MISDIAGNOSIS")
                .param("status", "FILED")
                .param("defendant", "Corp"))
               .andExpect(status().is3xxRedirection())
               .andExpect(flash().attributeExists("errorMsg"));
    }

    @Test
    void get_editForm_returns200WhenFound() throws Exception {
        when(port.findById("test-id-001")).thenReturn(sampleIncident());

        mockMvc.perform(get("/admin/liability/test-id-001/edit"))
               .andExpect(status().isOk())
               .andExpect(view().name("liability-edit"))
               .andExpect(model().attributeExists("incident"));
    }

    @Test
    void get_editForm_redirectsWhenNotFound() throws Exception {
        when(port.findById("missing")).thenReturn(null);

        mockMvc.perform(get("/admin/liability/missing/edit"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/liability"));
    }

    @Test
    void post_delete_redirectsToList() throws Exception {
        mockMvc.perform(post("/admin/liability/test-id-001/delete").with(csrf()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/liability"))
               .andExpect(flash().attributeExists("successMsg"));

        verify(port).deleteById("test-id-001");
    }

    @Test
    void post_approve_redirectsAndChangesStatusToFiled() throws Exception {
        LiabilityIncident pending = new LiabilityIncident(
                "pending-001", IncidentType.LAWSUIT, HarmType.DELAYED_CARE,
                IncidentStatus.PENDING_REVIEW, "Proposed incident",
                null, "VendorCo", null, null, null,
                null, null, null, "https://example.com", null,
                Instant.now(), Instant.now());
        when(port.findById("pending-001")).thenReturn(pending);

        mockMvc.perform(post("/admin/liability/pending-001/approve").with(csrf()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/liability"))
               .andExpect(flash().attributeExists("successMsg"));

        verify(port).save(any(LiabilityIncident.class));
    }

    @Test
    void post_reject_redirectsAndDeletesProposal() throws Exception {
        LiabilityIncident pending = new LiabilityIncident(
                "pending-002", IncidentType.FDA_FLAG, HarmType.AUTONOMOUS_DEVICE,
                IncidentStatus.PENDING_REVIEW, "Proposed FDA flag",
                null, "MedDevCo", null, null, null,
                null, null, null, null, null,
                Instant.now(), Instant.now());
        when(port.findById("pending-002")).thenReturn(pending);

        mockMvc.perform(post("/admin/liability/pending-002/reject").with(csrf()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/liability"))
               .andExpect(flash().attributeExists("successMsg"));

        verify(port).deleteById("pending-002");
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void get_list_nonAdminUser_returns403() throws Exception {
        mockMvc.perform(get("/admin/liability"))
               .andExpect(status().isForbidden());
    }
}
