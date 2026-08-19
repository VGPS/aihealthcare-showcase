package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for {@link PrivateFundingRoundEntity}.
 *
 * <p>Supports querying by announcement date and optional peer group filter.
 * The custom JPQL query is used to avoid overloading the method name
 * with a nullable filter parameter.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public interface PrivateFundingRoundJpaRepository extends JpaRepository<PrivateFundingRoundEntity, Long> {

    @Query("SELECT r FROM PrivateFundingRoundEntity r WHERE r.announcedAt >= :since " +
           "AND (:peerGroup IS NULL OR r.peerGroup = :peerGroup) " +
           "ORDER BY r.announcedAt DESC")
    List<PrivateFundingRoundEntity> findBySinceAndPeerGroup(
            @Param("since") Instant since,
            @Param("peerGroup") String peerGroup);
}
