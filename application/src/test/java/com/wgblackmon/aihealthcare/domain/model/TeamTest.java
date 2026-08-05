package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link Team} and {@link TeamMember} domain records.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
class TeamTest {

    private static final Instant NOW = Instant.now();

    @Test
    void validTeam_createsSuccessfully() {
        Team team = new Team("t1", "Acme Corp", "owner@test.com", NOW, 3);
        assertThat(team.teamId()).isEqualTo("t1");
        assertThat(team.name()).isEqualTo("Acme Corp");
        assertThat(team.memberCount()).isEqualTo(3);
    }

    @Test
    void blankTeamId_throws() {
        assertThatThrownBy(() -> new Team("", "Name", "owner@test.com", NOW, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("teamId");
    }

    @Test
    void blankName_throws() {
        assertThatThrownBy(() -> new Team("t1", " ", "owner@test.com", NOW, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void nullCreatedAt_throws() {
        assertThatThrownBy(() -> new Team("t1", "Name", "owner@test.com", null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("createdAt");
    }

    @Test
    void validTeamMember_createsSuccessfully() {
        TeamMember member = new TeamMember("m1", "t1", "user@test.com", TeamRole.MEMBER, NOW);
        assertThat(member.teamRole()).isEqualTo(TeamRole.MEMBER);
        assertThat(member.userEmail()).isEqualTo("user@test.com");
    }

    @Test
    void blankMemberId_throws() {
        assertThatThrownBy(() -> new TeamMember("", "t1", "user@test.com", TeamRole.MEMBER, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("memberId");
    }

    @Test
    void nullTeamRole_throws() {
        assertThatThrownBy(() -> new TeamMember("m1", "t1", "user@test.com", null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("teamRole");
    }

    @Test
    void allTeamRoles_haveValues() {
        assertThat(TeamRole.values()).containsExactly(
                TeamRole.OWNER, TeamRole.ADMIN, TeamRole.MEMBER);
    }
}
