package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.Contradiction;
import com.wgblackmon.aihealthcare.domain.model.WikiPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link WikiQueryAdapter} using the JPA slice.
 *
 * <p>Verifies entity-to-domain mapping, keyword search, and contradiction
 * retrieval through the full adapter → repository → H2 path.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
@DataJpaTest
@Import(WikiQueryAdapter.class)
class WikiQueryAdapterTest {

    @Autowired
    private WikiQueryAdapter adapter;

    @Autowired
    private WikiPageRepository pageRepository;

    @Autowired
    private WikiSourceRefRepository sourceRefRepository;

    @Autowired
    private WikiContradictionRepository contradictionRepository;

    private static final Instant NOW = Instant.parse("2026-07-04T12:00:00Z");

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
    void findRelevantPages_returnsMatchingPages() {
        pageRepository.save(createPage("fda-ai-guidance", "FDA AI Guidance", "ENTITY", "fda|regulation"));
        pageRepository.save(createPage("epic-ambient", "Epic Ambient AI", "ENTITY", "epic|ambient"));

        List<WikiPage> results = adapter.findRelevantPages("FDA", 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).slug()).isEqualTo("fda-ai-guidance");
        assertThat(results.get(0).title()).isEqualTo("FDA AI Guidance");
        assertThat(results.get(0).pageType().name()).isEqualTo("ENTITY");
    }

    @Test
    void findRelevantPages_respectsMaxResults() {
        pageRepository.save(createPage("page-one", "AI in Healthcare", "OVERVIEW", "ai"));
        pageRepository.save(createPage("page-two", "AI in Diagnostics", "OVERVIEW", "ai"));
        pageRepository.save(createPage("page-three", "AI in Imaging", "OVERVIEW", "ai"));

        List<WikiPage> results = adapter.findRelevantPages("AI", 2);

        assertThat(results).hasSize(2);
    }

    @Test
    void findRelevantPages_noMatches_returnsEmpty() {
        pageRepository.save(createPage("fda-ai-guidance", "FDA AI Guidance", "ENTITY", "fda"));

        List<WikiPage> results = adapter.findRelevantPages("nonexistent", 10);

        assertThat(results).isEmpty();
    }

    @Test
    void getPage_existingSlug_returnsDomainRecord() {
        pageRepository.save(createPage("fda-ai-guidance", "FDA AI Guidance", "ENTITY", "fda|regulation"));

        WikiSourceRefEntity ref = new WikiSourceRefEntity();
        ref.setPageSlug("fda-ai-guidance");
        ref.setArticleId("article-001");
        ref.setSourceName("PubMed");
        ref.setHarvestedOn(LocalDate.of(2026, 7, 1));
        ref.setExcerpt("An excerpt from the article");
        sourceRefRepository.save(ref);

        WikiPage page = adapter.getPage("fda-ai-guidance");

        assertThat(page).isNotNull();
        assertThat(page.slug()).isEqualTo("fda-ai-guidance");
        assertThat(page.tags()).containsExactly("fda", "regulation");
        assertThat(page.sources()).hasSize(1);
        assertThat(page.sources().get(0).articleId()).isEqualTo("article-001");
        assertThat(page.sources().get(0).sourceName()).isEqualTo("PubMed");
    }

    @Test
    void getPage_missingSlug_returnsNull() {
        WikiPage page = adapter.getPage("nonexistent-slug");

        assertThat(page).isNull();
    }

    @Test
    void recentContradictions_returnsMatchingContradictions() {
        WikiContradictionEntity entity = new WikiContradictionEntity();
        entity.setPageSlug("fda-ai-guidance");
        entity.setPriorClaim("All AI devices require review");
        entity.setNewClaim("Low-risk AI devices exempt");
        entity.setPriorSourceIds("article-001|article-002");
        entity.setNewSourceIds("article-042");
        entity.setDetectedAt(Instant.parse("2026-07-03T00:00:00Z"));
        contradictionRepository.save(entity);

        Instant cutoff = Instant.parse("2026-07-01T00:00:00Z");
        List<Contradiction> results = adapter.recentContradictions(cutoff);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).pageSlug()).isEqualTo("fda-ai-guidance");
        assertThat(results.get(0).priorClaim()).isEqualTo("All AI devices require review");
        assertThat(results.get(0).priorSources()).hasSize(2);
        assertThat(results.get(0).newSources()).hasSize(1);
    }

    @Test
    void recentContradictions_noMatches_returnsEmpty() {
        Instant future = Instant.parse("2099-01-01T00:00:00Z");
        List<Contradiction> results = adapter.recentContradictions(future);

        assertThat(results).isEmpty();
    }
}
