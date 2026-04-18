package com.wgblackmon.aihealthcare.application.service;

import com.wgblackmon.aihealthcare.domain.exception.PromptVariantNotFoundException;
import com.wgblackmon.aihealthcare.domain.model.ComparisonResult;
import com.wgblackmon.aihealthcare.domain.model.EvaluationResult;
import com.wgblackmon.aihealthcare.domain.model.EvaluationScore;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
import com.wgblackmon.aihealthcare.domain.model.NewsletterTone;
import com.wgblackmon.aihealthcare.domain.model.PromptVariant;
import com.wgblackmon.aihealthcare.domain.model.SectionType;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiEvaluationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSummarizationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.EvaluationResultPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.PromptVariantPort;
import com.wgblackmon.aihealthcare.domain.service.PromptEvaluationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PromptEvaluationService}.
 *
 * <p>All ports are injected as Mockito mocks — no Spring context, no database,
 * no AI calls.  Tests cover variant CRUD, single evaluation, comparison, and
 * result retrieval workflows.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-17
 * @updated 2026-04-17
 */
@ExtendWith(MockitoExtension.class)
class PromptEvaluationServiceTest {

    @Mock private PromptVariantPort    variantPort;
    @Mock private AiSummarizationPort  summarizationPort;
    @Mock private AiEvaluationPort     evaluationPort;
    @Mock private ArticleIngestionPort ingestionPort;
    @Mock private EvaluationResultPort resultPort;

    private PromptEvaluationService service;

    private static final String VARIANT_ID = "summarize-v1";
    private static final String NAME       = "Default V1";
    private static final String TEMPLATE   = "Summarize {topic} in {toneInstruction} style:\n{articles}";
    private static final String DESC       = "Baseline prompt";
    private static final Instant NOW       = Instant.parse("2026-04-17T00:00:00Z");

    private static final PromptVariant VARIANT = new PromptVariant(
            VARIANT_ID, NAME, TEMPLATE, DESC, NOW);

    private static final NewsArticle ARTICLE = new NewsArticle(
            "art-001", "AI in Healthcare", URI.create("https://example.com/1"),
            "Body text.", "AI Healthcare", null,
            null, null, null, 0.5, null);

    private static final NewsletterSection SECTION = new NewsletterSection(
            "eval-sec-001", SectionType.WHAT_SHIPPED, "AI Healthcare",
            "AI Headline", "AI Summary text.", List.of("art-001"));

    private static final EvaluationScore SCORE = new EvaluationScore(
            0.85, 0.90, 0.75, 0.80, 0.70, 0.80, "Good output");

    @BeforeEach
    void setUp() {
        service = new PromptEvaluationService(
                variantPort, summarizationPort, evaluationPort,
                ingestionPort, resultPort);
    }

    // -------------------------------------------------------------------------
    // Variant CRUD
    // -------------------------------------------------------------------------

    @Test
    void createVariant_savesAndReturns() {
        PromptVariant result = service.createVariant(VARIANT_ID, NAME, TEMPLATE, DESC);

        assertThat(result.variantId()).isEqualTo(VARIANT_ID);
        assertThat(result.name()).isEqualTo(NAME);
        assertThat(result.templateText()).isEqualTo(TEMPLATE);

        ArgumentCaptor<PromptVariant> captor = ArgumentCaptor.forClass(PromptVariant.class);
        verify(variantPort).save(captor.capture());
        assertThat(captor.getValue().variantId()).isEqualTo(VARIANT_ID);
    }

