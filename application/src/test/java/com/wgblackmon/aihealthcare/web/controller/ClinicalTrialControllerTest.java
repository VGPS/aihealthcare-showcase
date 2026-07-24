package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ClinicalTrial;
import com.wgblackmon.aihealthcare.domain.model.ClinicalTrialPhase;
import com.wgblackmon.aihealthcare.domain.model.ClinicalTrialStatus;
import com.wgblackmon.aihealthcare.domain.port.inbound.MonitorClinicalTrialsUseCase;
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
 * MockMvc tests for {@link ClinicalTrialController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-23
 * @updated 2026-07-23
 */
@WebMvcTest(ClinicalTrialController.class)
class ClinicalTrialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MonitorClinicalTrialsUseCase clinicalTrialsUseCase;

    @MockitoBean
    private SubscriberPort subscriberPort;

    @Test
    @WithMockUser
    void clinicalTrialsPage_rendersWithTrials() throws Exception {
        ClinicalTrial trial = trial("t1", "NCT001", ClinicalTrialStatus.RECRUITING, ClinicalTrialPhase.PHASE_2);
        when(clinicalTrialsUseCase.getRecentTrials(5)).thenReturn(List.of(trial));

        mockMvc.perform(get("/dashboard/clinical-trials"))
                .andExpect(status().isOk())
                .andExpect(view().name("clinical-trials"))
                .andExpect(model().attribute("trialCount", 1))
                .andExpect(model().attribute("recruitingCount", 1))
                .andExpect(model().attribute("phase2Count", 1));
    }

    @Test
    @WithMockUser
    void clinicalTrialsPage_rendersEmptyState() throws Exception {
        when(clinicalTrialsUseCase.getRecentTrials(5)).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/clinical-trials"))
                .andExpect(status().isOk())
                .andExpect(view().name("clinical-trials"))
                .andExpect(model().attribute("trialCount", 0));
    }

    @Test
    @WithMockUser
    void clinicalTrialsPage_filterByRecruiting() throws Exception {
        ClinicalTrial trial = trial("t1", "NCT001", ClinicalTrialStatus.RECRUITING, null);
        when(clinicalTrialsUseCase.getTrialsByStatus(ClinicalTrialStatus.RECRUITING, 5))
                .thenReturn(List.of(trial));

        mockMvc.perform(get("/dashboard/clinical-trials").param("filter", "recruiting"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("trialCount", 1))
                .andExpect(model().attribute("filter", "recruiting"));

        verify(clinicalTrialsUseCase).getTrialsByStatus(ClinicalTrialStatus.RECRUITING, 5);
    }

    @Test
    @WithMockUser
    void clinicalTrialsPage_filterByCompleted() throws Exception {
        ClinicalTrial trial = trial("t1", "NCT001", ClinicalTrialStatus.COMPLETED, null);
        when(clinicalTrialsUseCase.getTrialsByStatus(ClinicalTrialStatus.COMPLETED, 5))
                .thenReturn(List.of(trial));

        mockMvc.perform(get("/dashboard/clinical-trials").param("filter", "completed"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("completedCount", 1));

        verify(clinicalTrialsUseCase).getTrialsByStatus(ClinicalTrialStatus.COMPLETED, 5);
    }

    @Test
    @WithMockUser
    void clinicalTrialsPage_freeUserGetsLimitedResults() throws Exception {
        when(clinicalTrialsUseCase.getRecentTrials(5)).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/clinical-trials"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("fullAccess", false));

        // FREE user should only request 5 trials
        verify(clinicalTrialsUseCase).getRecentTrials(5);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void clinicalTrialsPage_adminGetsFullAccess() throws Exception {
        when(clinicalTrialsUseCase.getRecentTrials(50)).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/clinical-trials"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("fullAccess", true));

        // ADMIN should request full 50 trials
        verify(clinicalTrialsUseCase).getRecentTrials(50);
    }

    @Test
    void clinicalTrialsPage_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/dashboard/clinical-trials"))
                .andExpect(status().isUnauthorized());
    }

    // --- Helper ---

    private ClinicalTrial trial(String trialId, String nctId,
                                 ClinicalTrialStatus status, ClinicalTrialPhase phase) {
        return new ClinicalTrial(trialId, nctId, "AI Trial " + nctId,
                "Sponsor Inc", status, phase,
                List.of("Lung Cancer"), "A trial using AI for diagnosis.",
                "https://clinicaltrials.gov/study/" + nctId,
                "INTERVENTIONAL", null, Instant.now(), List.of("AI"));
    }
}
