package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.LlmCallUsage;
import com.wgblackmon.aihealthcare.domain.port.outbound.LlmCallUsagePort;
import com.wgblackmon.aihealthcare.infrastructure.scheduler.PipelineContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.time.Instant;

/**
 * Spring AI {@link CallAdvisor} that records token usage for every LLM call
 * made through the default {@code ChatClient.Builder}.
 *
 * <p>Attached globally via a {@code ChatClientCustomizer} bean in
 * {@code AppConfig}, so every adapter built from the shared (Anthropic)
 * {@code ChatClient.Builder} is covered without adapter-level changes. Reads
 * {@link PipelineContext#get()} to attribute the call to whichever scheduled
 * pipeline is currently executing on this thread.
 *
 * <p>Persistence and logging failures are swallowed — this advisor must never
 * break the underlying LLM call it wraps.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
@Slf4j
public class TokenUsageLoggingAdvisor implements CallAdvisor {

    private final LlmCallUsagePort llmCallUsagePort;

    public TokenUsageLoggingAdvisor(LlmCallUsagePort llmCallUsagePort) {
        log.debug("TokenUsageLoggingAdvisor() | llmCallUsagePort={}", llmCallUsagePort.getClass().getSimpleName());
        this.llmCallUsagePort = llmCallUsagePort;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        log.debug("adviseCall() | pipelineId={}", PipelineContext.get());

        ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);

        try {
            recordUsage(response);
        } catch (Exception e) {
            log.warn("adviseCall() | failed to record token usage: {}", e.getMessage());
        }

        log.debug("adviseCall() | return=response");
        return response;
    }

    private void recordUsage(ChatClientResponse response) {
        if (response == null) {
            return;
        }
        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse == null) {
            return;
        }
        ChatResponseMetadata metadata = chatResponse.getMetadata();
        if (metadata == null) {
            return;
        }
        Usage usage = metadata.getUsage();
        if (usage == null || usage instanceof EmptyUsage) {
            // Spring AI defaults ChatResponseMetadata.usage to a non-null EmptyUsage
            // (returns 0 for every field) rather than null when a response genuinely
            // carries no token accounting — treat that placeholder the same as absent.
            return;
        }

        String pipelineId = PipelineContext.get();
        String model = metadata.getModel();
        int promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        int outputTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
        int totalTokens = usage.getTotalTokens() != null ? usage.getTotalTokens() : promptTokens + outputTokens;

        LlmCallUsage callUsage = new LlmCallUsage(
                null, pipelineId, model != null ? model : "unknown",
                promptTokens, outputTokens, totalTokens, Instant.now());

        llmCallUsagePort.save(callUsage);

        log.info("llm usage | pipelineId={}, model={}, promptTokens={}, outputTokens={}, totalTokens={}",
                pipelineId, callUsage.model(), promptTokens, outputTokens, totalTokens);
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
