package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for {@link RegulatoryEventEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-29
 */
public interface RegulatoryEventRepository extends JpaRepository<RegulatoryEventEntity, String> {

    boolean existsByReferenceNumber(String referenceNumber);

    boolean existsBySourceUrl(String sourceUrl);

    List<RegulatoryEventEntity> findAllByOrderByDiscoveredAtDesc();

    List<RegulatoryEventEntity> findByEventTypeOrderByDiscoveredAtDesc(String eventType);

    List<RegulatoryEventEntity> findByRegulatoryBodyOrderByDiscoveredAtDesc(String regulatoryBody);

    List<RegulatoryEventEntity> findByDiscoveredAtAfterOrderByDiscoveredAtDesc(Instant since);

    @Query("SELECT e FROM RegulatoryEventEntity e WHERE " +
           "LOWER(e.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(e.summary) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(e.applicantName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(e.deviceName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "ORDER BY e.discoveredAt DESC")
    List<RegulatoryEventEntity> findByKeyword(@Param("keyword") String keyword);
}
