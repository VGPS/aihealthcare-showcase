package com.wgblackmon.aihealthcare.infrastructure.ingestion;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ArticleRelevanceFilter}.
 *
 * <p>Verifies that the healthcare + AI dual-pattern filter correctly retains
 * articles that mention both domains and drops articles that mention only one
 * domain or neither.  Representative WHO News and MIT Tech Review scenarios
 * are included to validate the primary use case.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-02
 * @updated 2026-05-02
 */
class ArticleRelevanceFilterTest {

    private ArticleRelevanceFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ArticleRelevanceFilter();
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static NewsArticle article(String title, String body) {
        return new NewsArticle(
                "test-id",
                title,
                URI.create("https://example.com/test"),
                body,
                "TestSource",
                null, null, "TestSource", null,
                0.5,
                null
        );
    }

    // -------------------------------------------------------------------------
    // isRelevant() — positive cases (both patterns match)
    // -------------------------------------------------------------------------

    @Test
    void isRelevant_titleHasBothKeywords_returnsTrue() {
        NewsArticle a = article("AI-Powered Diagnosis Tool Approved by FDA", "");
        assertThat(filter.isRelevant(a)).isTrue();
    }

    @Test
    void isRelevant_bodyHasBothKeywords_returnsTrue() {
        NewsArticle a = article("New Study Released",
                "Machine learning models are being used to improve patient outcomes in hospitals.");
        assertThat(filter.isRelevant(a)).isTrue();
    }

    @Test
    void isRelevant_splitAcrossTitleAndBody_returnsTrue() {
        // Healthcare keyword in title, AI keyword in body
        NewsArticle a = article("Clinical Trial Results", "Deep learning algorithm shows promise.");
        assertThat(filter.isRelevant(a)).isTrue();
    }

    @Test
    void isRelevant_llmAcronym_returnsTrue() {
        NewsArticle a = article("LLM in Healthcare", "Physicians are adopting large language models.");
        assertThat(filter.isRelevant(a)).isTrue();
    }

    @Test
    void isRelevant_generativeAiInHospital_returnsTrue() {
        NewsArticle a = article("Generative AI Cuts Hospital Readmissions",
                "A generative AI tool deployed in three hospitals reduced readmission rates.");
        assertThat(filter.isRelevant(a)).isTrue();
    }

    // -------------------------------------------------------------------------
    // isRelevant() — negative cases (only one or neither pattern matches)
    // -------------------------------------------------------------------------

    @Test
    void isRelevant_onlyHealthcareKeyword_returnsFalse() {
        // WHO News article about mpox — healthcare but no AI
        NewsArticle a = article("WHO Declares Mpox Public Health Emergency",
                "The World Health Organization has declared an international emergency regarding mpox.");
        assertThat(filter.isRelevant(a)).isFalse();
    }

    @Test
    void isRelevant_onlyAiKeyword_returnsFalse() {
        // MIT Tech Review article about AI for autonomous vehicles — AI but no healthcare
        NewsArticle a = article("Machine Learning Improves Autonomous Vehicle Safety",
                "Neural networks are being used to detect obstacles in self-driving car systems.");
        assertThat(filter.isRelevant(a)).isFalse();
    }

    @Test
    void isRelevant_neitherKeyword_returnsFalse() {
        NewsArticle a = article("Global Climate Summit Reaches New Agreement",
                "World leaders agreed on new carbon reduction targets at the annual summit.");
        assertThat(filter.isRelevant(a)).isFalse();
    }

    @Test
    void isRelevant_whoNewsAboutCholera_returnsFalse() {
        NewsArticle a = article("WHO Issues Cholera Alert for Three Countries",
                "Rising cholera cases in three African nations prompt emergency response from health agencies.");
        assertThat(filter.isRelevant(a)).isFalse();
    }

    @Test
    void isRelevant_mitAiButNotHealthcare_returnsFalse() {
        NewsArticle a = article("Artificial Intelligence Transforms Supply Chain Logistics",
                "Companies are using AI to optimize warehouse automation and delivery routes.");
        assertThat(filter.isRelevant(a)).isFalse();
    }

    // -------------------------------------------------------------------------
    // filter() — list-level behaviour
    // -------------------------------------------------------------------------

    @Test
    void filter_emptyList_returnsEmpty() {
        List<NewsArticle> result = filter.filter(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void filter_allRelevant_returnsAll() {
        List<NewsArticle> articles = List.of(
                article("AI Diagnosis in Clinical Settings", "Machine learning improves hospital care."),
                article("LLM Supports Medical Decision Making", "Physicians adopt deep learning tools.")
        );
        List<NewsArticle> result = filter.filter(articles);
        assertThat(result).hasSize(2);
    }

    @Test
    void filter_mixedRelevance_dropsIrrelevant() {
        List<NewsArticle> articles = List.of(
                article("AI Diagnosis Tool for Radiology", "Machine learning detects tumors in scans."),
                article("WHO Announces Climate Health Initiative", "Climate change threatens global health."),
                article("Neural Network Speeds Up Drug Discovery", "Deep learning models identify drug candidates.")
        );
        List<NewsArticle> result = filter.filter(articles);
        assertThat(result).hasSize(2);
        assertThat(result).extracting(NewsArticle::title)
                .containsExactlyInAnyOrder(
                        "AI Diagnosis Tool for Radiology",
                        "Neural Network Speeds Up Drug Discovery");
    }

    @Test
    void filter_allIrrelevant_returnsEmpty() {
        List<NewsArticle> articles = List.of(
                article("WHO Emergency Meeting on Mpox", "Outbreak continues to spread."),
                article("AI Powers New Satellite Imaging", "Machine learning used in space observation.")
        );
        List<NewsArticle> result = filter.filter(articles);
        assertThat(result).isEmpty();
    }
}
