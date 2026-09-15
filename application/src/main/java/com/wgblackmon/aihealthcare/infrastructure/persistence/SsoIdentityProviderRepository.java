package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link SsoIdentityProviderEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
public interface SsoIdentityProviderRepository
        extends JpaRepository<SsoIdentityProviderEntity, String> {

    List<SsoIdentityProviderEntity> findAllByActiveTrue();
}
