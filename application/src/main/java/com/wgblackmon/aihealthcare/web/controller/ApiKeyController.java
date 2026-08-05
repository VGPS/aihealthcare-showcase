package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.exception.ApiKeyCreationException;
import com.wgblackmon.aihealthcare.domain.exception.ApiKeyNotFoundException;
import com.wgblackmon.aihealthcare.domain.model.ApiKey;
import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.infrastructure.config.ApiKeyAuthenticationFilter;
import com.wgblackmon.aihealthcare.web.dto.ApiKeyRequest;
import com.wgblackmon.aihealthcare.web.dto.ApiKeyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for managing API keys.
 *
 * <p>Provides endpoints to create, list, and delete API keys for the
 * authenticated user. Key creation is gated by subscription tier
 * (SUBSCRIBER/ENTERPRISE/ADMIN only) with per-tier key count limits.
 * Deletion requires ownership verification — only the key owner or
 * an ADMIN can delete a key.
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-07-03
 * @updated 2026-08-04
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/keys")
public class ApiKeyController {

    private static final int MAX_KEYS_SUBSCRIBER = 3;
    private static final int MAX_KEYS_ENTERPRISE = 10;

    private final ApiKeyPort apiKeyPort;
    private final AppUserPort appUserPort;

    public ApiKeyController(ApiKeyPort apiKeyPort, AppUserPort appUserPort) {
        log.debug("ApiKeyController() | apiKeyPort={}, appUserPort={}",
                  apiKeyPort.getClass().getSimpleName(), appUserPort.getClass().getSimpleName());
        this.apiKeyPort = apiKeyPort;
        this.appUserPort = appUserPort;
    }

    /**
     * Creates a new API key for the authenticated user.
     *
     * <p>Only SUBSCRIBER, ENTERPRISE, and ADMIN users can create keys.
     * Key count is limited per tier: SUBSCRIBER=3, ENTERPRISE=10, ADMIN=unlimited.
     *
     * @param request   the key creation request containing the key name
     * @param principal the authenticated user
     * @return 201 Created with the API key response (including raw key)
     */
    @PostMapping
    public ResponseEntity<ApiKeyResponse> createKey(@RequestBody ApiKeyRequest request,
                                                    Principal principal) {
        log.debug("createKey() | name={}, principal={}", request.name(),
                  principal != null ? principal.getName() : "null");

        if (principal == null) {
            log.debug("createKey() | return=401, no authenticated user");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("API key name must not be blank");
        }

        String ownerEmail = principal.getName();
        SubscriptionTier tier = resolveTier(ownerEmail);
        boolean isAdmin = isAdmin();

        if (!isAdmin && tier != SubscriptionTier.SUBSCRIBER && tier != SubscriptionTier.ENTERPRISE) {
            throw new ApiKeyCreationException(
                    "API key creation requires SUBSCRIBER or ENTERPRISE tier");
        }

        if (!isAdmin) {
            int maxKeys = tier == SubscriptionTier.ENTERPRISE ? MAX_KEYS_ENTERPRISE : MAX_KEYS_SUBSCRIBER;
            int currentCount = apiKeyPort.countByOwnerEmail(ownerEmail);
            if (currentCount >= maxKeys) {
                throw new ApiKeyCreationException(
                        "Maximum API key limit reached (" + maxKeys + " keys for " + tier.name() + " tier)");
            }
        }

        String rawKey = "aih_" + UUID.randomUUID().toString().replace("-", "");
        String keyHash = ApiKeyAuthenticationFilter.sha256(rawKey);
        String keyPrefix = rawKey.substring(0, 8);
        String id = UUID.randomUUID().toString();

        ApiKey apiKey = new ApiKey(id, ownerEmail, request.name(), keyPrefix,
                                  keyHash, true, Instant.now());
        apiKeyPort.save(apiKey);

        ApiKeyResponse result = new ApiKeyResponse(id, request.name(), keyPrefix,
                                                   rawKey, true, apiKey.createdAt());
        log.debug("createKey() | return=201, id={}", id);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * Lists all API keys owned by the authenticated user.
     *
     * @param principal the authenticated user
     * @return 200 OK with the list of API keys (without raw keys)
     */
    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> listKeys(Principal principal) {
        log.debug("listKeys() | principal={}", principal != null ? principal.getName() : "null");

        if (principal == null) {
            log.debug("listKeys() | return=401, no authenticated user");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String ownerEmail = principal.getName();
        List<ApiKey> keys = apiKeyPort.findAllByOwnerEmail(ownerEmail);

        List<ApiKeyResponse> result = new ArrayList<>();
        for (ApiKey key : keys) {
            result.add(new ApiKeyResponse(key.id(), key.name(), key.keyPrefix(),
                                          null, key.active(), key.createdAt()));
        }

        log.debug("listKeys() | return={} keys", result.size());
        return ResponseEntity.ok(result);
    }

    /**
     * Deletes an API key by ID. Only the key owner or an ADMIN can delete.
     *
     * @param id        the API key ID to delete
     * @param principal the authenticated user
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteKey(@PathVariable String id, Principal principal) {
        log.debug("deleteKey() | id={}, principal={}", id,
                  principal != null ? principal.getName() : "null");

        if (principal == null) {
            log.debug("deleteKey() | return=401, no authenticated user");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<ApiKey> keyOpt = apiKeyPort.findById(id);
        if (keyOpt.isEmpty()) {
            throw new ApiKeyNotFoundException(id);
        }

        ApiKey key = keyOpt.get();
        boolean isAdmin = isAdmin();
        if (!isAdmin && !key.ownerEmail().equals(principal.getName())) {
            log.debug("deleteKey() | return=403, ownership mismatch");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        apiKeyPort.deleteById(id);
        log.debug("deleteKey() | return=204");
        return ResponseEntity.noContent().build();
    }

    private SubscriptionTier resolveTier(String email) {
        Optional<AppUser> userOpt = appUserPort.findByEmail(email);
        if (userOpt.isPresent() && userOpt.get().tier() != null) {
            return userOpt.get().tier();
        }
        return SubscriptionTier.FREE;
    }

    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
