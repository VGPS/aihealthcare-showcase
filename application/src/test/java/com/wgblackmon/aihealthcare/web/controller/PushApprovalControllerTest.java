package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.PushApprovalRequest;
import com.wgblackmon.aihealthcare.domain.model.PushApprovalStatus;
import com.wgblackmon.aihealthcare.domain.port.inbound.ReviewPushApprovalsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.PushApprovalGatePort;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for {@link PushApprovalController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
@Import(SecurityConfig.class)
@WebMvcTest(PushApprovalController.class)
class PushApprovalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private ReviewPushApprovalsUseCase reviewUseCase;

    @MockitoBean
    private PushApprovalGatePort gatePort;

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");

    @Test
    @WithMockUser(roles = "ADMIN")
    void listPending_returnsJsonList() throws Exception {
        PushApprovalRequest pending = new PushApprovalRequest("job-1", "sched-1",
                "alice@test.com", NOW, PushApprovalStatus.PENDING, null, null);
        when(reviewUseCase.listPending()).thenReturn(List.of(pending));

        mockMvc.perform(get("/admin/push-approvals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobId").value("job-1"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void listPending_returns403ForNonAdmin() throws Exception {
        mockMvc.perform(get("/admin/push-approvals"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void approve_callsUseCaseWithAdminUsername() throws Exception {
        mockMvc.perform(post("/admin/push-approvals/job-1/approve").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        verify(reviewUseCase).approve("job-1", "admin@test.com");
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void approve_useCaseThrows_returnsBadRequest() throws Exception {
        doThrow(new IllegalStateException("already decided"))
                .when(reviewUseCase).approve(anyString(), anyString());

        mockMvc.perform(post("/admin/push-approvals/job-1/approve").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void reject_callsUseCaseWithAdminUsername() throws Exception {
        mockMvc.perform(post("/admin/push-approvals/job-1/reject").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        verify(reviewUseCase).reject("job-1", "admin@test.com");
    }

    @Test
    @WithMockUser(roles = "USER")
    void approve_returns403ForNonAdmin() throws Exception {
        mockMvc.perform(post("/admin/push-approvals/job-1/approve").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setGate_callsGatePort() throws Exception {
        mockMvc.perform(post("/admin/push-approvals/gate").with(csrf())
                        .param("scheduleId", "sched-1")
                        .param("required", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleId").value("sched-1"))
                .andExpect(jsonPath("$.required").value(true));

        verify(gatePort).setRequired("sched-1", true);
    }

    @Test
    @WithMockUser(roles = "USER")
    void setGate_returns403ForNonAdmin() throws Exception {
        mockMvc.perform(post("/admin/push-approvals/gate").with(csrf())
                        .param("scheduleId", "sched-1")
                        .param("required", "true"))
                .andExpect(status().isForbidden());
    }
}
