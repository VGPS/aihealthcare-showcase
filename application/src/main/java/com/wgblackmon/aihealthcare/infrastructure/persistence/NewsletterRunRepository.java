package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link NewsletterRunEntity}.
 *
 * <p>Inherits {@code findById}, {@code save}, and {@code findAll} from
 * {@link JpaRepository} — no custom query methods are needed for Slice 2a.
 * The primary key is {@code String runId} which corresponds to the
 * {@code draftId} supplied to the newsletter generation use case.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-11
 * @updated 2026-04-11
 */
public interface NewsletterRunRepository extends JpaRepository<NewsletterRunEntity, String> {
}
