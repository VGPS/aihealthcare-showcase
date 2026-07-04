package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA slice tests for {@link WikiPageRepository}.
 *
 * <p>Uses {@code @DataJpaTest} which loads only the JPA slice (H2, repositories,
 * entity scanning) without the full Spring context.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
@DataJpaTest
class WikiPageRepositoryTest {

    @Autowired
    private WikiPageRepository repository;

    private static final Instant NOW = Instant.parse("2026-07-04T00:00:00Z");

    private WikiPageEntity createPage(String slug, String title, String pageType, String tags) {
        WikiPageEntity entity = new WikiPageEntity();
        entity.setSlug(slug);
        entity.setTitle(title);
        entity.setPageType(pageType);
        entity.setTags(tags);
        entity.setContentMarkdown("# " + title + "\nSome content.");
        entity.setRelatedSlugs("");
        entity.setCreatedAt(NOW);
        entity.setRevision(1);
        return entity;
    }

    @Test
    void save_andFindById_roundTrip() {
        WikiPageEntity page = createPage("fda-ai-guidance", "FDA AI Guidance", "ENTITY", "fda|regulation");
        repository.save(page);

        Optional<WikiPageEntity> found = repository.findById("fda-ai-guidance");
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("FDA AI Guidance");
        assertThat(found.get().getPageType()).isEqualTo("ENTITY");
        assertThat(found.get().getRevision()).isEqualTo(1);
    }

    @Test
    void findById_missingSlug_returnsEmpty() {
        Optional<WikiPageEntity> found = repository.findById("nonexistent-slug");
        assertThat(found).isEmpty();
    }

    @Test
    void updateRevision_incrementsCorrectly() {
        WikiPageEntity page = createPage("fda-ai-guidance", "FDA AI Guidance", "ENTITY", "fda");
        repository.save(page);

        page.setRevision(2);
        page.setUpdatedAt(Instant.now());
        page.setContentMarkdown("# FDA AI Guidance\nUpdated content.");
        repository.save(page);

        Optional<WikiPageEntity> found = repository.findById("fda-ai-guidance");
        assertThat(found).isPresent();
        assertThat(found.get().getRevision()).isEqualTo(2);
        assertThat(found.get().getUpdatedAt()).isNotNull();
    }

    @Test
    void searchByKeyword_matchesTitle() {
        repository.save(createPage("fda-ai-guidance", "FDA AI Guidance", "ENTITY", "fda"));
        repository.save(createPage("epic-ambient", "Epic Ambient AI", "ENTITY", "epic|ambient"));

        List<WikiPageEntity> results = repository.searchByKeyword("FDA");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSlug()).isEqualTo("fda-ai-guidance");
    }

    @Test
    void searchByKeyword_matchesTags() {
        repository.save(createPage("fda-ai-guidance", "FDA AI Guidance", "ENTITY", "regulation|fda"));

        List<WikiPageEntity> results = repository.searchByKeyword("regulation");
        assertThat(results).hasSize(1);
    }

    @Test
    void findByPageType_filtersCorrectly() {
        repository.save(createPage("fda-ai-guidance", "FDA AI Guidance", "ENTITY", "fda"));
        repository.save(createPage("ai-healthcare-overview", "AI Healthcare Overview", "OVERVIEW", "ai"));

        List<WikiPageEntity> entities = repository.findByPageType("ENTITY");
        assertThat(entities).hasSize(1);
        assertThat(entities.get(0).getSlug()).isEqualTo("fda-ai-guidance");
    }
}
