package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.EditorialDemandSignal;
import com.wgblackmon.aihealthcare.domain.model.EditorialEffort;
import com.wgblackmon.aihealthcare.domain.model.EditorialItem;
import com.wgblackmon.aihealthcare.domain.model.EditorialPriority;
import com.wgblackmon.aihealthcare.domain.model.EditorialStatus;
import com.wgblackmon.aihealthcare.domain.model.EditorialTheme;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageEditorialCalendarUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for {@link EditorialCalendarRestController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
@WebMvcTest(EditorialCalendarRestController.class)
class EditorialCalendarRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManageEditorialCalendarUseCase editorialUseCase;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @Test
    @WithMockUser
    void listItems_noFilter_returnsAll() throws Exception {
        when(editorialUseCase.getAll()).thenReturn(List.of(createItem("texas-traiga")));

        mockMvc.perform(get("/api/v1/editorial"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("texas-traiga"))
                .andExpect(jsonPath("$[0].priorityTier").value("P0"));
    }

    @Test
    @WithMockUser
    void getNext_itemExists_returns200() throws Exception {
        when(editorialUseCase.getNext()).thenReturn(Optional.of(createItem("texas-traiga")));

        mockMvc.perform(get("/api/v1/editorial/next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("texas-traiga"));
    }

    @Test
    @WithMockUser
    void getNext_queueEmpty_returns204() throws Exception {
        when(editorialUseCase.getNext()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/editorial/next"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void getById_found_returns200() throws Exception {
        when(editorialUseCase.getById("texas-traiga")).thenReturn(Optional.of(createItem("texas-traiga")));

        mockMvc.perform(get("/api/v1/editorial/texas-traiga"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Texas TRAIGA Test"));
    }

    @Test
    @WithMockUser
    void getById_notFound_returns404() throws Exception {
        when(editorialUseCase.getById(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/editorial/nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void advanceStatus_admin_returns200() throws Exception {
        EditorialItem updated = new EditorialItem(
                "texas-traiga", "Texas TRAIGA Test", "hook",
                EditorialTheme.HEALTHCARE_GOVERNANCE, EditorialDemandSignal.HOT,
                EditorialPriority.P0, EditorialEffort.M, "explainer",
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 9),
                LocalDate.of(2026, 10, 6), "CTA",
                EditorialStatus.RESEARCHING,
                LocalDate.of(2026, 10, 1), List.of(), List.of());
        when(editorialUseCase.advanceStatus("texas-traiga")).thenReturn(updated);

        mockMvc.perform(post("/api/v1/editorial/texas-traiga/status").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESEARCHING"))
                .andExpect(jsonPath("$.id").value("texas-traiga"));

        verify(editorialUseCase).advanceStatus("texas-traiga");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private EditorialItem createItem(String id) {
        return new EditorialItem(
                id, "Texas TRAIGA Test", "hook",
                EditorialTheme.HEALTHCARE_GOVERNANCE, EditorialDemandSignal.HOT,
                EditorialPriority.P0, EditorialEffort.M, "explainer",
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 9),
                LocalDate.of(2026, 10, 6), "Download the checklist",
                EditorialStatus.PLANNED,
                LocalDate.of(2026, 10, 1), List.of("healthcare-compliance"), List.of());
    }
}
