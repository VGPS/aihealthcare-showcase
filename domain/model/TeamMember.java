package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * A user's membership in a {@link Team}, with their assigned role.
 *
 * @param memberId   unique identifier (UUID)
 * @param teamId     the team this membership belongs to
 * @param userEmail  email of the team member
 * @param teamRole   role within the team (OWNER, ADMIN, MEMBER)
 * @param joinedAt   when the user joined the team
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public record TeamMember(
        String memberId,
        String teamId,
        String userEmail,
        TeamRole teamRole,
        Instant joinedAt
) {

    public TeamMember {
        if (memberId == null || memberId.isBlank()) {
            throw new IllegalArgumentException("memberId must not be blank");
        }
        if (teamId == null || teamId.isBlank()) {
            throw new IllegalArgumentException("teamId must not be blank");
        }
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("userEmail must not be blank");
        }
        if (teamRole == null) {
            throw new IllegalArgumentException("teamRole must not be null");
        }
        if (joinedAt == null) {
            throw new IllegalArgumentException("joinedAt must not be null");
        }
    }
}
