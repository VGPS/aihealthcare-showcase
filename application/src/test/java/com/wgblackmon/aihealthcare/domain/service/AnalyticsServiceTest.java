package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.CountByLabel;
import com.wgblackmon.aihealthcare.domain.model.EvaluationAnalytics;
import com.wgblackmon.aihealthcare.domain.model.IngestionAnalytics;
import com.wgblackmon.aihealthcare.domain.model.RunAnalytics;
import com.wgblackmon.aihealthcare.domain.model.VariantScore;
import com.wgblackmon.aihealthcare.domain.port.outbound.AnalyticsPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AnalyticsService}.
 *
 * <p>All outbound ports are mocked — no database access.
 * Focuses on the one piece of business logic the service owns:
 * computing {@code bestVariantId} from variant score aggregates.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-03
 * @updated 2026-05-03
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private AnalyticsPort analyticsPort;

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new AnalyticsService(analyticsPort);
    }

    // -------------------------------------------------------------------------
    // getIngestionAnalytics() — delegates to port
    // -------------------------------------------------------------------------

    @Test
    void getIngestionAnalytics_delegatesToPort() {
        IngestionAnalytics expected = new IngestionAnalytics(
                100L,
                List.of(new CountByLabel("ACADEMIC", 60L), new CountByLabel("INDUSTRY", 40L)),
                List.of(new CountByLabel("PubMed AI Healthcare", 60L)),
                10L, 30L);
        when(analyticsPort.getIngestionAnalytics()).thenReturn(expected);

        IngestionAnalytics result = service.getIngestionAnalytics();

        assertThat(result).isEqualTo(expected);
        verify(analyticsPort).getIngestionAnalytics();
    }

    // -------------------------------------------------------------------------
    // getRunAnalytics() — delegates to port
    // -------------------------------------------------------------------------

    @Test
    void getRunAnalytics_delegatesToPort() {
        RunAnalytics expected = new RunAnalytics(5L, 2L, 2L, 1L, Instant.now());
        when(analyticsPort.getRunAnalytics()).thenReturn(expected);

        RunAnalytics result = service.getRunAnalytics();

        assertThat(result).isEqualTo(expected);
        verify(analyticsPort).getRunAnalytics();
    }

    // -------------------------------------------------------------------------
    // getEvaluationAnalytics() — bestVariantId computation
    // -------------------------------------------------------------------------

    @Test
    void getEvaluationAnalytics_setsHighestScoringVariantAsBest() {
        VariantScore lower = new VariantScore("v1", "Concise", 3L, 0.70, 0.7, 0.7, 0.7, 0.7, 0.7);
        VariantScore higher = new VariantScore("v2", "Detailed", 4L, 0.85, 0.85, 0.85, 0.85, 0.85, 0.85);
        EvaluationAnalytics raw = new EvaluationAnalytics(7L, 2L, List.of(lower, higher), null);
        when(analyticsPort.getEvaluationAnalytics()).thenReturn(raw);

        EvaluationAnalytics result = service.getEvaluationAnalytics();

        assertThat(result.bestVariantId()).isEqualTo("v2");
    }

    @Test
    void getEvaluationAnalytics_noVariants_bestVariantIdIsNull() {
        EvaluationAnalytics raw = new EvaluationAnalytics(0L, 0L, List.of(), null);
        when(analyticsPort.getEvaluationAnalytics()).thenReturn(raw);

        EvaluationAnalytics result = service.getEvaluationAnalytics();

        assertThat(result.bestVariantId()).isNull();
    }

    @Test
    void getEvaluationAnalytics_singleVariant_thatVariantIsBest() {
        VariantScore only = new VariantScore("v1", "Baseline", 2L, 0.75, 0.7, 0.7, 0.7, 0.7, 0.7);
        EvaluationAnalytics raw = new EvaluationAnalytics(2L, 0L, List.of(only), null);
        when(analyticsPort.getEvaluationAnalytics()).thenReturn(raw);

        EvaluationAnalytics result = service.getEvaluationAnalytics();

        assertThat(result.bestVariantId()).isEqualTo("v1");
    }

    @Test
    void getEvaluationAnalytics_preservesTotalAndComparisonCounts() {
        EvaluationAnalytics raw = new EvaluationAnalytics(15L, 4L, List.of(), null);
        when(analyticsPort.getEvaluationAnalytics()).thenReturn(raw);

        EvaluationAnalytics result = service.getEvaluationAnalytics();

        assertThat(result.totalEvaluations()).isEqualTo(15L);
        assertThat(result.totalComparisons()).isEqualTo(4L);
    }

    @Test
    void getEvaluationAnalytics_preservesVariantScoreList() {
        VariantScore vs = new VariantScore("v1", "V1", 5L, 0.8, 0.8, 0.8, 0.8, 0.8, 0.8);
        EvaluationAnalytics raw = new EvaluationAnalytics(5L, 1L, List.of(vs), null);
        when(analyticsPort.getEvaluationAnalytics()).thenReturn(raw);

        EvaluationAnalytics result = service.getEvaluationAnalytics();

        assertThat(result.variantScores()).hasSize(1);
        assertThat(result.variantScores().get(0).variantId()).isEqualTo("v1");
    }

    // -------------------------------------------------------------------------
    // CountByLabel record validation
    // -------------------------------------------------------------------------

    @Test
    void countByLabel_blankLabel_throwsIllegalArgument() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new CountByLabel("  ", 5L));
    }

    @Test
    void countByLabel_negativeCount_throwsIllegalArgument() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new CountByLabel("ACADEMIC", -1L));
    }
}
