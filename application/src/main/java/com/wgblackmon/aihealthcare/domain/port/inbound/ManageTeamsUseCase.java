package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.Team;
import com.wgblackmon.aihealthcare.domain.model.TeamMember;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port for managing multi-tenant team accounts.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public interface ManageTeamsUseCase {

    Team createTeam(String name, String ownerEmail);

    Optional<Team> getTeam(String teamId);

    List<Team> getTeamsForOwner(String ownerEmail);

    TeamMember addMember(String teamId, String userEmail, String requestedByEmail);

    void removeMember(String teamId, String userEmail, String requestedByEmail);

    List<TeamMember> getMembers(String teamId);

    Optional<Team> getTeamForUser(String userEmail);
}
