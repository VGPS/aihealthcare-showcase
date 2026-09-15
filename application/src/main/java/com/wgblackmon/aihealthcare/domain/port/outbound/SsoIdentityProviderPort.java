package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.SsoIdentityProvider;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying SSO Identity Provider configurations.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
public interface SsoIdentityProviderPort {

    SsoIdentityProvider save(SsoIdentityProvider provider);

    Optional<SsoIdentityProvider> findById(String registrationId);

    List<SsoIdentityProvider> findAll();

    List<SsoIdentityProvider> findAllActive();

    void deleteById(String registrationId);

    boolean existsById(String registrationId);
}