    @Test
    void createVariant_blankName_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.createVariant(VARIANT_ID, "", TEMPLATE, DESC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void listVariants_delegatesToPort() {
        when(variantPort.findAll()).thenReturn(List.of(VARIANT));

        List<PromptVariant> result = service.listVariants();

        assertThat(result).hasSize(1);
        verify(variantPort).findAll();
    }

    @Test
    void getVariant_found_returns() {
        when(variantPort.findByVariantId(VARIANT_ID)).thenReturn(VARIANT);

        PromptVariant result = service.getVariant(VARIANT_ID);

        assertThat(result.variantId()).isEqualTo(VARIANT_ID);
    }

    @Test
    void getVariant_notFound_throws() {
        when(variantPort.findByVariantId("unknown"))
                .thenThrow(new PromptVariantNotFoundException("unknown"));

        assertThatThrownBy(() -> service.getVariant("unknown"))
                .isInstanceOf(PromptVariantNotFoundException.class);
    }

    @Test
    void deleteVariant_delegatesToPort() {
        service.deleteVariant(VARIANT_ID);

        verify(variantPort).delete(VARIANT_ID);
    }

    // -------------------------------------------------------------------------
    // Evaluation
    // -------------------------------------------------------------------------

    @Test
    void evaluate_loadsVariantAndArticles_callsAi_scoresAndPersists() {
        when(variantPort.findByVariantId(VARIANT_ID)).thenReturn(VARIANT);
        when(ingestionPort.fetchArticlesByIds(List.of("art-001"))).thenReturn(List.of(ARTICLE));
        when(summarizationPort.summarizeWithTemplate(
                eq(List.of(ARTICLE)), eq("AI Healthcare"), eq(NewsletterTone.PROFESSIONAL),
                anyString(), eq(TEMPLATE)))
                .thenReturn(SECTION);
        when(evaluationPort.evaluate(SECTION, List.of(ARTICLE), "AI Healthcare",
                NewsletterTone.PROFESSIONAL))
                .thenReturn(SCORE);

        EvaluationResult result = service.evaluate(
                VARIANT_ID, List.of("art-001"), "AI Healthcare", NewsletterTone.PROFESSIONAL);

        assertThat(result.variantId()).isEqualTo(VARIANT_ID);
        assertThat(result.variantName()).isEqualTo(NAME);
        assertThat(result.section()).isEqualTo(SECTION);
        assertThat(result.score()).isEqualTo(SCORE);
        assertThat(result.evaluationId()).isNotBlank();

        verify(resultPort).save(any(EvaluationResult.class));
    }

    @Test
    void evaluate_unknownVariant_throws() {
        when(variantPort.findByVariantId("unknown"))
                .thenThrow(new PromptVariantNotFoundException("unknown"));

        assertThatThrownBy(() -> service.evaluate(
                "unknown", List.of("art-001"), "AI Healthcare", NewsletterTone.PROFESSIONAL))
                .isInstanceOf(PromptVariantNotFoundException.class);

        verify(summarizationPort, never()).summarizeWithTemplate(
                anyList(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void evaluate_noArticlesFound_throwsIllegalArgument() {
        when(variantPort.findByVariantId(VARIANT_ID)).thenReturn(VARIANT);
        when(ingestionPort.fetchArticlesByIds(List.of("missing"))).thenReturn(List.of());

        assertThatThrownBy(() -> service.evaluate(
                VARIANT_ID, List.of("missing"), "AI Healthcare", NewsletterTone.PROFESSIONAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No articles found");
    }

    // -------------------------------------------------------------------------
    // Comparison
    // -------------------------------------------------------------------------

    @Test
    void compare_twoVariants_runsEachAndPersists() {
        PromptVariant variantB = new PromptVariant(
                "variant-b", "Variant B", "Different template: {topic}", "Alt", NOW);

        when(variantPort.findByVariantId(VARIANT_ID)).thenReturn(VARIANT);
        when(variantPort.findByVariantId("variant-b")).thenReturn(variantB);
        when(ingestionPort.fetchArticlesByIds(List.of("art-001"))).thenReturn(List.of(ARTICLE));
        when(summarizationPort.summarizeWithTemplate(
                anyList(), anyString(), any(), anyString(), anyString()))
                .thenReturn(SECTION);
        when(evaluationPort.evaluate(any(), anyList(), anyString(), any()))
                .thenReturn(SCORE);

        ComparisonResult result = service.compare(
                List.of(VARIANT_ID, "variant-b"), List.of("art-001"),
                "AI Healthcare", NewsletterTone.PROFESSIONAL);

        assertThat(result.results()).hasSize(2);
        assertThat(result.comparisonId()).isNotBlank();

        // Each variant evaluated individually = 2 saves + 1 comparison save
        verify(resultPort, times(2)).save(any(EvaluationResult.class));
        verify(resultPort).saveComparison(any(ComparisonResult.class));
    }

    @Test
    void compare_lessThanTwoVariants_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.compare(
                List.of(VARIANT_ID), List.of("art-001"),
                "AI Healthcare", NewsletterTone.PROFESSIONAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least two");
    }

    @Test
    void compare_nullVariants_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.compare(
                null, List.of("art-001"),
                "AI Healthcare", NewsletterTone.PROFESSIONAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least two");
    }

    // -------------------------------------------------------------------------
    // Result retrieval
    // -------------------------------------------------------------------------

    @Test
    void getEvaluation_delegatesToPort() {
        EvaluationResult eval = new EvaluationResult(
                "eval-001", VARIANT_ID, NAME, List.of("art-001"),
                "AI Healthcare", NewsletterTone.PROFESSIONAL, SECTION, SCORE, NOW);
        when(resultPort.findByEvaluationId("eval-001")).thenReturn(eval);

        EvaluationResult result = service.getEvaluation("eval-001");

        assertThat(result.evaluationId()).isEqualTo("eval-001");
    }

    @Test
    void listEvaluations_delegatesToPort() {
        when(resultPort.findAll()).thenReturn(List.of());

        List<EvaluationResult> result = service.listEvaluations();

        assertThat(result).isEmpty();
        verify(resultPort).findAll();
    }

    @Test
    void listEvaluationsByVariant_delegatesToPort() {
        when(resultPort.findByVariantId(VARIANT_ID)).thenReturn(List.of());

        List<EvaluationResult> result = service.listEvaluationsByVariant(VARIANT_ID);

        assertThat(result).isEmpty();
        verify(resultPort).findByVariantId(VARIANT_ID);
    }
}
