package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.AiSearchSynthesis;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSearchPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Anthropic Claude adapter for AI-enhanced search synthesis.
 *
 * <p>Implements {@link AiSearchPort} using the Anthropic {@link ChatModel}
 * (resolved via {@code @Qualifier("anthropicChatModel")}). Builds a dedicated
 * {@link ChatClient} for search-synthesis prompts, separate from the shared
 * client used by {@link AiSummarizationAdapter}.
 *
 * <p>Delegates prompt building and response parsing to the shared
 * {@link AiSearchResponseParser} utility, which centralizes the
 * template loading and structured response extraction logic.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-02
 * @updated 2026-09-11
 */
@Slf4j
@Component
public class AnthropicAiSearchAdapter implements AiSearchPort {

    private static final String MODEL_NAME = "Claude";

    private final ChatClient chatClient;
    private final AiSearchResponseParser responseParser;
    private final String modelId;
    private final String apiKey;

    /**
     * Constructs the adapter with the Anthropic-specific chat model.
     *
     * @param anthropicChatModel the auto-configured Anthropic chat model
     * @param responseParser     shared parser for prompt building and response extraction
     * @param modelId            the configured Anthropic model ID (from application.yml)
     * @param apiKey             Anthropic API key
     */
    public AnthropicAiSearchAdapter(
            @Qualifier("anthropicChatModel") ChatModel anthropicChatModel,
            AiSearchResponseParser responseParser,
            @Value("${spring.ai.anthropic.chat.options.model:claude-sonnet-4-6}") String modelId,
            @Value("${ANTHROPIC_API_KEY:}") String apiKey) {
        log.debug("AnthropicAiSearchAdapter() | model={}, responseParser={}, modelId={}, apiKeyPresent={}",
                  anthropicChatModel.getClass().getSimpleName(),
                  responseParser.getClass().getSimpleName(), modelId,
                  apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("placeholder-set-"));
        this.chatClient = ChatClient.builder(anthropicChatModel).build();
        this.responseParser = responseParser;
        this.modelId = modelId;
        this.apiKey = apiKey;
        log.debug("AnthropicAiSearchAdapter() | return=void");
    }

    @Override
    public AiSearchSynthesis synthesize(String query, List<NewsArticle> articles) {
        log.debug("synthesize() | query={}, articleCount={}", query, articles.size());

        String prompt = responseParser.buildPrompt(query, articles);
        log.info("synthesize() | sending prompt to Claude ({} chars)", prompt.length());

        String response = chatClient.prompt(prompt).call().content();
        log.info("synthesize() | received Claude response ({} chars)",
                 response == null ? 0 : response.length());

        AiSearchSynthesis result = responseParser.parseResponse(response, MODEL_NAME);
        log.debug("synthesize() | return={}", result);
        return result;
    }

    @Override
    public String modelName() {
        return MODEL_NAME;
    }

    @Override
    public String modelId() {
        return modelId;
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("placeholder-set-");
    }

}
