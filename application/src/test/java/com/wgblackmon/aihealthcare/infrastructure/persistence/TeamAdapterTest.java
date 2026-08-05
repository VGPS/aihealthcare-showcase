package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.Team;
import com.wgblackmon.aihealthcare.domain.model.TeamMember;
import com.wgblackmon.aihealthcare.domain.model.TeamRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link TeamAdapter} using H2 in-memory DB.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@DataJpaTest
class TeamAdapterTest {

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository memberRepository;

    private TeamAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TeamAdapter(teamRepository, memberRepository);
    }

    @Test
    void saveTeam_persistsAndFindsById() {
        Team team = new Team("t1", "Acme Corp", "owner@test.com", Instant.now(), 1);
        adapter.saveTeam(team);

        Optional<Team> found = adapter.findTeamById("t1");
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Acme Corp");
    }

    @Test
    void findTeamsByOwner_returnsOwnedTeams() {
        adapter.saveTeam(new Team("t1", "Team1", "owner@test.com", Instant.now(), 1));
        adapter.saveTeam(new Team("t2", "Team2", "other@test.com", Instant.now(), 1));

        List<Team> result = adapter.findTeamsByOwner("owner@test.com");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Team1");
    }

    @Test
    void saveMember_persistsAndFindsByTeam() {
        adapter.saveTeam(new Team("t1", "Team", "owner@test.com", Instant.now(), 1));
        TeamMember member = new TeamMember("m1", "t1", "user@test.com", TeamRole.MEMBER, Instant.now());
        adapter.saveMember(member);

        List<TeamMember> members = adapter.findMembersByTeam("t1");
        assertThat(members).hasSize(1);
        assertThat(members.get(0).userEmail()).isEqualTo("user@test.com");
    }

    @Test
    void findMemberByTeamAndEmail_returnsMatch() {
        adapter.saveTeam(new Team("t1", "Team", "owner@test.com", Instant.now(), 1));
        adapter.saveMember(new TeamMember("m1", "t1", "user@test.com", TeamRole.ADMIN, Instant.now()));

        Optional<TeamMember> result = adapter.findMemberByTeamAndEmail("t1", "user@test.com");
        assertThat(result).isPresent();
        assertThat(result.get().teamRole()).isEqualTo(TeamRole.ADMIN);
    }

    @Test
    void findTeamByMemberEmail_returnsTeam() {
        adapter.saveTeam(new Team("t1", "Team", "owner@test.com", Instant.now(), 2));
        adapter.saveMember(new TeamMember("m1", "t1", "member@test.com", TeamRole.MEMBER, Instant.now()));

        Optional<Team> result = adapter.findTeamByMemberEmail("member@test.com");
        assertThat(result).isPresent();
        assertThat(result.get().teamId()).isEqualTo("t1");
    }

    @Test
    void removeMember_deletesFromDb() {
        adapter.saveTeam(new Team("t1", "Team", "owner@test.com", Instant.now(), 2));
        adapter.saveMember(new TeamMember("m1", "t1", "user@test.com", TeamRole.MEMBER, Instant.now()));

        adapter.removeMember("m1");

        assertThat(adapter.findMembersByTeam("t1")).isEmpty();
    }
}
