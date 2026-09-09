package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.RemoteConnection;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for customer-registered remote HTTPS connections.
 *
 * <p>Every read method scopes to the owner email — a connection belonging
 * to another account is invisible, not access-denied.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public interface RemoteConnectionPort {

    Optional<RemoteConnection> findByConnectionIdAndOwnerEmail(String connectionId, String ownerEmail);

    List<RemoteConnection> findByOwnerEmail(String ownerEmail);

    RemoteConnection save(RemoteConnection connection);

    void delete(String connectionId, String ownerEmail);
}
