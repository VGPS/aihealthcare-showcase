package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * A multi-tenant team that groups users under a shared workspace.
 *
 * <p>Teams are created by ENTERPRISE-tier users and allow shared access
 * to watchlists, deal signals, relationship graphs, and API keys within
 * the organization.
 *
 * @param teamId      unique identifier (UUID)
 * @param name        display name of the team
 * @param ownerEmail  email of the team creator (ENTERPRISE user)
 * @param createdAt   when the team was created
 * @param memberCount current number of team members
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public record Team(
        String teamId,
        String name,
        String ownerEmail,
        Instant createdAt,
        int memberCount
) {

    public Team {
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("teamId must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (ownerEmail == null || ownerEmail.isBlank()) {
            throw new IllegalArgumentException("ownerEmail must not be blank");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
    }
}
