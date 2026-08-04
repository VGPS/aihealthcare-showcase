package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA repository for the singleton {@link NewsletterSendSettingsEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public interface NewsletterSendSettingsRepository
        extends JpaRepository<NewsletterSendSettingsEntity, Long> {
}
