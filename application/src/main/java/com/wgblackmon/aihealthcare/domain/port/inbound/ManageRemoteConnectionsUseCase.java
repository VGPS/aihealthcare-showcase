package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.RemoteConnection;

import java.util.List;

/**
 * Inbound port for CRUD operations on customer-registered remote
 * HTTPS connections.
 *
 * <p>Every operation is scoped to the owner's email — a connection
 * belonging to another account is invisible (not access-denied).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public interface ManageRemoteConnectionsUseCase {

    RemoteConnection create(RemoteConnection connection);

    RemoteConnection get(String connectionId, String ownerEmail);

    List<RemoteConnection> list(String ownerEmail);

    RemoteConnection update(String connectionId, String ownerEmail, RemoteConnection updated);

    void delete(String connectionId, String ownerEmail);
}
