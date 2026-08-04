package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AnalystNote;
import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.NoteTargetType;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.AnalystNotePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
 * MockMvc tests for {@link AnalystNoteController}.
 *
 * <p>Verifies GET rendering, POST add/edit/delete, tier gating (FREE redirects
 * to pricing), returnUrl redirect behavior, and ownership checks.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(AnalystNoteController.class)
class AnalystNoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalystNotePort analystNotePort;

    @MockitoBean
    private AppUserPort appUserPort;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");

    private void stubSubscriberUser() {
        AppUser user = new AppUser("user", "hashed", "Test User", "USER", true,
                SubscriptionTier.SUBSCRIBER, null);
        when(appUserPort.findByEmail("user")).thenReturn(Optional.of(user));
    }

    private void stubFreeUser() {
        AppUser user = new AppUser("user", "hashed", "Test User", "USER", true,
                SubscriptionTier.FREE, null);
        when(appUserPort.findByEmail("user")).thenReturn(Optional.of(user));
    }

    private void stubAdminUser() {
        AppUser user = new AppUser("user", "hashed", "Admin User", "ADMIN", true,
                SubscriptionTier.FREE, null);
        when(appUserPort.findByEmail("user")).thenReturn(Optional.of(user));
    }

    @Test
    void getNotes_subscriberUser_rendersPage() throws Exception {
        stubSubscriberUser();
        when(analystNotePort.findByUser("user")).thenReturn(List.of());

        mockMvc.perform(get("/notes"))
                .andExpect(status().isOk())
                .andExpect(view().name("notes"))
                .andExpect(model().attributeExists("notes", "noteCount", "noteDates"));
    }

    @Test
    void getNotes_freeUser_redirectsToPricing() throws Exception {
        stubFreeUser();

        mockMvc.perform(get("/notes"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pricing"));
    }

    @Test
    void getNotes_adminUser_rendersPage() throws Exception {
        stubAdminUser();
        when(analystNotePort.findByUser("user")).thenReturn(List.of());

        mockMvc.perform(get("/notes"))
                .andExpect(status().isOk())
                .andExpect(view().name("notes"));
    }

    @Test
    void postNotes_subscriberUser_addsNoteAndRedirects() throws Exception {
        stubSubscriberUser();

        mockMvc.perform(post("/notes").with(csrf())
                        .param("targetType", "COMPANY")
                        .param("targetId", "tempus-ai")
                        .param("targetLabel", "Tempus AI")
                        .param("content", "Great company"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notes"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(analystNotePort).save(any(AnalystNote.class));
    }

    @Test
    void postNotes_freeUser_redirectsToPricing() throws Exception {
        stubFreeUser();

        mockMvc.perform(post("/notes").with(csrf())
                        .param("targetType", "COMPANY")
                        .param("targetId", "tempus-ai")
                        .param("targetLabel", "Tempus AI")
                        .param("content", "Great company"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pricing"));

        verify(analystNotePort, never()).save(any());
    }

    @Test
    void editNote_subscriberUser_updatesAndRedirects() throws Exception {
        stubSubscriberUser();
        AnalystNote existing = new AnalystNote("n1", "user", NoteTargetType.COMPANY,
                "tempus-ai", "Tempus AI", "Old content", NOW, NOW);
        when(analystNotePort.findById("n1")).thenReturn(Optional.of(existing));

        mockMvc.perform(post("/notes/n1/edit").with(csrf())
                        .param("content", "Updated content"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notes"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(analystNotePort).save(any(AnalystNote.class));
    }

    @Test
    void deleteNote_subscriberUser_deletesAndRedirects() throws Exception {
        stubSubscriberUser();

        mockMvc.perform(post("/notes/n1/delete").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notes"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(analystNotePort).delete("n1", "user");
    }

    @Test
    void postNotes_withReturnUrl_redirectsToReturnUrl() throws Exception {
        stubSubscriberUser();

        mockMvc.perform(post("/notes").with(csrf())
                        .param("targetType", "COMPANY")
                        .param("targetId", "tempus-ai")
                        .param("targetLabel", "Tempus AI")
                        .param("content", "Inline note")
                        .param("returnUrl", "/companies/tempus-ai"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/companies/tempus-ai"));
    }
}
