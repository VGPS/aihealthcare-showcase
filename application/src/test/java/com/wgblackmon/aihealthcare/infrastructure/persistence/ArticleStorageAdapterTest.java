package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA slice tests for {@link ArticleStorageAdapter}.
 *
 * <p>Uses {@code @DataJpaTest} which loads only the JPA slice (H2, repositories,
 * entity scanning) without the full Spring context — Spring AI, scheduling, and
 * web layers are excluded.  The adapter is constructed directly in
 * {@link #setUp()} via constructor injection to respect the project's
 * no-field-injection convention.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-11
 * @updated 2026-04-11
 */
@DataJpaTest
class ArticleStorageAdapterTest {

    @Autowired
    private NewsArticleRepository repository;

    private ArticleStorageAdapter adapter;

    private static final NewsArticle ARTICLE = new NewsArticle(
            "article-001",
            "AI Improves Diagnostic Accuracy",
            URI.create("https://example.com/article-001"),
            "Researchers found that AI models outperform radiologists.",
            "PubMed AI Healthcare",
            "Dr. Jane Smith",
            1L, "PubMed AI Healthcare", "ACADEMIC", 0.9,
            Instant.parse("2026-04-01T00:00:00Z")
    );

    @BeforeEach
    void setUp() {
        adapter = new ArticleStorageAdapter(repository);
    }

    @Test
    @DisplayName("save() persists a new article to the database")
    void save_newArticle_persistsToDb() {
        adapter.save(List.of(ARTICLE));

        List<NewsArticleEntity> all = repository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getArticleId()).isEqualTo("article-001");
        assertThat(all.get(0).getTitle()).isEqualTo("AI Improves Diagnostic Accuracy");
        assertThat(all.get(0).getSourceTier()).isEqualTo("ACADEMIC");
        assertThat(all.get(0).getSourceWeight()).isEqualTo(0.9);
    }

    @Test
    @DisplayName("save() silently skips duplicate URLs")
    void save_duplicateUrl_silentlySkips() {
        adapter.save(List.of(ARTICLE));
        adapter.save(List.of(ARTICLE));

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("save() with empty list is a no-op")
    void save_emptyList_noOp() {
        adapter.save(List.of());

        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("save() persists multiple distinct articles")
    void save_multipleDistinctArticles_allPersisted() {
        NewsArticle article2 = new NewsArticle(
                "article-002",
                "ML in Drug Discovery",
                URI.create("https://example.com/article-002"),
                "Machine learning accelerates drug discovery pipelines.",
                "PubMed AI Healthcare",
                null, 1L, "PubMed AI Healthcare", "ACADEMIC", 0.9,
                Instant.parse("2026-04-02T00:00:00Z")
        );

        adapter.save(List.of(ARTICLE, article2));

        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("findByTopic() returns articles matching the topic name")
    void findByTopic_returnsMatchingArticles() {
        adapter.save(List.of(ARTICLE));

        List<NewsArticleEntity> found = repository.findByTopic("PubMed AI Healthcare");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getArticleId()).isEqualTo("article-001");
    }
}
