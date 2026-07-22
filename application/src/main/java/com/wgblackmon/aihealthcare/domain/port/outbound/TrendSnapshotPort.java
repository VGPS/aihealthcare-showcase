package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying trend detection snapshots.
 *
 * <p>Implementations store serialized {@link TrendSnapshot} records in the
 * persistence layer.  Each snapshot contains the rising, fading, and new
 * keyword signals computed at a point in time.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public interface TrendSnapshotPort {

    /**
     * Persists a trend snapshot.
     *
     * @param snapshot the snapshot to save
     */
    void save(TrendSnapshot snapshot);

    /**
     * Returns the most recently generated snapshot.
     *
     * @return the latest snapshot, or empty if none exists
     */
    Optional<TrendSnapshot> findLatest();

    /**
     * Returns all stored snapshots, ordered by generation time descending.
     *
     * @return list of all snapshots; empty if none exist
     */
    List<TrendSnapshot> findAll();
}
