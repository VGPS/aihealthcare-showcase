package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.SsoIdentityProvider;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageSsoProvidersUseCase;
import com.wgblackmon.aihealthcare.web.dto.SsoProviderRequest;
import com.wgblackmon.aihealthcare.web.dto.SsoProviderResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * REST controller for SSO Identity Provider management.
 *
 * <p>All endpoints require ADMIN role (enforced by SecurityConfig
 * via {@code /api/**} authenticated + method-level checks).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sso/providers")
public class SsoRestController {

    private final ManageSsoProvidersUseCase ssoUseCase;

    public SsoRestController(ManageSsoProvidersUseCase ssoUseCase) {
        log.debug("SsoRestController() | ssoUseCase={}", ssoUseCase.getClass().getSimpleName());
        this.ssoUseCase = ssoUseCase;
    }

    @GetMapping
    public ResponseEntity<List<SsoProviderResponse>> listProviders() {
        log.debug("listProviders() | (no args)");
        List<SsoProviderResponse> result = ssoUseCase.getAllActive().stream()
                .map(SsoProviderResponse::from)
                .toList();
        log.debug("listProviders() | return=size={}", result.size());
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<SsoProviderResponse> createProvider(@RequestBody SsoProviderRequest request) {
        log.debug("createProvider() | registrationId={}", request.registrationId());

        Instant now = Instant.now();
        SsoIdentityProvider provider = new SsoIdentityProvider(
                request.registrationId(), request.label(),
                request.entityId(), request.ssoUrl(), request.certificate(),
                request.metadataUrl(), request.emailAttribute(), request.displayNameAttribute(),
                SubscriptionTier.ENTERPRISE, request.active(), now, now);

        SsoIdentityProvider created = ssoUseCase.create(provider);
        SsoProviderResponse result = SsoProviderResponse.from(created);
        log.debug("createProvider() | return={}", result.registrationId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SsoProviderResponse> updateProvider(@PathVariable String id,
                                                               @RequestBody SsoProviderRequest request) {
        log.debug("updateProvider() | id={}", id);

        SsoIdentityProvider existing = ssoUseCase.getById(id)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + id));

        SsoIdentityProvider updated = new SsoIdentityProvider(
                id, request.label(), request.entityId(), request.ssoUrl(),
                request.certificate(), request.metadataUrl(),
                request.emailAttribute(), request.displayNameAttribute(),
                SubscriptionTier.ENTERPRISE, request.active(),
                existing.createdAt(), Instant.now());

        SsoIdentityProvider saved = ssoUseCase.update(updated);
        SsoProviderResponse result = SsoProviderResponse.from(saved);
        log.debug("updateProvider() | return={}", result.registrationId());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProvider(@PathVariable String id) {
        log.debug("deleteProvider() | id={}", id);
        ssoUseCase.delete(id);
        log.debug("deleteProvider() | return=204");
        return ResponseEntity.noContent().build();
    }
}
