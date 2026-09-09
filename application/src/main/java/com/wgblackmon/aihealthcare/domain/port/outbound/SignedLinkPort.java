package com.wgblackmon.aihealthcare.domain.port.outbound;

import java.time.Instant;
import java.util.Optional;

/**
 * Creates and verifies time-limited signed download tokens.
 *
 * <p>Tokens are opaque strings encoding a job id and an expiration
 * timestamp, signed with HMAC-SHA256. They allow unauthenticated
 * artifact downloads at {@code /d/{token}} without requiring a
 * session.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public interface SignedLinkPort {

    /**
     * Creates a signed token for the given job, expiring at the given instant.
     *
     * @param jobId     the job whose artifact the token grants access to
     * @param expiresAt when the token becomes invalid
     * @return an opaque URL-safe token string
     */
    String createToken(String jobId, Instant expiresAt);

    /**
     * Verifies a token and returns the job id if valid and not expired.
     *
     * @param token the token to verify
     * @param now   current time for expiry check
     * @return the job id if the token is valid, empty otherwise
     */
    Optional<String> verifyToken(String token, Instant now);
}
