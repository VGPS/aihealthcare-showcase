package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.Team;
import com.wgblackmon.aihealthcare.domain.model.TeamMember;
import com.wgblackmon.aihealthcare.domain.model.TeamRole;
import com.wgblackmon.aihealthcare.domain.port.outbound.TeamPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed implementation of {@link TeamPort}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Slf4j
@Component
public class TeamAdapter implements TeamPort {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository memberRepository;

    public TeamAdapter(TeamRepository teamRepository, TeamMemberRepository memberRepository) {
        log.debug("TeamAdapter() | teamRepository={}, memberRepository={}",
                teamRepository.getClass().getSimpleName(),
                memberRepository.getClass().getSimpleName());
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
    }

    @Override
    public void saveTeam(Team team) {
        log.debug("saveTeam() | teamId={}", team.teamId());
        TeamEntity entity = new TeamEntity();
        entity.setTeamId(team.teamId());
        entity.setName(team.name());
        entity.setOwnerEmail(team.ownerEmail());
        entity.setCreatedAt(team.createdAt());
        entity.setMemberCount(team.memberCount());
        teamRepository.save(entity);
        log.debug("saveTeam() | return=void");
    }

    @Override
    public Optional<Team> findTeamById(String teamId) {
        log.debug("findTeamById() | teamId={}", teamId);
        Optional<Team> result = teamRepository.findById(teamId).map(this::toTeamDomain);
        log.debug("findTeamById() | return={}", result.isPresent() ? "found" : "empty");
        return result;
    }

    @Override
    public List<Team> findTeamsByOwner(String ownerEmail) {
        log.debug("findTeamsByOwner() | ownerEmail={}", ownerEmail);
        List<TeamEntity> entities = teamRepository.findByOwnerEmailOrderByCreatedAtDesc(ownerEmail);
        List<Team> result = new ArrayList<>();
        for (TeamEntity entity : entities) {
            result.add(toTeamDomain(entity));
        }
        log.debug("findTeamsByOwner() | return={} teams", result.size());
        return result;
    }

    @Override
    public void deleteTeam(String teamId) {
        log.debug("deleteTeam() | teamId={}", teamId);
        teamRepository.deleteById(teamId);
        log.debug("deleteTeam() | return=void");
    }

    @Override
    public void saveMember(TeamMember member) {
        log.debug("saveMember() | memberId={}, teamId={}, userEmail={}",
                member.memberId(), member.teamId(), member.userEmail());
        TeamMemberEntity entity = new TeamMemberEntity();
        entity.setMemberId(member.memberId());
        entity.setTeamId(member.teamId());
        entity.setUserEmail(member.userEmail());
        entity.setTeamRole(member.teamRole().name());
        entity.setJoinedAt(member.joinedAt());
        memberRepository.save(entity);
        log.debug("saveMember() | return=void");
    }

    @Override
    public void removeMember(String memberId) {
        log.debug("removeMember() | memberId={}", memberId);
        memberRepository.deleteById(memberId);
        log.debug("removeMember() | return=void");
    }

    @Override
    public List<TeamMember> findMembersByTeam(String teamId) {
        log.debug("findMembersByTeam() | teamId={}", teamId);
        List<TeamMemberEntity> entities = memberRepository.findByTeamIdOrderByJoinedAtAsc(teamId);
        List<TeamMember> result = new ArrayList<>();
        for (TeamMemberEntity entity : entities) {
            result.add(toMemberDomain(entity));
        }
        log.debug("findMembersByTeam() | return={} members", result.size());
        return result;
    }

    @Override
    public Optional<TeamMember> findMemberByTeamAndEmail(String teamId, String userEmail) {
        log.debug("findMemberByTeamAndEmail() | teamId={}, userEmail={}", teamId, userEmail);
        Optional<TeamMember> result = memberRepository.findByTeamIdAndUserEmail(teamId, userEmail)
                .map(this::toMemberDomain);
        log.debug("findMemberByTeamAndEmail() | return={}", result.isPresent() ? "found" : "empty");
        return result;
    }

    @Override
    public Optional<Team> findTeamByMemberEmail(String userEmail) {
        log.debug("findTeamByMemberEmail() | userEmail={}", userEmail);
        Optional<TeamMemberEntity> memberOpt = memberRepository.findByUserEmail(userEmail);
        if (memberOpt.isEmpty()) {
            log.debug("findTeamByMemberEmail() | return=empty");
            return Optional.empty();
        }
        Optional<Team> result = teamRepository.findById(memberOpt.get().getTeamId())
                .map(this::toTeamDomain);
        log.debug("findTeamByMemberEmail() | return={}", result.isPresent() ? "found" : "empty");
        return result;
    }

    private Team toTeamDomain(TeamEntity entity) {
        return new Team(
                entity.getTeamId(),
                entity.getName(),
                entity.getOwnerEmail(),
                entity.getCreatedAt(),
                entity.getMemberCount()
        );
    }

    private TeamMember toMemberDomain(TeamMemberEntity entity) {
        return new TeamMember(
                entity.getMemberId(),
                entity.getTeamId(),
                entity.getUserEmail(),
                TeamRole.valueOf(entity.getTeamRole()),
                entity.getJoinedAt()
        );
    }
}
