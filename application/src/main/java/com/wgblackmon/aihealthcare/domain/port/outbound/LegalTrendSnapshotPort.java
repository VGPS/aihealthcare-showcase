package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.LegalTrendSnapshot;

import java.util.Optional;

/**
 * Outbound port for persisting and querying legal trend snapshots.
 *
 * <p>Implementations live in {@code infrastructure/persistence}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-07-30
 */
public interface LegalTrendSnapshotPort {

    /**
     * Persists a legal trend snapshot.
     */
    void save(LegalTrendSnapshot snapshot);

    /**
     * Returns the most recently saved snapshot.
     */
    Optional<LegalTrendSnapshot> findLatest();
}
