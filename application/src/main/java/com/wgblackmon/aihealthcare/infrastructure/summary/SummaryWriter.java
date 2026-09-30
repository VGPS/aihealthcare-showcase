package com.wgblackmon.aihealthcare.infrastructure.summary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * Stage 2 of the anti-slop pipeline: writes markdown prose from a structured extraction.
 *
 * <p>The writer LLM never sees raw source documents — only the {@link SummaryExtraction}
 * JSON and the house style guide.  This forces every prose claim to originate from an
 * extracted finding, eliminating the main source of padding and unsupported statements.
 *
 * <p>The house style file ({@code house-style.md}) is loaded fresh on each call so that
 * edits to the style guide take effect without a restart.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-29
 * @updated 2026-09-29
 */
@Slf4j
@Component
public class SummaryWriter {

    private final ChatClient          chatClient;
    private final PromptLoaderService promptLoader;
    private final ObjectMapper        mapper;

    public SummaryWriter(ChatClient.Builder chatClientBuilder,
                         PromptLoaderService promptLoader,
                         ObjectMapper mapper) {
        this.chatClient   = chatClientBuilder.build();
        this.promptLoader = promptLoader;
        this.mapper       = mapper;
    }

    /**
     * Generates markdown prose from the extraction.
     *
     * @param summaryType  e.g. "TOPIC_SUMMARY", "MARKET_ALERT"
     * @param extraction   structured facts from Stage 1
     * @param targetWords  target word count (writer stays within ±15%)
     * @return markdown string; never null
     */
    public String write(String summaryType,
                        SummaryExtraction extraction,
                        int targetWords) {
        log.debug("write() | summaryType={}, targetWords={}", summaryType, targetWords);

        String houseStyle      = promptLoader.load("house-style.md");
        String systemTemplate  = promptLoader.load("write-system.txt");
        String userTemplate    = promptLoader.load("write-user.txt");

        String systemPrompt = systemTemplate
                .replace("{houseStyle}", houseStyle)
                .replace("{targetWords}", String.valueOf(targetWords))
                .replace("{summaryType}", summaryType);

        String extractionJson;
        try {
            extractionJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(extraction);
        } catch (Exception e) {
            log.error("write() | failed to serialize extraction", e);
            extractionJson = extraction.toString();
        }

        String userPrompt = userTemplate.replace("{extractionJson}", extractionJson);
        String fullPrompt = systemPrompt + "\n\n" + userPrompt;

        String result = chatClient.prompt(fullPrompt).call().content();
        if (result == null) result = "";

        log.debug("write() | return length={}", result.length());
        return result.trim();
    }
}
