package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link EnterpriseDataPromptEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public interface EnterpriseDataPromptRepository extends JpaRepository<EnterpriseDataPromptEntity, String> {

    List<EnterpriseDataPromptEntity> findByActiveTrue();

    List<EnterpriseDataPromptEntity> findByFeedIdAndActiveTrue(String feedId);
}
