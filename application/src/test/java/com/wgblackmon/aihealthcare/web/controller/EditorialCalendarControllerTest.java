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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MockMvc tests for {@link EditorialCalendarController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
@WebMvcTest(EditorialCalendarController.class)
class EditorialCalendarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManageEditorialCalendarUseCase editorialUseCase;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @Test
    @WithMockUser(roles = "ADMIN")
    void queue_admin_renders200() throws Exception {
        when(editorialUseCase.getQueue()).thenReturn(List.of(createItem("slug-1")));
        when(editorialUseCase.getNext()).thenReturn(Optional.empty());

        mockMvc.perform(get("/admin/editorial"))
                .andExpect(status().isOk())
                .andExpect(view().name("editorial-queue"))
                .andExpect(model().attributeExists("items"))
                .andExpect(model().attribute("totalCount", 1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void queue_withNextItem_addsNextItemToModel() throws Exception {
        EditorialItem nextItem = createItem("next-slug");
        when(editorialUseCase.getQueue()).thenReturn(List.of(nextItem));
        when(editorialUseCase.getNext()).thenReturn(Optional.of(nextItem));

        mockMvc.perform(get("/admin/editorial"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("nextItem"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void queue_priorityFilter_filtersItems() throws Exception {
        EditorialItem p0Item = createItem("p0-item");
        EditorialItem p1Item = new EditorialItem("p1-item", "P1 Title", "hook",
                EditorialTheme.HEALTHCARE_ECONOMICS, EditorialDemandSignal.STEADY,
                EditorialPriority.P1, EditorialEffort.L, "cost_model",
                LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 27),
                LocalDate.of(2026, 11, 24), "CTA",
                EditorialStatus.PLANNED, LocalDate.of(2026, 10, 1),
                List.of(), List.of(), null);
        when(editorialUseCase.getQueue()).thenReturn(List.of(p0Item, p1Item));
        when(editorialUseCase.getNext()).thenReturn(Optional.empty());

        mockMvc.perform(get("/admin/editorial").param("priority", "P0"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("totalCount", 1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void advance_redirectsToQueue() throws Exception {
        EditorialItem advanced = new EditorialItem("slug-1", "Texas TRAIGA Test", "hook",
                EditorialTheme.HEALTHCARE_GOVERNANCE, EditorialDemandSignal.HOT,
                EditorialPriority.P0, EditorialEffort.M, "explainer",
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 9),
                LocalDate.of(2026, 10, 6), "CTA",
                EditorialStatus.RESEARCHING, LocalDate.of(2026, 10, 1), List.of(), List.of(), null);
        when(editorialUseCase.advanceStatus(anyString()))
                .thenReturn(advanced);

        mockMvc.perform(post("/admin/editorial/slug-1/advance").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/editorial"));

        verify(editorialUseCase).advanceStatus("slug-1");
    }

    @Test
    void queue_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/admin/editorial"))
                .andExpect(status().is4xxClientError());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EditorialItem createItem(String id) {
        return new EditorialItem(
                id, "Texas TRAIGA Test", "hook",
                EditorialTheme.HEALTHCARE_GOVERNANCE, EditorialDemandSignal.HOT,
                EditorialPriority.P0, EditorialEffort.M, "explainer",
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 9),
                LocalDate.of(2026, 10, 6), "CTA",
                EditorialStatus.PLANNED, LocalDate.of(2026, 10, 1),
                List.of("healthcare-compliance"), List.of(), null);
    }
}
