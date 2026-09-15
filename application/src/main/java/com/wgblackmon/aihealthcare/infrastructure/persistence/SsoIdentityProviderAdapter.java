package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.SsoIdentityProvider;
import com.wgblackmon.aihealthcare.domain.model.SsoProvisioningAction;
import com.wgblackmon.aihealthcare.domain.model.SsoProvisioningEvent;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.SsoIdentityProviderPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SsoProvisioningEventPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Persistence adapter implementing both {@link SsoIdentityProviderPort}
 * and {@link SsoProvisioningEventPort}.
 *
 * <p>Maps between domain records and JPA entities for SSO identity
 * provider configurations and provisioning audit events.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
@Slf4j
@Component
public class SsoIdentityProviderAdapter
        implements SsoIdentityProviderPort, SsoProvisioningEventPort {

    private final SsoIdentityProviderRepository providerRepo;
    private final SsoProvisioningEventRepository eventRepo;

    public SsoIdentityProviderAdapter(SsoIdentityProviderRepository providerRepo,
                                      SsoProvisioningEventRepository eventRepo) {
        log.debug("SsoIdentityProviderAdapter() | providerRepo={}, eventRepo={}",
                  providerRepo.getClass().getSimpleName(), eventRepo.getClass().getSimpleName());
        this.providerRepo = providerRepo;
        this.eventRepo = eventRepo;
    }

    @Override
    public SsoIdentityProvider save(SsoIdentityProvider provider) {
        log.debug("save() | registrationId={}", provider.registrationId());
        SsoIdentityProviderEntity entity = toEntity(provider);
        SsoIdentityProviderEntity saved = providerRepo.save(entity);
        SsoIdentityProvider result = toDomain(saved);
        log.debug("save() | return={}", result.registrationId());
        return result;
    }

    @Override
    public Optional<SsoIdentityProvider> findById(String registrationId) {
        log.debug("findById() | registrationId={}", registrationId);
        Optional<SsoIdentityProvider> result = providerRepo.findById(registrationId).map(this::toDomain);
        log.debug("findById() | return={}", result.isPresent() ? result.get().registrationId() : "empty");
        return result;
    }

    @Override
    public List<SsoIdentityProvider> findAll() {
        log.debug("findAll()");
        List<SsoIdentityProvider> result = providerRepo.findAll().stream()
                .map(this::toDomain).toList();
        log.debug("findAll() | return=size={}", result.size());
        return result;
    }

    @Override
    public List<SsoIdentityProvider> findAllActive() {
        log.debug("findAllActive()");
        List<SsoIdentityProvider> result = providerRepo.findAllByActiveTrue().stream()
                .map(this::toDomain).toList();
        log.debug("findAllActive() | return=size={}", result.size());
        return result;
    }

    @Override
    public void deleteById(String registrationId) {
        log.debug("deleteById() | registrationId={}", registrationId);
        providerRepo.deleteById(registrationId);
        log.debug("deleteById() | return=void");
    }

    @Override
    public boolean existsById(String registrationId) {
        log.debug("existsById() | registrationId={}", registrationId);
        boolean result = providerRepo.existsById(registrationId);
        log.debug("existsById() | return={}", result);
        return result;
    }

    @Override
    public void record(SsoProvisioningEvent event) {
        log.debug("record() | eventId={}, action={}", event.eventId(), event.action());
        SsoProvisioningEventEntity entity = toEventEntity(event);
        eventRepo.save(entity);
        log.debug("record() | return=void");
    }

    @Override
    public List<SsoProvisioningEvent> findByEmail(String email) {
        log.debug("findByEmail() | email={}", email);
        List<SsoProvisioningEvent> result = eventRepo.findByEmail(email).stream()
                .map(this::toEventDomain).toList();
        log.debug("findByEmail() | return=size={}", result.size());
        return result;
    }

    @Override
    public List<SsoProvisioningEvent> findByRegistrationId(String registrationId) {
        log.debug("findByRegistrationId() | registrationId={}", registrationId);
        List<SsoProvisioningEvent> result = eventRepo.findByRegistrationId(registrationId).stream()
                .map(this::toEventDomain).toList();
        log.debug("findByRegistrationId() | return=size={}", result.size());
        return result;
    }

    private SsoIdentityProviderEntity toEntity(SsoIdentityProvider domain) {
        SsoIdentityProviderEntity entity = new SsoIdentityProviderEntity();
        entity.setRegistrationId(domain.registrationId());
        entity.setLabel(domain.label());
        entity.setEntityId(domain.entityId());
        entity.setSsoUrl(domain.ssoUrl());
        entity.setCertificate(domain.certificate());
        entity.setMetadataUrl(domain.metadataUrl());
        entity.setEmailAttribute(domain.emailAttribute());
        entity.setDisplayNameAttribute(domain.displayNameAttribute());
        entity.setDefaultTier(domain.defaultTier().name());
        entity.setActive(domain.active());
        entity.setCreatedAt(domain.createdAt());
        entity.setUpdatedAt(domain.updatedAt());
        return entity;
    }

    private SsoIdentityProvider toDomain(SsoIdentityProviderEntity entity) {
        return new SsoIdentityProvider(
                entity.getRegistrationId(),
                entity.getLabel(),
                entity.getEntityId(),
                entity.getSsoUrl(),
                entity.getCertificate(),
                entity.getMetadataUrl(),
                entity.getEmailAttribute(),
                entity.getDisplayNameAttribute(),
                SubscriptionTier.valueOf(entity.getDefaultTier()),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private SsoProvisioningEventEntity toEventEntity(SsoProvisioningEvent domain) {
        SsoProvisioningEventEntity entity = new SsoProvisioningEventEntity();
        entity.setEventId(domain.eventId());
        entity.setRegistrationId(domain.registrationId());
        entity.setEmail(domain.email());
        entity.setAction(domain.action().name());
        entity.setOccurredAt(domain.occurredAt());
        return entity;
    }

    private SsoProvisioningEvent toEventDomain(SsoProvisioningEventEntity entity) {
        return new SsoProvisioningEvent(
                entity.getEventId(),
                entity.getRegistrationId(),
                entity.getEmail(),
                SsoProvisioningAction.valueOf(entity.getAction()),
                entity.getOccurredAt());
    }
}
