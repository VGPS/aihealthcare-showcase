package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code teams} table.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Entity
@Table(name = "teams")
public class TeamEntity {

    @Id
    @Column(name = "team_id", nullable = false, length = 36)
    private String teamId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "owner_email", nullable = false, length = 255)
    private String ownerEmail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "member_count", nullable = false)
    private int memberCount;

    public TeamEntity() {}

    public String getTeamId()                        { return teamId; }
    public void setTeamId(String teamId)             { this.teamId = teamId; }

    public String getName()                          { return name; }
    public void setName(String name)                 { this.name = name; }

    public String getOwnerEmail()                    { return ownerEmail; }
    public void setOwnerEmail(String ownerEmail)     { this.ownerEmail = ownerEmail; }

    public Instant getCreatedAt()                    { return createdAt; }
    public void setCreatedAt(Instant createdAt)      { this.createdAt = createdAt; }

    public int getMemberCount()                      { return memberCount; }
    public void setMemberCount(int memberCount)      { this.memberCount = memberCount; }
}
