package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.Team;
import com.wgblackmon.aihealthcare.domain.model.TeamMember;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageTeamsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for multi-tenant team management at {@code /api/v1/teams}.
 *
 * <p>ENTERPRISE-tier users can create teams, add/remove members, and list
 * team membership. Non-ENTERPRISE users receive 403.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final ManageTeamsUseCase manageTeamsUseCase;
    private final AppUserPort appUserPort;

    public TeamController(ManageTeamsUseCase manageTeamsUseCase, AppUserPort appUserPort) {
        log.debug("TeamController() | manageTeamsUseCase={}, appUserPort={}",
                manageTeamsUseCase.getClass().getSimpleName(),
                appUserPort.getClass().getSimpleName());
        this.manageTeamsUseCase = manageTeamsUseCase;
        this.appUserPort = appUserPort;
    }

    @Transactional
    @PostMapping
    public ResponseEntity<?> createTeam(@RequestParam String name, Principal principal) {
        log.debug("createTeam() | name={}, user={}", name, principal.getName());
        if (!isEnterprise(principal.getName())) {
            log.debug("createTeam() | denied — not ENTERPRISE tier");
            return ResponseEntity.status(403).body(Map.of("error", "ENTERPRISE tier required"));
        }
        try {
            Team team = manageTeamsUseCase.createTeam(name, principal.getName());
            log.debug("createTeam() | return=created teamId={}", team.teamId());
            return ResponseEntity.status(201).body(teamToMap(team));
        } catch (IllegalStateException e) {
            log.debug("createTeam() | return=409 {}", e.getMessage());
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getMyTeam(Principal principal) {
        log.debug("getMyTeam() | user={}", principal.getName());
        Optional<Team> team = manageTeamsUseCase.getTeamForUser(principal.getName());
        if (team.isEmpty()) {
            log.debug("getMyTeam() | return=no team");
            return ResponseEntity.ok(Map.of("team", (Object) null));
        }
        log.debug("getMyTeam() | return=teamId={}", team.get().teamId());
        return ResponseEntity.ok(teamToMap(team.get()));
    }

    @GetMapping("/{teamId}/members")
    public ResponseEntity<?> getMembers(@PathVariable String teamId, Principal principal) {
        log.debug("getMembers() | teamId={}, user={}", teamId, principal.getName());
        List<TeamMember> members = manageTeamsUseCase.getMembers(teamId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (TeamMember member : members) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("memberId", member.memberId());
            entry.put("userEmail", member.userEmail());
            entry.put("teamRole", member.teamRole().name());
            entry.put("joinedAt", member.joinedAt().toString());
            result.add(entry);
        }
        log.debug("getMembers() | return={} members", result.size());
        return ResponseEntity.ok(result);
    }

    @Transactional
    @PostMapping("/{teamId}/members")
    public ResponseEntity<?> addMember(@PathVariable String teamId,
                                        @RequestParam String email,
                                        Principal principal) {
        log.debug("addMember() | teamId={}, email={}, requestedBy={}", teamId, email, principal.getName());
        try {
            TeamMember member = manageTeamsUseCase.addMember(teamId, email, principal.getName());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("memberId", member.memberId());
            result.put("userEmail", member.userEmail());
            result.put("teamRole", member.teamRole().name());
            log.debug("addMember() | return=201 memberId={}", member.memberId());
            return ResponseEntity.status(201).body(result);
        } catch (IllegalStateException | IllegalArgumentException e) {
            log.debug("addMember() | return=400 {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Transactional
    @DeleteMapping("/{teamId}/members/{email}")
    public ResponseEntity<?> removeMember(@PathVariable String teamId,
                                           @PathVariable String email,
                                           Principal principal) {
        log.debug("removeMember() | teamId={}, email={}, requestedBy={}", teamId, email, principal.getName());
        try {
            manageTeamsUseCase.removeMember(teamId, email, principal.getName());
            log.debug("removeMember() | return=204");
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException | IllegalArgumentException e) {
            log.debug("removeMember() | return=400 {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private boolean isEnterprise(String email) {
        Optional<AppUser> user = appUserPort.findByEmail(email);
        return user.isPresent() && user.get().tier() == SubscriptionTier.ENTERPRISE;
    }

    private Map<String, Object> teamToMap(Team team) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("teamId", team.teamId());
        map.put("name", team.name());
        map.put("ownerEmail", team.ownerEmail());
        map.put("memberCount", team.memberCount());
        map.put("createdAt", team.createdAt().toString());
        return map;
    }
}
