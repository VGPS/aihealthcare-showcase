package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.NewsletterTone;
import com.wgblackmon.aihealthcare.domain.port.inbound.DeliverNewsletterUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.GenerateNewsletterUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.IngestArticlesUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterAutoSendPort;
import com.wgblackmon.aihealthcare.infrastructure.config.NewsTopicProperties;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NewsletterGenerationScheduler}.
 *
 * <p>Verifies the weekly draft-then-deliver pipeline (ingest across every
 * configured topic → generate, in that order) and the error-recovery guarantee
 * that no exception from any stage is allowed to propagate out of the scheduled
 * method. The FREE-tier digest is intentionally out of scope here — it is
 * delivered independently by {@link DigestDeliveryScheduler}.
 *
 * <p>No Spring context is loaded — dependencies are provided as Mockito mocks.
 *
 * @author  Bill Blackmon
 * @version 3.0
 * @since   2026-04-16
 * @updated 2026-08-24
 */
@ExtendWith(MockitoExtension.class)
class NewsletterGenerationSchedulerTest {

    private static final String         TITLE                   = "AI in Healthcare Weekly";
    private static final NewsletterTone TONE                    = NewsletterTone.PROFESSIONAL;
    private static final List<String>   TOPICS                  = List.of("General AI Healthcare News", "Anthropic Healthcare");
    private static final int            MAX_ARTICLES_PER_TOPIC  = 20;
    private static final int            MAX_SECTIONS_PER_TOPIC  = 3;

    @Mock
    private IngestArticlesUseCase ingestUseCase;

    @Mock
    private GenerateNewsletterUseCase generateUseCase;

    @Mock
    private DeliverNewsletterUseCase deliverUseCase;

    @Mock
    private NewsletterAutoSendPort autoSendPort;

    @Mock
    private NewsTopicProperties newsTopicProperties;

    private NewsletterGenerationScheduler scheduler;

    @BeforeEach
    void setUp() {
        lenient().when(newsTopicProperties.getTopics()).thenReturn(TOPICS);

        scheduler = new NewsletterGenerationScheduler(
                ingestUseCase,
                generateUseCase,
                deliverUseCase,
                autoSendPort,
                newsTopicProperties,
                TITLE,
                TONE,
                MAX_ARTICLES_PER_TOPIC,
                MAX_SECTIONS_PER_TOPIC);
    }

    // -------------------------------------------------------------------------
    // runWeeklyDraftGeneration() — happy path
    // -------------------------------------------------------------------------

    @Test
    void runWeeklyDraftGeneration_invokesIngestThenGenerateInOrder() {
        scheduler.runWeeklyDraftGeneration();

        InOrder ordered = inOrder(ingestUseCase, generateUseCase);
        ordered.verify(ingestUseCase).ingest(anyString(), any(LocalDate.class), anyList(), anyInt());
        ordered.verify(generateUseCase).generate(anyString(), anyString(), anyString(), any(NewsletterTone.class), anyInt(), anyBoolean(), anyInt());
    }

    @Test
    void runWeeklyDraftGeneration_passesFullTopicListAndConfiguredParameters() {
        scheduler.runWeeklyDraftGeneration();

        verify(ingestUseCase).ingest(
                anyString(),
                any(LocalDate.class),
                eq(TOPICS),
                eq(MAX_ARTICLES_PER_TOPIC));
        verify(generateUseCase).generate(
                anyString(),
                anyString(),
                argThat(t -> t.startsWith(TITLE)),
                eq(TONE),
                eq(MAX_SECTIONS_PER_TOPIC),
                eq(false),
                eq(3));
    }

    @Test
    void runWeeklyDraftGeneration_titleIncludesWeekOfDate() {
        scheduler.runWeeklyDraftGeneration();

        verify(generateUseCase).generate(
                anyString(), anyString(),
                argThat(t -> t.contains("Week of")),
                any(NewsletterTone.class), anyInt(), anyBoolean(), anyInt());
    }

    // -------------------------------------------------------------------------
    // runWeeklyDraftGeneration() — exception handling
    // -------------------------------------------------------------------------

    @Test
    void runWeeklyDraftGeneration_ingestThrows_doesNotCallGenerateAndDoesNotPropagate() {
        doThrow(new RuntimeException("ingest failure"))
                .when(ingestUseCase).ingest(anyString(), any(LocalDate.class), anyList(), anyInt());

        assertThatCode(() -> scheduler.runWeeklyDraftGeneration()).doesNotThrowAnyException();

        verify(generateUseCase, never()).generate(anyString(), anyString(), anyString(), any(NewsletterTone.class), anyInt(), anyBoolean(), anyInt());
    }

    @Test
    void runWeeklyDraftGeneration_generateThrows_doesNotPropagate() {
        doThrow(new RuntimeException("generate failure"))
                .when(generateUseCase).generate(anyString(), anyString(), anyString(), any(NewsletterTone.class), anyInt(), anyBoolean(), anyInt());

        assertThatCode(() -> scheduler.runWeeklyDraftGeneration()).doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // Auto-send behavior
    // -------------------------------------------------------------------------

    @Test
    void runWeeklyDraftGeneration_noOverride_autoSendsNewsletter() {
        when(autoSendPort.isOverriddenForDate(any(LocalDate.class))).thenReturn(false);

        scheduler.runWeeklyDraftGeneration();

        verify(deliverUseCase).deliver(argThat(id -> id.startsWith("draft-")));
    }

    @Test
    void runWeeklyDraftGeneration_overrideActive_doesNotAutoSend() {
        when(autoSendPort.isOverriddenForDate(any(LocalDate.class))).thenReturn(true);

        scheduler.runWeeklyDraftGeneration();

        verify(deliverUseCase, never()).deliver(anyString());
    }

    @Test
    void runWeeklyDraftGeneration_deliveryThrows_doesNotPropagate() {
        when(autoSendPort.isOverriddenForDate(any(LocalDate.class))).thenReturn(false);
        doThrow(new RuntimeException("delivery failure"))
                .when(deliverUseCase).deliver(anyString());

        assertThatCode(() -> scheduler.runWeeklyDraftGeneration()).doesNotThrowAnyException();
    }
}
