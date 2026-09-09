package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.RemoteAuthType;
import com.wgblackmon.aihealthcare.domain.model.RemoteConnection;
import com.wgblackmon.aihealthcare.domain.model.RemoteConnectionKind;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageRemoteConnectionsUseCase;
import com.wgblackmon.aihealthcare.web.dto.RemoteConnectionRequest;
import com.wgblackmon.aihealthcare.web.dto.RemoteConnectionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing customer-registered remote HTTPS connections.
 *
 * <p><strong>SECURITY:</strong> Response bodies never contain the
 * {@code secretRef} value. Only a {@code hasSecret} boolean is exposed.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/enterprise/connections")
public class EnterpriseConnectionRestController {

    private final ManageRemoteConnectionsUseCase useCase;

    public EnterpriseConnectionRestController(ManageRemoteConnectionsUseCase useCase) {
        log.debug("EnterpriseConnectionRestController() | useCase={}",
                useCase.getClass().getSimpleName());
        this.useCase = useCase;
    }

    @PostMapping
    public ResponseEntity<RemoteConnectionResponse> create(
            @RequestBody RemoteConnectionRequest body, Principal principal) {
        log.debug("create() | label={}, principal={}", body.label(), principal.getName());

        RemoteConnection connection = new RemoteConnection(
                UUID.randomUUID().toString(),
                principal.getName(),
                body.label(),
                parseKind(body.kind()),
                body.baseUrl(),
                parseAuthType(body.authType()),
                body.headerName(),
                body.secretRef(),
                body.active());

        RemoteConnection created = useCase.create(connection);
        RemoteConnectionResponse response = RemoteConnectionResponse.from(created);

        URI location = URI.create("/api/v1/enterprise/connections/" + created.connectionId());
        log.debug("create() | return=201, connectionId={}", created.connectionId());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{connectionId}")
    public ResponseEntity<RemoteConnectionResponse> get(
            @PathVariable String connectionId, Principal principal) {
        log.debug("get() | connectionId={}, principal={}", connectionId, principal.getName());
        try {
            RemoteConnection conn = useCase.get(connectionId, principal.getName());
            RemoteConnectionResponse result = RemoteConnectionResponse.from(conn);
            log.debug("get() | return={}", result.connectionId());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.debug("get() | return=404");
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<RemoteConnectionResponse>> list(Principal principal) {
        log.debug("list() | principal={}", principal.getName());
        List<RemoteConnection> connections = useCase.list(principal.getName());
        List<RemoteConnectionResponse> result = connections.stream()
                .map(RemoteConnectionResponse::from).toList();
        log.debug("list() | return={} connections", result.size());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{connectionId}")
    public ResponseEntity<RemoteConnectionResponse> update(
            @PathVariable String connectionId,
            @RequestBody RemoteConnectionRequest body,
            Principal principal) {
        log.debug("update() | connectionId={}, principal={}", connectionId, principal.getName());
        try {
            RemoteConnection updated = new RemoteConnection(
                    connectionId,
                    principal.getName(),
                    body.label(),
                    parseKind(body.kind()),
                    body.baseUrl(),
                    parseAuthType(body.authType()),
                    body.headerName(),
                    body.secretRef(),
                    body.active());

            RemoteConnection saved = useCase.update(connectionId, principal.getName(), updated);
            RemoteConnectionResponse result = RemoteConnectionResponse.from(saved);
            log.debug("update() | return={}", result.connectionId());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.debug("update() | return=404");
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{connectionId}")
    public ResponseEntity<Void> delete(@PathVariable String connectionId,
                                        Principal principal) {
        log.debug("delete() | connectionId={}, principal={}", connectionId, principal.getName());
        try {
            useCase.delete(connectionId, principal.getName());
            log.debug("delete() | return=204");
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.debug("delete() | return=404");
            return ResponseEntity.notFound().build();
        }
    }

    private RemoteConnectionKind parseKind(String kind) {
        if (kind == null || kind.isBlank()) return RemoteConnectionKind.HTTPS_JSON;
        return RemoteConnectionKind.valueOf(kind.toUpperCase());
    }

    private RemoteAuthType parseAuthType(String authType) {
        if (authType == null || authType.isBlank()) return RemoteAuthType.NONE;
        return RemoteAuthType.valueOf(authType.toUpperCase());
    }
}
