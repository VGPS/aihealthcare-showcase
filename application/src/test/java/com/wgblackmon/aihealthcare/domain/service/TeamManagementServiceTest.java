package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.Team;
import com.wgblackmon.aihealthcare.domain.model.TeamMember;
import com.wgblackmon.aihealthcare.domain.model.TeamRole;
import com.wgblackmon.aihealthcare.domain.port.outbound.TeamPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TeamManagementService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@ExtendWith(MockitoExtension.class)
class TeamManagementServiceTest {

    @Mock
    private TeamPort teamPort;

    private TeamManagementService service;

    @BeforeEach
    void setUp() {
        service = new TeamManagementService(teamPort);
    }

    @Test
    void createTeam_savesTeamAndOwnerMember() {
        when(teamPort.findTeamsByOwner("owner@test.com")).thenReturn(List.of());

        Team result = service.createTeam("Acme Corp", "owner@test.com");

        assertThat(result.name()).isEqualTo("Acme Corp");
        assertThat(result.ownerEmail()).isEqualTo("owner@test.com");
        assertThat(result.memberCount()).isEqualTo(1);
        verify(teamPort).saveTeam(any(Team.class));
        verify(teamPort).saveMember(any(TeamMember.class));
    }

    @Test
    void createTeam_alreadyOwnsTeam_throws() {
        Team existing = new Team("t1", "Existing", "owner@test.com", Instant.now(), 1);
        when(teamPort.findTeamsByOwner("owner@test.com")).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.createTeam("New Team", "owner@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already owns");
    }

    @Test
    void addMember_byOwner_succeeds() {
        Team team = new Team("t1", "Team", "owner@test.com", Instant.now(), 1);
        TeamMember ownerMember = new TeamMember("m1", "t1", "owner@test.com", TeamRole.OWNER, Instant.now());

        when(teamPort.findTeamById("t1")).thenReturn(Optional.of(team));
        when(teamPort.findMemberByTeamAndEmail("t1", "owner@test.com")).thenReturn(Optional.of(ownerMember));
        when(teamPort.findMemberByTeamAndEmail("t1", "new@test.com")).thenReturn(Optional.empty());
        when(teamPort.findMembersByTeam("t1")).thenReturn(List.of(ownerMember));

        TeamMember result = service.addMember("t1", "new@test.com", "owner@test.com");

        assertThat(result.userEmail()).isEqualTo("new@test.com");
        assertThat(result.teamRole()).isEqualTo(TeamRole.MEMBER);
        verify(teamPort).saveMember(any(TeamMember.class));
    }

    @Test
    void addMember_byNonOwner_throws() {
        Team team = new Team("t1", "Team", "owner@test.com", Instant.now(), 1);
        TeamMember regularMember = new TeamMember("m2", "t1", "regular@test.com", TeamRole.MEMBER, Instant.now());

        when(teamPort.findTeamById("t1")).thenReturn(Optional.of(team));
        when(teamPort.findMemberByTeamAndEmail("t1", "regular@test.com")).thenReturn(Optional.of(regularMember));

        assertThatThrownBy(() -> service.addMember("t1", "new@test.com", "regular@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Not authorized");
    }

    @Test
    void addMember_alreadyMember_throws() {
        Team team = new Team("t1", "Team", "owner@test.com", Instant.now(), 2);
        TeamMember ownerMember = new TeamMember("m1", "t1", "owner@test.com", TeamRole.OWNER, Instant.now());
        TeamMember existingMember = new TeamMember("m2", "t1", "existing@test.com", TeamRole.MEMBER, Instant.now());

        when(teamPort.findTeamById("t1")).thenReturn(Optional.of(team));
        when(teamPort.findMemberByTeamAndEmail("t1", "owner@test.com")).thenReturn(Optional.of(ownerMember));
        when(teamPort.findMemberByTeamAndEmail("t1", "existing@test.com")).thenReturn(Optional.of(existingMember));

        assertThatThrownBy(() -> service.addMember("t1", "existing@test.com", "owner@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already a member");
    }

    @Test
    void removeMember_byOwner_succeeds() {
        Team team = new Team("t1", "Team", "owner@test.com", Instant.now(), 2);
        TeamMember ownerMember = new TeamMember("m1", "t1", "owner@test.com", TeamRole.OWNER, Instant.now());
        TeamMember target = new TeamMember("m2", "t1", "member@test.com", TeamRole.MEMBER, Instant.now());

        when(teamPort.findMemberByTeamAndEmail("t1", "owner@test.com")).thenReturn(Optional.of(ownerMember));
        when(teamPort.findMemberByTeamAndEmail("t1", "member@test.com")).thenReturn(Optional.of(target));
        when(teamPort.findTeamById("t1")).thenReturn(Optional.of(team));
        when(teamPort.findMembersByTeam("t1")).thenReturn(List.of(ownerMember));

        service.removeMember("t1", "member@test.com", "owner@test.com");

        verify(teamPort).removeMember("m2");
    }

    @Test
    void removeMember_ownerCantBeRemoved() {
        TeamMember ownerMember = new TeamMember("m1", "t1", "owner@test.com", TeamRole.OWNER, Instant.now());

        when(teamPort.findMemberByTeamAndEmail("t1", "owner@test.com")).thenReturn(Optional.of(ownerMember));

        assertThatThrownBy(() -> service.removeMember("t1", "owner@test.com", "owner@test.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot remove the team owner");
    }

    @Test
    void getTeamForUser_delegatesToPort() {
        Team team = new Team("t1", "Team", "owner@test.com", Instant.now(), 2);
        when(teamPort.findTeamByMemberEmail("user@test.com")).thenReturn(Optional.of(team));

        Optional<Team> result = service.getTeamForUser("user@test.com");

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("Team");
    }
}
