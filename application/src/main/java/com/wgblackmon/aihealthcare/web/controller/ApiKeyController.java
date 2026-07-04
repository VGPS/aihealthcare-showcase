package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.exception.ApiKeyNotFoundException;
import com.wgblackmon.aihealthcare.domain.model.ApiKey;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.ApiKeyAuthenticationFilter;
import com.wgblackmon.aihealthcare.web.dto.ApiKeyRequest;
import com.wgblackmon.aihealthcare.web.dto.ApiKeyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import java.util.UUID;

/**
 * REST controller for managing API keys.
 *
 * <p>Provides endpoints to create, list, and delete API keys for the
 * authenticated user. The raw key is only returned on creation — it is
 * SHA-256 hashed before storage and cannot be retrieved afterwards.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-03
 * @updated 2026-07-03
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/keys")
public class ApiKeyController {

    private final ApiKeyPort apiKeyPort;

    public ApiKeyController(ApiKeyPort apiKeyPort) {
        log.debug("ApiKeyController() | apiKeyPort={}", apiKeyPort.getClass().getSimpleName());
        this.apiKeyPort = apiKeyPort;
    }

    /**
     * Creates a new API key for the authenticated user.
     *
     * @param request   the key creation request containing the key name
     * @param principal the authenticated user
     * @return 201 Created with the API key response (including raw key)
     */
    @PostMapping
    public ResponseEntity<ApiKeyResponse> createKey(@RequestBody ApiKeyRequest request,
                                                    Principal principal) {
        log.debug("createKey() | name={}, principal={}", request.name(),
                  principal != null ? principal.getName() : "anonymous");

        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("API key name must not be blank");
        }

        String ownerEmail = principal != null ? principal.getName() : "anonymous";
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
        log.debug("listKeys() | principal={}", principal != null ? principal.getName() : "anonymous");

        String ownerEmail = principal != null ? principal.getName() : "anonymous";
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
     * Deletes an API key by ID.
     *
     * @param id the API key ID to delete
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteKey(@PathVariable String id) {
        log.debug("deleteKey() | id={}", id);

        if (!apiKeyPort.existsById(id)) {
            throw new ApiKeyNotFoundException(id);
        }

        apiKeyPort.deleteById(id);
        log.debug("deleteKey() | return=204");
        return ResponseEntity.noContent().build();
    }
}
