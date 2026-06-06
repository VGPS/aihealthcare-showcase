package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.ArticleSearchCriteria;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA slice tests for {@link ArticleSearchQueryAdapter} and
 * {@link ArticleSpecificationBuilder}.
 *
 * <p>Uses {@code @DataJpaTest} with H2 to verify that criteria-based
 * specifications produce the correct SQL predicates. Seeds the database
 * with two articles having different titles, topics, tiers, and dates,
 * then asserts that each filter field narrows results correctly.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-06-01
 * @updated 2026-06-06
 */
@DataJpaTest
class ArticleSearchQueryAdapterTest {

    @Autowired
    private NewsArticleRepository repository;

    private ArticleSearchQueryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ArticleSearchQueryAdapter(repository);

        repository.deleteAll();

        NewsArticleEntity academic = new NewsArticleEntity();
        academic.setArticleId("academic-001");
        academic.setTitle("AI Advances in Radiology Diagnostics");
        academic.setUrl("https://example.com/radiology");
        academic.setBodyText("A study shows AI outperforms radiologists in chest X-ray analysis.");
        academic.setTopic("PubMed AI Healthcare");
        academic.setAuthor("Dr. Smith");
        academic.setTopicId(1L);
        academic.setSourceName("PubMed");
        academic.setSourceTier("ACADEMIC");
        academic.setSourceWeight(0.9);
        academic.setPublishedAt(Instant.parse("2026-05-01T09:00:00Z"));
        academic.setCreatedAt(Instant.parse("2026-05-01T10:00:00Z"));
        repository.save(academic);

        NewsArticleEntity industry = new NewsArticleEntity();
        industry.setArticleId("industry-001");
        industry.setTitle("Google Launches New Healthcare AI Platform");
        industry.setUrl("https://example.com/google-health");
        industry.setBodyText("Google announced a new platform for clinical trial matching.");
        industry.setTopic("Google Healthcare");
        industry.setAuthor("Jane Doe");
        industry.setTopicId(2L);
        industry.setSourceName("Healthcare Dive");
        industry.setSourceTier("INDUSTRY");
        industry.setSourceWeight(0.6);
        industry.setPublishedAt(Instant.parse("2026-05-15T14:00:00Z"));
        industry.setCreatedAt(Instant.parse("2026-05-15T15:00:00Z"));
        repository.save(industry);
    }

    @Test
    void findByCriteria_titleFilter_caseInsensitiveSubstring() {
        ArticleSearchCriteria criteria = new ArticleSearchCriteria(
                "radiology", null, null, null, null, null, null);

        List<NewsArticle> result = adapter.findByCriteria(criteria);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).articleId()).isEqualTo("academic-001");
    }

    @Test
    void findByCriteria_topicFilter_caseInsensitiveSubstring() {
        ArticleSearchCriteria criteria = new ArticleSearchCriteria(
                null, "google", null, null, null, null, null);

        List<NewsArticle> result = adapter.findByCriteria(criteria);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).articleId()).isEqualTo("industry-001");
    }

    @Test
    void findByCriteria_authorFilter() {
        ArticleSearchCriteria criteria = new ArticleSearchCriteria(
                null, null, "smith", null, null, null, null);

        List<NewsArticle> result = adapter.findByCriteria(criteria);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).author()).isEqualTo("Dr. Smith");
    }

    @Test
    void findByCriteria_bodyTextFilter() {
        ArticleSearchCriteria criteria = new ArticleSearchCriteria(
                null, null, null, null, "clinical trial", null, null);

        List<NewsArticle> result = adapter.findByCriteria(criteria);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).articleId()).isEqualTo("industry-001");
    }

    @Test
    void findByCriteria_publishedDateRange() {
        ArticleSearchCriteria criteria = new ArticleSearchCriteria(
                null, null, null, null, null,
                Instant.parse("2026-05-10T00:00:00Z"),
                Instant.parse("2026-05-20T00:00:00Z"));

        List<NewsArticle> result = adapter.findByCriteria(criteria);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).articleId()).isEqualTo("industry-001");
    }

    @Test
    void findByCriteria_multipleCriteria_andCombined() {
        ArticleSearchCriteria criteria = new ArticleSearchCriteria(
                "AI", null, null, "PubMed", null, null, null);

        List<NewsArticle> result = adapter.findByCriteria(criteria);

        // Both have "AI" in title, but only one has sourceName "PubMed"
        assertThat(result).hasSize(1);
        assertThat(result.get(0).articleId()).isEqualTo("academic-001");
    }

    @Test
    void findByCriteria_noMatch_returnsEmptyList() {
        ArticleSearchCriteria criteria = new ArticleSearchCriteria(
                "nonexistent-query", null, null, null, null, null, null);

        List<NewsArticle> result = adapter.findByCriteria(criteria);

        assertThat(result).isEmpty();
    }

    @Test
    void findByCriteria_sourceNameFilter() {
        ArticleSearchCriteria criteria = new ArticleSearchCriteria(
                null, null, null, "pubmed", null, null, null);

        List<NewsArticle> result = adapter.findByCriteria(criteria);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sourceName()).isEqualTo("PubMed");
    }
}
