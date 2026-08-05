package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code team_members} table.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Entity
@Table(name = "team_members")
public class TeamMemberEntity {

    @Id
    @Column(name = "member_id", nullable = false, length = 36)
    private String memberId;

    @Column(name = "team_id", nullable = false, length = 36)
    private String teamId;

    @Column(name = "user_email", nullable = false, length = 255)
    private String userEmail;

    @Column(name = "team_role", nullable = false, length = 20)
    private String teamRole;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    public TeamMemberEntity() {}

    public String getMemberId()                      { return memberId; }
    public void setMemberId(String memberId)         { this.memberId = memberId; }

    public String getTeamId()                        { return teamId; }
    public void setTeamId(String teamId)             { this.teamId = teamId; }

    public String getUserEmail()                     { return userEmail; }
    public void setUserEmail(String userEmail)       { this.userEmail = userEmail; }

    public String getTeamRole()                      { return teamRole; }
    public void setTeamRole(String teamRole)         { this.teamRole = teamRole; }

    public Instant getJoinedAt()                     { return joinedAt; }
    public void setJoinedAt(Instant joinedAt)        { this.joinedAt = joinedAt; }
}
