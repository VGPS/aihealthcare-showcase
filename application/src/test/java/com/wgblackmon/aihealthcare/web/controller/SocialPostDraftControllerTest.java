package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.SavedPost;
import com.wgblackmon.aihealthcare.domain.model.SavedPostStatus;
import com.wgblackmon.aihealthcare.domain.model.SocialPlatform;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageSavedPostsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
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
 * MockMvc tests for {@link SocialPostDraftController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
@WebMvcTest(SocialPostDraftController.class)
class SocialPostDraftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManageSavedPostsUseCase savedPostsUseCase;

    @MockitoBean
    private TierResolver tierResolver;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    private SavedPost buildDraft(String id) {
        Instant now = Instant.parse("2026-10-01T12:00:00Z");
        return new SavedPost(id, SocialPlatform.LINKEDIN, "October 1, 2026",
                "Post body", "First comment", "My take",
                SavedPostStatus.DRAFT, now, now, null);
    }

    @Test
    @WithMockUser(roles = "SUBSCRIBER")
    void listDrafts_subscriberWithDrafts_renders200() throws Exception {
        when(tierResolver.hasFullAccess(any())).thenReturn(true);
        when(savedPostsUseCase.listDrafts()).thenReturn(List.of(buildDraft("id-1")));

        mockMvc.perform(get("/dashboard/social/drafts"))
                .andExpect(status().isOk())
                .andExpect(view().name("social-post-drafts"))
                .andExpect(model().attributeExists("drafts"))
                .andExpect(model().attribute("draftCount", 1));
    }

    @Test
    @WithMockUser(roles = "FREE")
    void listDrafts_freeUser_showsTierGate() throws Exception {
        when(tierResolver.hasFullAccess(any())).thenReturn(false);

        mockMvc.perform(get("/dashboard/social/drafts"))
                .andExpect(status().isOk())
                .andExpect(view().name("social-post-drafts"))
                .andExpect(model().attribute("fullAccess", false));
    }

    @Test
    @WithMockUser(roles = "SUBSCRIBER")
    void editDraft_existingPost_renders200() throws Exception {
        when(savedPostsUseCase.findById("id-1")).thenReturn(Optional.of(buildDraft("id-1")));

        mockMvc.perform(get("/dashboard/social/drafts/id-1/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("social-post-draft-edit"))
                .andExpect(model().attributeExists("post"));
    }

    @Test
    @WithMockUser(roles = "SUBSCRIBER")
    void editDraft_missingPost_redirectsToDrafts() throws Exception {
        when(savedPostsUseCase.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/dashboard/social/drafts/missing/edit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard/social/drafts"));
    }

    @Test
    @WithMockUser(roles = "SUBSCRIBER")
    void saveEdit_validInput_callsUpdateAndRedirects() throws Exception {
        when(savedPostsUseCase.update(anyString(), anyString(), any(), any()))
                .thenReturn(buildDraft("id-1"));

        mockMvc.perform(post("/dashboard/social/drafts/id-1/edit").with(csrf())
                        .param("body", "updated body")
                        .param("comment", "")
                        .param("yourTake", "Bill's voice"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard/social/drafts/id-1/edit"));

        verify(savedPostsUseCase).update("id-1", "updated body", null, "Bill's voice");
    }

    @Test
    @WithMockUser(roles = "SUBSCRIBER")
    void markPosted_validId_redirectsToDrafts() throws Exception {
        when(savedPostsUseCase.markPosted("id-1")).thenReturn(buildDraft("id-1"));

        mockMvc.perform(post("/dashboard/social/drafts/id-1/post").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard/social/drafts"));

        verify(savedPostsUseCase).markPosted("id-1");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteDraft_admin_redirectsToDrafts() throws Exception {
        mockMvc.perform(post("/dashboard/social/drafts/id-1/delete").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard/social/drafts"));

        verify(savedPostsUseCase).delete("id-1");
    }

    @Test
    @WithMockUser(roles = "SUBSCRIBER")
    void saveFromGenerator_validInput_savesTwoDraftsAndRedirects() throws Exception {
        when(savedPostsUseCase.save(any(), any(), anyString(), any(), any()))
                .thenReturn(buildDraft("id-new"));

        mockMvc.perform(post("/dashboard/social/drafts/save-from-generator").with(csrf())
                        .param("linkedinBody", "LI body")
                        .param("linkedinComment", "LI comment")
                        .param("facebookBody", "FB body")
                        .param("facebookComment", "")
                        .param("yourTake", "My voice")
                        .param("digestDate", "October 1, 2026"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard/social/drafts"));

        verify(savedPostsUseCase).save(SocialPlatform.LINKEDIN, "October 1, 2026", "LI body", "LI comment", "My voice");
        verify(savedPostsUseCase).save(SocialPlatform.FACEBOOK, "October 1, 2026", "FB body", null, "My voice");
    }
}
