package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link HealthSystemEntity}.
 *
 * @author  Bill Blackmon
 * @since   2026-08-16
 * @updated 2026-08-16
 */
public interface HealthSystemRepository extends JpaRepository<HealthSystemEntity, String> {

    List<HealthSystemEntity> findAllByOrderByCanonicalNameAsc();

    List<HealthSystemEntity> findBySystemType(String systemType);
}
