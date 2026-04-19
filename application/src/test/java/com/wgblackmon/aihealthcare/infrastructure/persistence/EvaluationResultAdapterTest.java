package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.exception.EvaluationNotFoundException;
import com.wgblackmon.aihealthcare.domain.model.ComparisonResult;
import com.wgblackmon.aihealthcare.domain.model.EvaluationResult;
import com.wgblackmon.aihealthcare.domain.model.EvaluationScore;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
import com.wgblackmon.aihealthcare.domain.model.NewsletterTone;
import com.wgblackmon.aihealthcare.domain.model.SectionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JPA slice tests for {@link EvaluationResultAdapter}.
 *
 * <p>Uses {@code @DataJpaTest} which loads only the JPA slice (H2, repositories,
 * entity scanning) without the full Spring context.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-17
 * @updated 2026-04-17
 */
@DataJpaTest
class EvaluationResultAdapterTest {

    @Autowired
    private EvaluationResultRepository evalRepository;

    @Autowired
    private ComparisonResultRepository compRepository;

    private EvaluationResultAdapter adapter;

    private static final Instant NOW = Instant.parse("2026-04-17T00:00:00Z");

    private static final NewsletterSection SECTION = new NewsletterSection(
            "sec-001", SectionType.WHAT_SHIPPED, "AI Healthcare",
            "AI Advances", "Summary of advances.", List.of("art-001"));

    private static final EvaluationScore SCORE = new EvaluationScore(
            0.85, 0.90, 0.75, 0.80, 0.70, 0.80, "Solid output");

    private static final EvaluationResult EVAL_1 = new EvaluationResult(
            "eval-001", "variant-a", "Variant A", List.of("art-001", "art-002"),
            "AI Healthcare", NewsletterTone.PROFESSIONAL, SECTION, SCORE, NOW);

    private static final EvaluationResult EVAL_2 = new EvaluationResult(
            "eval-002", "variant-b", "Variant B", List.of("art-001", "art-002"),
            "AI Healthcare", NewsletterTone.PROFESSIONAL, SECTION, SCORE, NOW);

    @BeforeEach
    void setUp() {
        adapter = new EvaluationResultAdapter(evalRepository, compRepository);
    }

    @Test
    void save_and_findByEvaluationId_roundTrips() {
        adapter.save(EVAL_1);

        EvaluationResult found = adapter.findByEvaluationId("eval-001");

        assertThat(found.evaluationId()).isEqualTo("eval-001");
        assertThat(found.variantId()).isEqualTo("variant-a");
        assertThat(found.variantName()).isEqualTo("Variant A");
        assertThat(found.articleIds()).containsExactly("art-001", "art-002");
        assertThat(found.topic()).isEqualTo("AI Healthcare");
        assertThat(found.tone()).isEqualTo(NewsletterTone.PROFESSIONAL);
        assertThat(found.section().headline()).isEqualTo("AI Advances");
        assertThat(found.score().relevance()).isEqualTo(0.85);
        assertThat(found.score().overall()).isEqualTo(0.80);
        assertThat(found.score().scoringNotes()).isEqualTo("Solid output");
    }

    @Test
    void findByEvaluationId_notFound_throws() {
        assertThatThrownBy(() -> adapter.findByEvaluationId("unknown"))
                .isInstanceOf(EvaluationNotFoundException.class);
    }

    @Test
    void findByVariantId_returnsMatching() {
        adapter.save(EVAL_1);
        adapter.save(EVAL_2);

        List<EvaluationResult> results = adapter.findByVariantId("variant-a");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).evaluationId()).isEqualTo("eval-001");
    }

    @Test
    void findAll_returnsAll() {
        adapter.save(EVAL_1);
        adapter.save(EVAL_2);

        List<EvaluationResult> results = adapter.findAll();

        assertThat(results).hasSize(2);
    }

    @Test
    void saveComparison_and_findComparisonById_roundTrips() {
        ComparisonResult comparison = new ComparisonResult(
                "comp-001", List.of("art-001", "art-002"),
                "AI Healthcare", NewsletterTone.PROFESSIONAL,
                List.of(EVAL_1, EVAL_2), NOW);

        adapter.saveComparison(comparison);

        ComparisonResult found = adapter.findComparisonById("comp-001");

        assertThat(found.comparisonId()).isEqualTo("comp-001");
        assertThat(found.articleIds()).containsExactly("art-001", "art-002");
        assertThat(found.topic()).isEqualTo("AI Healthcare");
        assertThat(found.tone()).isEqualTo(NewsletterTone.PROFESSIONAL);
        assertThat(found.results()).hasSize(2);
    }

    @Test
    void findComparisonById_notFound_throws() {
        assertThatThrownBy(() -> adapter.findComparisonById("unknown"))
                .isInstanceOf(EvaluationNotFoundException.class);
    }

    @Test
    void findAllComparisons_returnsAll() {
        ComparisonResult comparison = new ComparisonResult(
                "comp-001", List.of("art-001"),
                "AI Healthcare", NewsletterTone.PROFESSIONAL,
                List.of(EVAL_1, EVAL_2), NOW);

        adapter.saveComparison(comparison);

        List<ComparisonResult> results = adapter.findAllComparisons();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).results()).hasSize(2);
    }
}
