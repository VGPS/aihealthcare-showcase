package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity representing a row in the {@code app_users} table.
 *
 * <p>Mirrors the immutable {@link com.wgblackmon.aihealthcare.domain.model.AppUser}
 * domain record.  All mapping between the two types is performed inside
 * {@link AppUserAdapter} — this entity never escapes into the domain or
 * application layers.
 *
 * <p>Email is the natural business key and serves as the primary key; no surrogate
 * ID is needed.  The {@code enabled} flag supports soft-disabling accounts without
 * deleting the historical record.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-28
 * @updated 2026-05-28
 */
@Entity
@Table(name = "app_users")
public class AppUserEntity {

    @Id
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "role", nullable = false, length = 20)
    private String role = "USER";

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** Required no-arg constructor for JPA. */
    public AppUserEntity() {}

    public String getEmail()                        { return email; }
    public void setEmail(String email)              { this.email = email; }

    public String getPasswordHash()                 { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getDisplayName()                  { return displayName; }
    public void setDisplayName(String displayName)  { this.displayName = displayName; }

    public String getRole()                         { return role; }
    public void setRole(String role)                { this.role = role; }

    public boolean isEnabled()                      { return enabled; }
    public void setEnabled(boolean enabled)         { this.enabled = enabled; }
}
