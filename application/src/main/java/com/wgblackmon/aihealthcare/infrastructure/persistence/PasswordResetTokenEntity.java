package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code password_reset_tokens} table.
 *
 * <p>Mirrors the immutable {@link com.wgblackmon.aihealthcare.domain.model.PasswordResetToken}
 * domain record.  All mapping between the two types is performed inside
 * {@link PasswordResetAdapter} — this entity never escapes into the domain or
 * application layers.
 *
 * <p>The token string is the natural business key and serves as the primary key;
 * no surrogate ID is needed.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-17
 * @updated 2026-08-17
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetTokenEntity {

    @Id
    @Column(name = "token", nullable = false, length = 36)
    private String token;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used", nullable = false)
    private boolean used;

    /** Required no-arg constructor for JPA. */
    public PasswordResetTokenEntity() {}

    public String getToken()               { return token; }
    public void setToken(String token)     { this.token = token; }

    public String getEmail()               { return email; }
    public void setEmail(String email)     { this.email = email; }

    public Instant getExpiresAt()                  { return expiresAt; }
    public void setExpiresAt(Instant expiresAt)     { this.expiresAt = expiresAt; }

    public boolean isUsed()             { return used; }
    public void setUsed(boolean used)   { this.used = used; }
}
