package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link StateLawSourceEntity}.
 *
 * <p>Sources are child records of a {@link StateLawEntity}, linked by
 * the {@code lawId} string column. The {@link #deleteByLawId(String)}
 * method supports the replace-all-sources pattern used during upsert.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
public interface StateLawSourceRepository extends JpaRepository<StateLawSourceEntity, Long> {

    List<StateLawSourceEntity> findByLawId(String lawId);

    java.util.Optional<StateLawSourceEntity> findByLawIdAndUrl(String lawId, String url);

    void deleteByLawId(String lawId);
}
