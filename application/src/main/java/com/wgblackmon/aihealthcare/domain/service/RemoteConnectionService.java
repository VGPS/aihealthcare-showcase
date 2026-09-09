package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.RemoteConnection;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageRemoteConnectionsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.RemoteConnectionPort;

import java.util.List;
import java.util.logging.Logger;

/**
 * Domain service implementing remote connection management.
 *
 * <p>Delegates to {@link RemoteConnectionPort} with ownership enforcement.
 * A connection belonging to another account is treated as not found.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public class RemoteConnectionService implements ManageRemoteConnectionsUseCase {

    private static final Logger LOG = Logger.getLogger(RemoteConnectionService.class.getName());

    private final RemoteConnectionPort connectionPort;

    public RemoteConnectionService(RemoteConnectionPort connectionPort) {
        LOG.fine("RemoteConnectionService() | connectionPort=" + connectionPort.getClass().getSimpleName());
        this.connectionPort = connectionPort;
    }

    @Override
    public RemoteConnection create(RemoteConnection connection) {
        LOG.fine("create() | connectionId=" + connection.connectionId()
                + ", ownerEmail=" + connection.ownerEmail());
        RemoteConnection result = connectionPort.save(connection);
        LOG.fine("create() | return=" + result.connectionId());
        return result;
    }

    @Override
    public RemoteConnection get(String connectionId, String ownerEmail) {
        LOG.fine("get() | connectionId=" + connectionId + ", ownerEmail=" + ownerEmail);
        RemoteConnection result = connectionPort.findByConnectionIdAndOwnerEmail(connectionId, ownerEmail)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Connection not found: " + connectionId));
        LOG.fine("get() | return=" + result.connectionId());
        return result;
    }

    @Override
    public List<RemoteConnection> list(String ownerEmail) {
        LOG.fine("list() | ownerEmail=" + ownerEmail);
        List<RemoteConnection> result = connectionPort.findByOwnerEmail(ownerEmail);
        LOG.fine("list() | return=" + result.size() + " connections");
        return result;
    }

    @Override
    public RemoteConnection update(String connectionId, String ownerEmail,
                                    RemoteConnection updated) {
        LOG.fine("update() | connectionId=" + connectionId + ", ownerEmail=" + ownerEmail);
        connectionPort.findByConnectionIdAndOwnerEmail(connectionId, ownerEmail)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Connection not found: " + connectionId));
        RemoteConnection toSave = new RemoteConnection(
                connectionId,
                ownerEmail,
                updated.label(),
                updated.kind(),
                updated.baseUrl(),
                updated.authType(),
                updated.headerName(),
                updated.secretRef(),
                updated.active());
        RemoteConnection result = connectionPort.save(toSave);
        LOG.fine("update() | return=" + result.connectionId());
        return result;
    }

    @Override
    public void delete(String connectionId, String ownerEmail) {
        LOG.fine("delete() | connectionId=" + connectionId + ", ownerEmail=" + ownerEmail);
        connectionPort.findByConnectionIdAndOwnerEmail(connectionId, ownerEmail)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Connection not found: " + connectionId));
        connectionPort.delete(connectionId, ownerEmail);
        LOG.fine("delete() | return=void");
    }
}
