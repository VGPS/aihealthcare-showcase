package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.Team;
import com.wgblackmon.aihealthcare.domain.model.TeamMember;
import com.wgblackmon.aihealthcare.domain.model.TeamRole;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageTeamsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.TeamPort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain service implementing multi-tenant team management.
 *
 * <p>ENTERPRISE-tier users can create teams, invite members, and manage
 * membership. Team members share access to the owner's ENTERPRISE features
 * (API keys, watchlists, deal signals, etc.).
 *
 * <p>Authorization rules:
 * <ul>
 *   <li>Only OWNER/ADMIN can add or remove members</li>
 *   <li>OWNER cannot be removed</li>
 *   <li>A user can only belong to one team at a time</li>
 * </ul>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public class TeamManagementService implements ManageTeamsUseCase {

    private static final int MAX_MEMBERS = 25;

    private final TeamPort teamPort;

    public TeamManagementService(TeamPort teamPort) {
        this.teamPort = teamPort;
    }

    @Override
    public Team createTeam(String name, String ownerEmail) {
        List<Team> existing = teamPort.findTeamsByOwner(ownerEmail);
        if (!existing.isEmpty()) {
            throw new IllegalStateException("User already owns a team");
        }

        String teamId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Team team = new Team(teamId, name, ownerEmail, now, 1);
        teamPort.saveTeam(team);

        TeamMember ownerMember = new TeamMember(
                UUID.randomUUID().toString(), teamId, ownerEmail, TeamRole.OWNER, now);
        teamPort.saveMember(ownerMember);

        return team;
    }

    @Override
    public Optional<Team> getTeam(String teamId) {
        return teamPort.findTeamById(teamId);
    }

    @Override
    public List<Team> getTeamsForOwner(String ownerEmail) {
        return teamPort.findTeamsByOwner(ownerEmail);
    }

    @Override
    public TeamMember addMember(String teamId, String userEmail, String requestedByEmail) {
        Optional<Team> teamOpt = teamPort.findTeamById(teamId);
        if (teamOpt.isEmpty()) {
            throw new IllegalArgumentException("Team not found: " + teamId);
        }

        Optional<TeamMember> requester = teamPort.findMemberByTeamAndEmail(teamId, requestedByEmail);
        if (requester.isEmpty() ||
                (requester.get().teamRole() != TeamRole.OWNER && requester.get().teamRole() != TeamRole.ADMIN)) {
            throw new IllegalStateException("Not authorized to add members");
        }

        Optional<TeamMember> existingMembership = teamPort.findMemberByTeamAndEmail(teamId, userEmail);
        if (existingMembership.isPresent()) {
            throw new IllegalStateException("User is already a member of this team");
        }

        List<TeamMember> currentMembers = teamPort.findMembersByTeam(teamId);
        if (currentMembers.size() >= MAX_MEMBERS) {
            throw new IllegalStateException("Team has reached the maximum of " + MAX_MEMBERS + " members");
        }

        TeamMember member = new TeamMember(
                UUID.randomUUID().toString(), teamId, userEmail, TeamRole.MEMBER, Instant.now());
        teamPort.saveMember(member);

        Team team = teamOpt.get();
        Team updated = new Team(team.teamId(), team.name(), team.ownerEmail(),
                team.createdAt(), currentMembers.size() + 1);
        teamPort.saveTeam(updated);

        return member;
    }

    @Override
    public void removeMember(String teamId, String userEmail, String requestedByEmail) {
        Optional<TeamMember> requester = teamPort.findMemberByTeamAndEmail(teamId, requestedByEmail);
        if (requester.isEmpty() ||
                (requester.get().teamRole() != TeamRole.OWNER && requester.get().teamRole() != TeamRole.ADMIN)) {
            throw new IllegalStateException("Not authorized to remove members");
        }

        Optional<TeamMember> target = teamPort.findMemberByTeamAndEmail(teamId, userEmail);
        if (target.isEmpty()) {
            throw new IllegalArgumentException("User is not a member of this team");
        }

        if (target.get().teamRole() == TeamRole.OWNER) {
            throw new IllegalStateException("Cannot remove the team owner");
        }

        teamPort.removeMember(target.get().memberId());

        Optional<Team> teamOpt = teamPort.findTeamById(teamId);
        if (teamOpt.isPresent()) {
            Team team = teamOpt.get();
            List<TeamMember> remaining = teamPort.findMembersByTeam(teamId);
            Team updated = new Team(team.teamId(), team.name(), team.ownerEmail(),
                    team.createdAt(), remaining.size());
            teamPort.saveTeam(updated);
        }
    }

    @Override
    public List<TeamMember> getMembers(String teamId) {
        return teamPort.findMembersByTeam(teamId);
    }

    @Override
    public Optional<Team> getTeamForUser(String userEmail) {
        return teamPort.findTeamByMemberEmail(userEmail);
    }
}
