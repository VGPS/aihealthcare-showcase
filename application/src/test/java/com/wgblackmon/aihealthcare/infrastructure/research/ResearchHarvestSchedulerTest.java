package com.wgblackmon.aihealthcare.infrastructure.research;

import com.wgblackmon.aihealthcare.domain.model.ResearchAnswer;
import com.wgblackmon.aihealthcare.domain.model.ResearchMode;
import com.wgblackmon.aihealthcare.domain.model.ResearchRequest;
import com.wgblackmon.aihealthcare.domain.model.ResearchSection;
import com.wgblackmon.aihealthcare.domain.port.inbound.ConductResearchUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ResearchHarvestScheduler}.
 *
 * <p>Verifies that the scheduler calls {@link ConductResearchUseCase#conduct}
 * once per configured topic with the correct mode and that per-topic failures
 * are swallowed without aborting the remaining topics.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-07
 * @updated 2026-05-07
 */
@ExtendWith(MockitoExtension.class)
class ResearchHarvestSchedulerTest {

    @Mock private ConductResearchUseCase conductResearchUseCase;

    private ResearchHarvestProperties properties;
    private ResearchHarvestScheduler  scheduler;

    @BeforeEach
    void setUp() {
        properties = new ResearchHarvestProperties();
        properties.setMaxSourcesPerTopic(20);
        scheduler  = new ResearchHarvestScheduler(conductResearchUseCase, properties);
    }

    @Test
    void harvestResearchTopics_noTopics_skipsWithoutCalling() {
        properties.setTopics(Collections.emptyList());

        scheduler.harvestResearchTopics();

        verify(conductResearchUseCase, never()).conduct(any());
    }

    @Test
    void harvestResearchTopics_callsConductOncePerTopic() {
        properties.setTopics(List.of("AI diagnostics", "AI radiology", "AI oncology"));
        when(conductResearchUseCase.conduct(any())).thenReturn(stubAnswer());

        scheduler.harvestResearchTopics();

        verify(conductResearchUseCase, times(3)).conduct(any());
    }

    @Test
    void harvestResearchTopics_usesCombinedMode() {
        properties.setTopics(List.of("AI Healthcare"));
        when(conductResearchUseCase.conduct(any())).thenReturn(stubAnswer());

        scheduler.harvestResearchTopics();

        ArgumentCaptor<ResearchRequest> captor = ArgumentCaptor.forClass(ResearchRequest.class);
        verify(conductResearchUseCase).conduct(captor.capture());
        assertThat(captor.getValue().mode()).isEqualTo(ResearchMode.COMBINED);
    }

    @Test
    void harvestResearchTopics_usesConfiguredMaxSources() {
        properties.setMaxSourcesPerTopic(15);
        properties.setTopics(List.of("OpenAI Healthcare"));
        when(conductResearchUseCase.conduct(any())).thenReturn(stubAnswer());

        scheduler.harvestResearchTopics();

        ArgumentCaptor<ResearchRequest> captor = ArgumentCaptor.forClass(ResearchRequest.class);
        verify(conductResearchUseCase).conduct(captor.capture());
        assertThat(captor.getValue().maxSources()).isEqualTo(15);
    }

    @Test
    void harvestResearchTopics_singleTopicFailure_remainingTopicsStillRun() {
        properties.setTopics(List.of("Topic A", "Topic B", "Topic C"));
        when(conductResearchUseCase.conduct(any()))
                .thenThrow(new RuntimeException("AI call failed"))
                .thenReturn(stubAnswer())
                .thenReturn(stubAnswer());

        // Should not throw — failures are swallowed
        scheduler.harvestResearchTopics();

        // All three topics attempted despite the first failure
        verify(conductResearchUseCase, times(3)).conduct(any());
    }

    @Test
    void harvestResearchTopics_passesTopicAsQuery() {
        properties.setTopics(List.of("Anthropic Healthcare"));
        when(conductResearchUseCase.conduct(any())).thenReturn(stubAnswer());

        scheduler.harvestResearchTopics();

        ArgumentCaptor<ResearchRequest> captor = ArgumentCaptor.forClass(ResearchRequest.class);
        verify(conductResearchUseCase).conduct(captor.capture());
        assertThat(captor.getValue().query()).isEqualTo("Anthropic Healthcare");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ResearchAnswer stubAnswer() {
        return new ResearchAnswer(
                "answer-id",
                "stub query",
                List.of(new ResearchSection("Findings", "body", Collections.emptyList())),
                Collections.emptyList(),
                Instant.now());
    }
}
