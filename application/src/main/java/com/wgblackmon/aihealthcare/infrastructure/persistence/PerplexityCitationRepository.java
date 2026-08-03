package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link PerplexityCitationEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-02
 * @updated 2026-08-02
 */
public interface PerplexityCitationRepository
        extends JpaRepository<PerplexityCitationEntity, String> {

    List<PerplexityCitationEntity> findByCompanyId(String companyId);
}
