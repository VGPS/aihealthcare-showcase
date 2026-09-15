package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.SsoIdentityProvider;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port for managing SSO Identity Provider configurations.
 *
 * <p>Supports full CRUD for IdP registrations. Only ADMIN-role users
 * should be allowed to invoke these operations (enforced at the web layer).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
public interface ManageSsoProvidersUseCase {

    SsoIdentityProvider create(SsoIdentityProvider provider);

    SsoIdentityProvider update(SsoIdentityProvider provider);

    void delete(String registrationId);

    Optional<SsoIdentityProvider> getById(String registrationId);

    List<SsoIdentityProvider> getAll();

    List<SsoIdentityProvider> getAllActive();
}
