package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link SsoProvisioningEventEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
public interface SsoProvisioningEventRepository
        extends JpaRepository<SsoProvisioningEventEntity, String> {

    List<SsoProvisioningEventEntity> findByEmail(String email);

    List<SsoProvisioningEventEntity> findByRegistrationId(String registrationId);
}
