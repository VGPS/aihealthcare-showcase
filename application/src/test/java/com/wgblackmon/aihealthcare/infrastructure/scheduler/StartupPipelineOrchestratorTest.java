package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.PipelineRunEvent;
import com.wgblackmon.aihealthcare.domain.model.PipelineStepStatus;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectTrendsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.PipelinePausePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.PipelineRunEventPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StartupPipelineOrchestrator}'s safe-stop behavior.
 *
 * <p>Verifies the {@code pipeline-cascade} pause kill switch halts every
 * remaining step (recorded as {@code SKIPPED}) without running it, and that
 * an unpaused run proceeds normally and records a non-SKIPPED outcome.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
@ExtendWith(MockitoExtension.class)
class StartupPipelineOrchestratorTest {

    @Mock
    private DetectTrendsUseCase detectTrendsUseCase;

    @Mock
    private PipelineRunEventPort pipelineRunEventPort;

    @Mock
    private PipelinePausePort pausePort;

    private StartupPipelineOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new StartupPipelineOrchestrator(
                null, null, null, null, null, null, null,
                detectTrendsUseCase, null, null,
                pipelineRunEventPort, null, null, null, null, null,
                null, null, pausePort);
    }

    @Test
    void runAllPipelines_paused_skipsEveryStepAndNeverInvokesDetectTrends() {
        when(pausePort.isPaused("pipeline-cascade")).thenReturn(true);

        orchestrator.runAllPipelines();

        verify(detectTrendsUseCase, never()).detectTrends();

        ArgumentCaptor<PipelineRunEvent> captor = ArgumentCaptor.forClass(PipelineRunEvent.class);
        verify(pipelineRunEventPort, atLeastOnce()).save(captor.capture());
        List<PipelineRunEvent> events = captor.getAllValues();
        assertThat(events).isNotEmpty();
        assertThat(events).extracting(PipelineRunEvent::status)
                .containsOnly(PipelineStepStatus.SKIPPED);
    }

    @Test
    void runAllPipelines_notPaused_runsStepsAndInvokesDetectTrends() {
        when(pausePort.isPaused("pipeline-cascade")).thenReturn(false);

        orchestrator.runAllPipelines();

        verify(detectTrendsUseCase).detectTrends();

        ArgumentCaptor<PipelineRunEvent> captor = ArgumentCaptor.forClass(PipelineRunEvent.class);
        verify(pipelineRunEventPort, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(PipelineRunEvent::status)
                .contains(PipelineStepStatus.SUCCESS)
                .doesNotContain(PipelineStepStatus.SKIPPED);
    }

    @Test
    void runAllPipelines_pausePortNull_treatsAsNotPaused() {
        StartupPipelineOrchestrator noPausePortOrchestrator = new StartupPipelineOrchestrator(
                null, null, null, null, null, null, null,
                detectTrendsUseCase, null, null,
                pipelineRunEventPort, null, null, null, null, null,
                null, null, null);

        noPausePortOrchestrator.runAllPipelines();

        verify(detectTrendsUseCase).detectTrends();
    }
}
