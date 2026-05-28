package com.wgblackmon.aihealthcare.domain.model;

/**
 * Immutable domain record representing an application user who can log in
 * to the web UI.
 *
 * <p>Email is the natural business key and serves as the unique identifier
 * in the persistence layer.  The {@code passwordHash} stores a BCrypt-encoded
 * password; raw passwords never appear in domain objects.
 *
 * <p>The {@code role} field holds a simple authority string ("USER" or "ADMIN").
 * Role-based access control beyond "authenticated vs anonymous" is deferred
 * to a future slice; this field is present for forward-compatibility.
 *
 * <p>The {@code enabled} flag supports soft-disabling accounts without deletion.
 *
 * @param email        The user's email address; used as the unique login identifier.
 *                     Must not be blank.
 * @param passwordHash BCrypt-encoded password hash.  Must not be blank.
 * @param displayName  Human-readable display name.  Must not be blank.
 * @param role         Authority role string (e.g. "USER", "ADMIN").  Must not be blank.
 * @param enabled      {@code true} if the account is active and allowed to log in.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-28
 * @updated 2026-05-28
 */
public record AppUser(
        String  email,
        String  passwordHash,
        String  displayName,
        String  role,
        boolean enabled
) {
    /** Compact canonical constructor — validates required fields. */
    public AppUser {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role must not be blank");
        }
    }
}
