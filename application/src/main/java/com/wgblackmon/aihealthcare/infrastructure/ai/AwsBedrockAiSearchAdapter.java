package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.AiSearchSynthesis;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSearchPort;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * AWS Bedrock adapter for AI-enhanced search synthesis.
 *
 * <p>Implements {@link AiSearchPort} using the AWS Bedrock Converse API
 * via Spring AI's auto-configured {@link ChatModel}. Builds a dedicated
 * {@link ChatClient} for search-synthesis prompts.
 *
 * <p>Shares the same prompt template ({@code ai-search-synthesis.txt}) and
 * response parsing logic as {@link AnthropicAiSearchAdapter} and
 * {@link OpenAiSearchAdapter}, enabling direct side-by-side comparison of
 * model outputs across all five providers.
 *
 * <p>Only created when {@code aihealthcare.aws.bedrock.enabled=true}.
 * When disabled, this bean is not registered and AWS will not appear
 * in the available model list.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-07
 * @updated 2026-07-10
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "aihealthcare.aws.bedrock.enabled", havingValue = "true")
public class AwsBedrockAiSearchAdapter implements AiSearchPort {

    private static final String MODEL_NAME = "Amazon/AWS";

    private final ChatClient chatClient;
    private final PromptLoaderService promptLoaderService;
    private final String modelId;

    /**
     * Constructs the adapter with the Bedrock Converse chat model
     * (resolved via {@code @Qualifier("bedrockProxyChatModel")}).
     *
     * @param bedrockChatModel    the auto-configured Bedrock Converse chat model
     * @param promptLoaderService service for loading prompt templates
     * @param modelId             the configured Bedrock model ID (from application.yml)
     */
    public AwsBedrockAiSearchAdapter(
            @Qualifier("bedrockProxyChatModel") ChatModel bedrockChatModel,
            PromptLoaderService promptLoaderService,
            @Value("${spring.ai.bedrock.converse.chat.options.model:amazon.nova-lite-v1:0}") String modelId) {
        log.debug("AwsBedrockAiSearchAdapter() | model={}, promptLoaderService={}, modelId={}",
                  bedrockChatModel.getClass().getSimpleName(),
                  promptLoaderService.getClass().getSimpleName(), modelId);
        this.chatClient = ChatClient.builder(bedrockChatModel).build();
        this.promptLoaderService = promptLoaderService;
        this.modelId = modelId;
        log.info("AwsBedrockAiSearchAdapter() | AWS Bedrock active for AI search (model={})", modelId);
        log.debug("AwsBedrockAiSearchAdapter() | return=void");
    }

    @Override
    public AiSearchSynthesis synthesize(String query, List<NewsArticle> articles) {
        log.debug("synthesize() | query={}, articleCount={}", query, articles.size());

        String prompt = buildPrompt(query, articles);
        log.info("synthesize() | sending prompt to AWS Bedrock ({} chars)", prompt.length());

        String response = chatClient.prompt(prompt).call().content();
        log.info("synthesize() | received AWS Bedrock response ({} chars)",
                 response == null ? 0 : response.length());

        AiSearchSynthesis result = parseResponse(response);
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

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String buildPrompt(String query, List<NewsArticle> articles) {
        log.debug("buildPrompt() | query={}, articleCount={}", query, articles.size());

        StringBuilder articlesBlock = new StringBuilder();
        int index = 1;
        for (NewsArticle article : articles) {
            articlesBlock.append("[").append(index).append("] Title: ").append(article.title()).append("\n");
            if (article.author() != null && !article.author().isBlank()) {
                articlesBlock.append("    Author: ").append(article.author()).append("\n");
            }
            if (article.sourceName() != null && !article.sourceName().isBlank()) {
                articlesBlock.append("    Source: ").append(article.sourceName()).append("\n");
            }
            String body = article.bodyText() != null ? article.bodyText() : "";
            if (body.length() > 500) {
                body = body.substring(0, 500) + "...";
            }
            articlesBlock.append("    Body:   ").append(body).append("\n\n");
            index++;
        }

        String template = promptLoaderService.load("ai-search-synthesis.txt");
        String result = template
                .replace("{query}", query)
                .replace("{articleCount}", String.valueOf(articles.size()))
                .replace("{articles}", articlesBlock.toString().trim());

        log.debug("buildPrompt() | return=prompt[{} chars]", result.length());
        return result;
    }

    private AiSearchSynthesis parseResponse(String response) {
        log.debug("parseResponse() | responseLength={}", response == null ? 0 : response.length());

        if (response == null || response.isBlank()) {
            log.warn("parseResponse() | empty response from AWS Bedrock");
            AiSearchSynthesis result = new AiSearchSynthesis(
                    MODEL_NAME, "No synthesis available.", new ArrayList<>(), Instant.now());
            log.debug("parseResponse() | return={}", result);
            return result;
        }

        if (response.trim().startsWith("NO_MATCH")) {
            log.info("parseResponse() | AWS Bedrock reported NO_MATCH — articles not relevant to query");
            log.debug("parseResponse() | return=null");
            return null;
        }

        StringBuilder summaryBuilder = new StringBuilder();
        List<String> keyFindings = new ArrayList<>();
        boolean inSummary = false;
        boolean inFindings = false;

        for (String line : response.split("\n")) {
            String trimmed = line.trim();

            if (trimmed.startsWith("SUMMARY:")) {
                summaryBuilder.append(trimmed.substring("SUMMARY:".length()).trim());
                inSummary = true;
                inFindings = false;
            } else if (trimmed.equals("KEY_FINDINGS:")) {
                inSummary = false;
                inFindings = true;
            } else if (inSummary && !trimmed.isEmpty()) {
                summaryBuilder.append(" ").append(trimmed);
            } else if (inFindings && trimmed.startsWith("- ")) {
                keyFindings.add(trimmed.substring(2).trim());
            } else if (inFindings && trimmed.startsWith("* ")) {
                keyFindings.add(trimmed.substring(2).trim());
            }
        }

        String summary = summaryBuilder.toString().trim();
        if (summary.isEmpty()) {
            log.warn("parseResponse() | SUMMARY line not found in AWS Bedrock response; using full response as summary");
            summary = response.length() > 4000 ? response.substring(0, 4000) + "..." : response;
        }

        AiSearchSynthesis result = new AiSearchSynthesis(MODEL_NAME, summary, keyFindings, Instant.now());
        log.debug("parseResponse() | return=AiSearchSynthesis[findings={}]", keyFindings.size());
        return result;
    }
}
