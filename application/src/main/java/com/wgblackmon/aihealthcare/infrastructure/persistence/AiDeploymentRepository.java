package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link AiDeploymentEntity}.
 *
 * @author  Bill Blackmon
 * @since   2026-08-16
 * @updated 2026-08-16
 */
public interface AiDeploymentRepository extends JpaRepository<AiDeploymentEntity, String> {

    List<AiDeploymentEntity> findByHealthSystemId(String healthSystemId);

    List<AiDeploymentEntity> findByDomain(String domain);

    List<AiDeploymentEntity> findByHealthSystemIdAndDomain(String healthSystemId, String domain);
}
