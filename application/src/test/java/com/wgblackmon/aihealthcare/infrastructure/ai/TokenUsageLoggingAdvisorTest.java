package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.LlmCallUsage;
import com.wgblackmon.aihealthcare.domain.port.outbound.LlmCallUsagePort;
import com.wgblackmon.aihealthcare.infrastructure.scheduler.PipelineContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TokenUsageLoggingAdvisor}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
@ExtendWith(MockitoExtension.class)
class TokenUsageLoggingAdvisorTest {

    @Mock
    private LlmCallUsagePort llmCallUsagePort;

    @Mock
    private ChatClientRequest chatClientRequest;

    @Mock
    private CallAdvisorChain callAdvisorChain;

    private TokenUsageLoggingAdvisor advisor;

    @AfterEach
    void tearDown() {
        PipelineContext.clear();
    }

    private ChatClientResponse responseWithUsage(String model, int promptTokens, int outputTokens) {
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model(model)
                .usage(new DefaultUsage(promptTokens, outputTokens))
                .build();
        ChatResponse chatResponse = new ChatResponse(List.of(), metadata);
        return new ChatClientResponse(chatResponse, java.util.Map.of());
    }

    @Test
    void adviseCall_recordsUsageTaggedWithCurrentPipelineContext() {
        advisor = new TokenUsageLoggingAdvisor(llmCallUsagePort);
        PipelineContext.set("legal-trends");

        ChatClientResponse response = responseWithUsage("claude-sonnet-4-6", 1000, 200);
        when(callAdvisorChain.nextCall(chatClientRequest)).thenReturn(response);

        ChatClientResponse result = advisor.adviseCall(chatClientRequest, callAdvisorChain);

        assertThat(result).isSameAs(response);

        ArgumentCaptor<LlmCallUsage> captor = ArgumentCaptor.forClass(LlmCallUsage.class);
        verify(llmCallUsagePort).save(captor.capture());
        LlmCallUsage saved = captor.getValue();
        assertThat(saved.pipelineId()).isEqualTo("legal-trends");
        assertThat(saved.model()).isEqualTo("claude-sonnet-4-6");
        assertThat(saved.promptTokens()).isEqualTo(1000);
        assertThat(saved.outputTokens()).isEqualTo(200);
        assertThat(saved.totalTokens()).isEqualTo(1200);
    }

    @Test
    void adviseCall_withNoPipelineContextSet_tagsAsInteractive() {
        advisor = new TokenUsageLoggingAdvisor(llmCallUsagePort);

        ChatClientResponse response = responseWithUsage("claude-sonnet-4-6", 500, 50);
        when(callAdvisorChain.nextCall(chatClientRequest)).thenReturn(response);

        advisor.adviseCall(chatClientRequest, callAdvisorChain);

        ArgumentCaptor<LlmCallUsage> captor = ArgumentCaptor.forClass(LlmCallUsage.class);
        verify(llmCallUsagePort).save(captor.capture());
        assertThat(captor.getValue().pipelineId()).isEqualTo(PipelineContext.UNTAGGED);
    }

    @Test
    void adviseCall_missingUsageMetadata_skipsSaveWithoutThrowing() {
        advisor = new TokenUsageLoggingAdvisor(llmCallUsagePort);

        ChatResponseMetadata metadata = ChatResponseMetadata.builder().model("claude-sonnet-4-6").build();
        ChatResponse chatResponse = new ChatResponse(List.of(), metadata);
        ChatClientResponse response = new ChatClientResponse(chatResponse, java.util.Map.of());
        when(callAdvisorChain.nextCall(chatClientRequest)).thenReturn(response);

        ChatClientResponse result = advisor.adviseCall(chatClientRequest, callAdvisorChain);

        assertThat(result).isSameAs(response);
        verifyNoInteractions(llmCallUsagePort);
    }

    @Test
    void adviseCall_nullChatResponse_skipsSaveWithoutThrowing() {
        advisor = new TokenUsageLoggingAdvisor(llmCallUsagePort);

        ChatClientResponse response = new ChatClientResponse(null, java.util.Map.of());
        when(callAdvisorChain.nextCall(chatClientRequest)).thenReturn(response);

        ChatClientResponse result = advisor.adviseCall(chatClientRequest, callAdvisorChain);

        assertThat(result).isSameAs(response);
        verifyNoInteractions(llmCallUsagePort);
    }

    @Test
    void adviseCall_persistenceFailureDoesNotBreakTheCall() {
        advisor = new TokenUsageLoggingAdvisor(llmCallUsagePort);

        ChatClientResponse response = responseWithUsage("claude-sonnet-4-6", 1000, 200);
        when(callAdvisorChain.nextCall(chatClientRequest)).thenReturn(response);
        org.mockito.Mockito.doThrow(new RuntimeException("db down")).when(llmCallUsagePort).save(any());

        ChatClientResponse result = advisor.adviseCall(chatClientRequest, callAdvisorChain);

        assertThat(result).isSameAs(response);
    }

    @Test
    void getName_returnsSimpleClassName() {
        advisor = new TokenUsageLoggingAdvisor(llmCallUsagePort);
        assertThat(advisor.getName()).isEqualTo("TokenUsageLoggingAdvisor");
    }
}
