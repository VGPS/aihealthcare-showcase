package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.RemoteAuthType;
import com.wgblackmon.aihealthcare.domain.model.RemoteConnection;
import com.wgblackmon.aihealthcare.domain.model.RemoteConnectionKind;
import com.wgblackmon.aihealthcare.domain.port.outbound.RemoteConnectionPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed adapter implementing {@link RemoteConnectionPort}.
 *
 * <p>Ownership is enforced in the query — every lookup includes
 * {@code ownerEmail} so cross-tenant reads are structurally impossible.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Slf4j
@Component
public class RemoteConnectionAdapter implements RemoteConnectionPort {

    private final EnterpriseRemoteConnectionRepository repository;

    public RemoteConnectionAdapter(EnterpriseRemoteConnectionRepository repository) {
        this.repository = repository;
        log.debug("RemoteConnectionAdapter() | repository={}", repository.getClass().getSimpleName());
    }

    @Override
    public Optional<RemoteConnection> findByConnectionIdAndOwnerEmail(String connectionId,
                                                                       String ownerEmail) {
        log.debug("findByConnectionIdAndOwnerEmail() | connectionId={}, ownerEmail={}",
                connectionId, ownerEmail);
        Optional<RemoteConnection> result = repository
                .findByConnectionIdAndOwnerEmail(connectionId, ownerEmail)
                .map(this::toDomain);
        log.debug("findByConnectionIdAndOwnerEmail() | return={}", result.isPresent());
        return result;
    }

    @Override
    public List<RemoteConnection> findByOwnerEmail(String ownerEmail) {
        log.debug("findByOwnerEmail() | ownerEmail={}", ownerEmail);
        List<RemoteConnection> result = repository
                .findByOwnerEmailOrderByCreatedAtDesc(ownerEmail)
                .stream().map(this::toDomain).toList();
        log.debug("findByOwnerEmail() | return={} connections", result.size());
        return result;
    }

    @Override
    @Transactional
    public RemoteConnection save(RemoteConnection connection) {
        log.debug("save() | connectionId={}", connection.connectionId());
        EnterpriseRemoteConnectionEntity entity = toEntity(connection);
        EnterpriseRemoteConnectionEntity saved = repository.save(entity);
        RemoteConnection result = toDomain(saved);
        log.debug("save() | return={}", result.connectionId());
        return result;
    }

    @Override
    @Transactional
    public void delete(String connectionId, String ownerEmail) {
        log.debug("delete() | connectionId={}, ownerEmail={}", connectionId, ownerEmail);
        repository.findByConnectionIdAndOwnerEmail(connectionId, ownerEmail)
                .ifPresent(repository::delete);
        log.debug("delete() | return=void");
    }

    // ---- mapping helpers ----

    private EnterpriseRemoteConnectionEntity toEntity(RemoteConnection c) {
        EnterpriseRemoteConnectionEntity e = new EnterpriseRemoteConnectionEntity();
        e.setConnectionId(c.connectionId());
        e.setOwnerEmail(c.ownerEmail());
        e.setLabel(c.label());
        e.setKind(c.kind().name());
        e.setBaseUrl(c.baseUrl());
        e.setAuthType(c.authType().name());
        e.setHeaderName(c.headerName());
        e.setSecretRef(c.secretRef());
        e.setActive(c.active());
        Instant now = Instant.now();
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }

    private RemoteConnection toDomain(EnterpriseRemoteConnectionEntity e) {
        return new RemoteConnection(
                e.getConnectionId(),
                e.getOwnerEmail(),
                e.getLabel(),
                RemoteConnectionKind.valueOf(e.getKind()),
                e.getBaseUrl(),
                RemoteAuthType.valueOf(e.getAuthType()),
                e.getHeaderName(),
                e.getSecretRef(),
                e.isActive()
        );
    }
}
