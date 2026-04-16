package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.exception.RunNotFoundException;
import com.wgblackmon.aihealthcare.domain.model.NewsletterTone;
import com.wgblackmon.aihealthcare.domain.port.inbound.DeliverNewsletterUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.GenerateNewsletterUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.IngestArticlesUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link NewsletterGenerationScheduler}.
 *
 * <p>Verifies the happy-path pipeline (ingest → generate → deliver, in that order
 * with the configured parameters) and the error-recovery guarantee that no
 * exception from any stage is allowed to propagate out of the scheduled method.
 * No Spring context is loaded — dependencies are provided as Mockito mocks.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-16
 * @updated 2026-04-16
 */
@ExtendWith(MockitoExtension.class)
class NewsletterGenerationSchedulerTest {

    private static final String         TITLE                   = "AI in Healthcare Weekly";
    private static final NewsletterTone TONE                    = NewsletterTone.PROFESSIONAL;
    private static final String         TOPIC                   = "AI Healthcare";
    private static final int            MAX_ARTICLES_PER_TOPIC  = 20;
    private static final int            MAX_SECTIONS_PER_TOPIC  = 3;

    @Mock
    private IngestArticlesUseCase ingestUseCase;

    @Mock
    private GenerateNewsletterUseCase generateUseCase;

    @Mock
    private DeliverNewsletterUseCase deliverUseCase;

    private NewsletterGenerationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new NewsletterGenerationScheduler(
                ingestUseCase,
                generateUseCase,
                deliverUseCase,
                TITLE,
                TONE,
                TOPIC,
                MAX_ARTICLES_PER_TOPIC,
                MAX_SECTIONS_PER_TOPIC);
    }

    // -------------------------------------------------------------------------
    // runWeeklyNewsletter() — happy path
    // -------------------------------------------------------------------------

    @Test
    void runWeeklyNewsletter_invokesIngestThenGenerateThenDeliverInOrder() {
        scheduler.runWeeklyNewsletter();

        InOrder ordered = inOrder(ingestUseCase, generateUseCase, deliverUseCase);
        ordered.verify(ingestUseCase).ingest(anyString(), any(LocalDate.class), anyList(), anyInt());
        ordered.verify(generateUseCase).generate(anyString(), anyString(), anyString(), any(NewsletterTone.class), anyInt());
        ordered.verify(deliverUseCase).deliver(anyString());
    }

    @Test
    void runWeeklyNewsletter_passesConfiguredParametersToUseCases() {
        scheduler.runWeeklyNewsletter();

        verify(ingestUseCase).ingest(
                anyString(),
                any(LocalDate.class),
                eq(List.of(TOPIC)),
                eq(MAX_ARTICLES_PER_TOPIC));
        verify(generateUseCase).generate(
                anyString(),
                anyString(),
                eq(TITLE),
                eq(TONE),
                eq(MAX_SECTIONS_PER_TOPIC));
    }

    // -------------------------------------------------------------------------
    // runWeeklyNewsletter() — exception handling
    // -------------------------------------------------------------------------

    @Test
    void runWeeklyNewsletter_ingestThrows_doesNotCallGenerateOrDeliverAndDoesNotPropagate() {
        doThrow(new RuntimeException("ingest failure"))
                .when(ingestUseCase).ingest(anyString(), any(LocalDate.class), anyList(), anyInt());

        assertThatCode(() -> scheduler.runWeeklyNewsletter()).doesNotThrowAnyException();

        verify(generateUseCase, never()).generate(anyString(), anyString(), anyString(), any(NewsletterTone.class), anyInt());
        verify(deliverUseCase, never()).deliver(anyString());
    }

    @Test
    void runWeeklyNewsletter_generateThrows_doesNotCallDeliverAndDoesNotPropagate() {
        doThrow(new RuntimeException("generate failure"))
                .when(generateUseCase).generate(anyString(), anyString(), anyString(), any(NewsletterTone.class), anyInt());

        assertThatCode(() -> scheduler.runWeeklyNewsletter()).doesNotThrowAnyException();

        verify(deliverUseCase, never()).deliver(anyString());
    }

    @Test
    void runWeeklyNewsletter_deliverThrows_doesNotPropagate() {
        doThrow(new RunNotFoundException("missing-run"))
                .when(deliverUseCase).deliver(anyString());

        assertThatCode(() -> scheduler.runWeeklyNewsletter()).doesNotThrowAnyException();
    }
}
