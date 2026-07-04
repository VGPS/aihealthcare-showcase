package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA slice tests for {@link WikiContradictionRepository}.
 *
 * <p>Verifies save, retrieval, and time-based filtering of contradiction records.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
@DataJpaTest
class WikiContradictionRepositoryTest {

    @Autowired
    private WikiContradictionRepository repository;

    private WikiContradictionEntity createContradiction(String slug, Instant detectedAt) {
        WikiContradictionEntity entity = new WikiContradictionEntity();
        entity.setPageSlug(slug);
        entity.setPriorClaim("All AI devices require premarket review");
        entity.setNewClaim("Low-risk AI devices exempt from review");
        entity.setPriorSourceIds("article-001");
        entity.setNewSourceIds("article-042");
        entity.setDetectedAt(detectedAt);
        return entity;
    }

    @Test
    void save_andRetrieve_roundTrip() {
        Instant now = Instant.parse("2026-07-04T12:00:00Z");
        WikiContradictionEntity entity = createContradiction("fda-ai-guidance", now);
        repository.save(entity);

        List<WikiContradictionEntity> all = repository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getPageSlug()).isEqualTo("fda-ai-guidance");
        assertThat(all.get(0).getPriorClaim()).isEqualTo("All AI devices require premarket review");
        assertThat(all.get(0).getNewClaim()).isEqualTo("Low-risk AI devices exempt from review");
    }

    @Test
    void findByDetectedAtAfter_filtersCorrectly() {
        Instant old = Instant.parse("2026-06-01T00:00:00Z");
        Instant recent = Instant.parse("2026-07-03T00:00:00Z");
        Instant cutoff = Instant.parse("2026-07-01T00:00:00Z");

        repository.save(createContradiction("old-page", old));
        repository.save(createContradiction("recent-page", recent));

        List<WikiContradictionEntity> results =
                repository.findByDetectedAtAfterOrderByDetectedAtDesc(cutoff);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getPageSlug()).isEqualTo("recent-page");
    }

    @Test
    void findByDetectedAtAfter_noMatches_returnsEmpty() {
        Instant future = Instant.parse("2099-01-01T00:00:00Z");
        repository.save(createContradiction("fda-ai-guidance", Instant.parse("2026-07-04T00:00:00Z")));

        List<WikiContradictionEntity> results =
                repository.findByDetectedAtAfterOrderByDetectedAtDesc(future);
        assertThat(results).isEmpty();
    }

    @Test
    void pipeDelimitedSourceIds_persistCorrectly() {
        WikiContradictionEntity entity = new WikiContradictionEntity();
        entity.setPageSlug("multi-source");
        entity.setPriorClaim("Claim A");
        entity.setNewClaim("Claim B");
        entity.setPriorSourceIds("article-001|article-002|article-003");
        entity.setNewSourceIds("article-010|article-011");
        entity.setDetectedAt(Instant.now());
        repository.save(entity);

        List<WikiContradictionEntity> all = repository.findAll();
        assertThat(all.get(0).getPriorSourceIds()).isEqualTo("article-001|article-002|article-003");
        assertThat(all.get(0).getNewSourceIds()).isEqualTo("article-010|article-011");
    }
}
