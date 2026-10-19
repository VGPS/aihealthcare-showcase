package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import org.springframework.core.task.TaskDecorator;

/**
 * Propagates the calling thread's {@link PipelineContext} pipeline id onto whatever
 * worker thread a {@code ThreadPoolTaskExecutor} runs the submitted task on.
 *
 * <p>{@link PipelineContext} is a plain {@code ThreadLocal} and does not cross thread
 * boundaries on its own, so any executor that fans out independent branches of a
 * pipeline step (e.g. {@code ResearchOrchestratorService} running Perplexity and
 * legacy-DB retrieval concurrently) would otherwise have the worker-thread branch
 * silently mis-tagged as {@link PipelineContext#UNTAGGED} in the LLM token-usage
 * table. Attach via {@code ThreadPoolTaskExecutor.setTaskDecorator(...)}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
public class PipelineContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        String pipelineId = PipelineContext.get();
        return () -> {
            PipelineContext.set(pipelineId);
            try {
                runnable.run();
            } finally {
                PipelineContext.clear();
            }
        };
    }
}
