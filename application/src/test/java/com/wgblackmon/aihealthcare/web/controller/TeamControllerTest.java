package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.Team;
import com.wgblackmon.aihealthcare.domain.model.TeamMember;
import com.wgblackmon.aihealthcare.domain.model.TeamRole;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageTeamsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for {@link TeamController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Import(SecurityConfig.class)
@WebMvcTest(TeamController.class)
class TeamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManageTeamsUseCase manageTeamsUseCase;

    @MockitoBean
    private AppUserPort appUserPort;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    private void stubEnterprise(String email) {
        when(appUserPort.findByEmail(email)).thenReturn(Optional.of(
                new AppUser(email, "$2a$hash", "User", "USER", true,
                        SubscriptionTier.ENTERPRISE, null)));
    }

    private void stubSubscriber(String email) {
        when(appUserPort.findByEmail(email)).thenReturn(Optional.of(
                new AppUser(email, "$2a$hash", "User", "USER", true,
                        SubscriptionTier.SUBSCRIBER, null)));
    }

    @Test
    @WithMockUser(username = "enterprise@test.com")
    @DisplayName("POST /api/v1/teams creates team for ENTERPRISE user")
    void createTeam_enterprise_returns201() throws Exception {
        stubEnterprise("enterprise@test.com");
        Team team = new Team("t1", "Acme", "enterprise@test.com", Instant.now(), 1);
        when(manageTeamsUseCase.createTeam("Acme", "enterprise@test.com")).thenReturn(team);

        mockMvc.perform(post("/api/v1/teams").param("name", "Acme").with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teamId").value("t1"))
                .andExpect(jsonPath("$.name").value("Acme"));
    }

    @Test
    @WithMockUser(username = "subscriber@test.com")
    @DisplayName("POST /api/v1/teams denied for non-ENTERPRISE user")
    void createTeam_subscriber_returns403() throws Exception {
        stubSubscriber("subscriber@test.com");

        mockMvc.perform(post("/api/v1/teams").param("name", "Team").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("GET /api/v1/teams returns user's team")
    void getMyTeam_returnsTeam() throws Exception {
        Team team = new Team("t1", "Team", "owner@test.com", Instant.now(), 3);
        when(manageTeamsUseCase.getTeamForUser("user@test.com")).thenReturn(Optional.of(team));

        mockMvc.perform(get("/api/v1/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value("t1"));
    }

    @Test
    @WithMockUser(username = "user@test.com")
    @DisplayName("GET /api/v1/teams/{id}/members returns member list")
    void getMembers_returnsJson() throws Exception {
        TeamMember member = new TeamMember("m1", "t1", "user@test.com", TeamRole.MEMBER, Instant.now());
        when(manageTeamsUseCase.getMembers("t1")).thenReturn(List.of(member));

        mockMvc.perform(get("/api/v1/teams/t1/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userEmail").value("user@test.com"))
                .andExpect(jsonPath("$[0].teamRole").value("MEMBER"));
    }

    @Test
    @WithMockUser(username = "owner@test.com")
    @DisplayName("POST /api/v1/teams/{id}/members adds member")
    void addMember_returns201() throws Exception {
        TeamMember member = new TeamMember("m2", "t1", "new@test.com", TeamRole.MEMBER, Instant.now());
        when(manageTeamsUseCase.addMember("t1", "new@test.com", "owner@test.com")).thenReturn(member);

        mockMvc.perform(post("/api/v1/teams/t1/members")
                        .param("email", "new@test.com")
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userEmail").value("new@test.com"));
    }
}
