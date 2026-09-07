package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data JPA repository for {@link StateLawEntity}.
 *
 * <p>Provides derived query methods for filtering laws by state code,
 * status, and category (pipe-delimited substring match), as well as
 * a JPQL full-text search across multiple columns.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
public interface StateLawRepository extends JpaRepository<StateLawEntity, String> {

    List<StateLawEntity> findByStateCode(String stateCode);

    List<StateLawEntity> findByStatus(String status);

    List<StateLawEntity> findByCategoriesContaining(String category);

    @Query("SELECT e FROM StateLawEntity e WHERE " +
           "LOWER(e.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(e.billNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(e.regulatedParties) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<StateLawEntity> search(@Param("query") String query);
}
