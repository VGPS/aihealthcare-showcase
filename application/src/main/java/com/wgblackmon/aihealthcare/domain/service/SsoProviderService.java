package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.SsoIdentityProvider;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageSsoProvidersUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.SsoIdentityProviderPort;

import java.util.List;
import java.util.Optional;

/**
 * Domain service for SSO Identity Provider CRUD operations.
 *
 * <p>Validates registrationId uniqueness on creation and delegates
 * persistence to {@link SsoIdentityProviderPort}. This class carries
 * no Spring annotations; it is wired as a {@code @Bean} in AppConfig.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
public class SsoProviderService implements ManageSsoProvidersUseCase {

    private static final DomainLogger log = new DomainLogger(SsoProviderService.class);

    private final SsoIdentityProviderPort providerPort;

    public SsoProviderService(SsoIdentityProviderPort providerPort) {
        log.debug("SsoProviderService() | providerPort={}", providerPort.getClass().getSimpleName());
        this.providerPort = providerPort;
    }

    @Override
    public SsoIdentityProvider create(SsoIdentityProvider provider) {
        log.debug("create() | registrationId={}", provider.registrationId());

        if (providerPort.existsById(provider.registrationId())) {
            throw new IllegalArgumentException(
                    "SSO provider already exists: " + provider.registrationId());
        }

        SsoIdentityProvider result = providerPort.save(provider);
        log.debug("create() | return={}", result.registrationId());
        return result;
    }

    @Override
    public SsoIdentityProvider update(SsoIdentityProvider provider) {
        log.debug("update() | registrationId={}", provider.registrationId());

        if (!providerPort.existsById(provider.registrationId())) {
            throw new IllegalArgumentException(
                    "SSO provider not found: " + provider.registrationId());
        }

        SsoIdentityProvider result = providerPort.save(provider);
        log.debug("update() | return={}", result.registrationId());
        return result;
    }

    @Override
    public void delete(String registrationId) {
        log.debug("delete() | registrationId={}", registrationId);

        if (!providerPort.existsById(registrationId)) {
            throw new IllegalArgumentException(
                    "SSO provider not found: " + registrationId);
        }

        providerPort.deleteById(registrationId);
        log.debug("delete() | return=void");
    }

    @Override
    public Optional<SsoIdentityProvider> getById(String registrationId) {
        log.debug("getById() | registrationId={}", registrationId);
        Optional<SsoIdentityProvider> result = providerPort.findById(registrationId);
        log.debug("getById() | return={}", result.isPresent() ? result.get().registrationId() : "empty");
        return result;
    }

    @Override
    public List<SsoIdentityProvider> getAll() {
        log.debug("getAll()");
        List<SsoIdentityProvider> result = providerPort.findAll();
        log.debug("getAll() | return=size={}", result.size());
        return result;
    }

    @Override
    public List<SsoIdentityProvider> getAllActive() {
        log.debug("getAllActive()");
        List<SsoIdentityProvider> result = providerPort.findAllActive();
        log.debug("getAllActive() | return=size={}", result.size());
        return result;
    }
}
