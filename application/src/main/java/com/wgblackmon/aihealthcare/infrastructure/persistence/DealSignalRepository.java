package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link DealSignalEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-06
 */
public interface DealSignalRepository extends JpaRepository<DealSignalEntity, String> {

    List<DealSignalEntity> findAllByOrderByDetectedAtDesc(Pageable pageable);

    boolean existsByArticleId(String articleId);

    List<DealSignalEntity> findBySignalTypeOrderByDetectedAtDesc(String signalType, Pageable pageable);
}
