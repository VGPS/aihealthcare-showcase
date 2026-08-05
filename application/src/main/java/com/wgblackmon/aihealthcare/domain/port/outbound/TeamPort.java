package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.Team;
import com.wgblackmon.aihealthcare.domain.model.TeamMember;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying {@link Team} and {@link TeamMember} records.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public interface TeamPort {

    void saveTeam(Team team);

    Optional<Team> findTeamById(String teamId);

    List<Team> findTeamsByOwner(String ownerEmail);

    void deleteTeam(String teamId);

    void saveMember(TeamMember member);

    void removeMember(String memberId);

    List<TeamMember> findMembersByTeam(String teamId);

    Optional<TeamMember> findMemberByTeamAndEmail(String teamId, String userEmail);

    Optional<Team> findTeamByMemberEmail(String userEmail);
}
