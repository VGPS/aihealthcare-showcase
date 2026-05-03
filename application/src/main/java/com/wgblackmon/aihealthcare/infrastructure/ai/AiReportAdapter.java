package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.port.outbound.AiReportPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * Spring AI adapter that implements {@link AiReportPort} by forwarding a free-form
 * prompt to the configured LLM via Spring AI's {@link ChatClient}.
 *
 * <p>Unlike {@link AiSummarizationAdapter}, which builds structured article-summary
 * prompts and parses the response into domain objects, this adapter is intentionally
 * thin: it accepts any prompt string, sends it verbatim to the model, and returns the
 * raw response string.  The caller is responsible for prompt construction and response
 * interpretation.
 *
 * <p>The same {@link ChatClient} instance is shared with {@link AiSummarizationAdapter}
 * via the auto-configured {@link ChatClient.Builder} — no additional Spring AI configuration
 * is required.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-02
 * @updated 2026-05-02
 */
@Slf4j
@Component
public class AiReportAdapter implements AiReportPort {

    private final ChatClient chatClient;

    /**
     * Constructs the adapter and builds the shared {@link ChatClient}.
     *
     * @param chatClientBuilder Auto-configured builder provided by Spring AI.
     */
    public AiReportAdapter(ChatClient.Builder chatClientBuilder) {
        log.debug("AiReportAdapter() | chatClientBuilder={}", chatClientBuilder);
        this.chatClient = chatClientBuilder.build();
        log.debug("AiReportAdapter() | return=void");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sends {@code promptText} as the user message to the LLM and returns
     * the model's complete response string.
     *
     * @throws IllegalArgumentException if {@code promptText} is blank.
     */
    @Override
    public String generate(String promptText) {
        log.debug("generate() | promptLength={} chars", promptText == null ? 0 : promptText.length());

        if (promptText == null || promptText.isBlank()) {
            throw new IllegalArgumentException("promptText must not be blank");
        }

        log.info("generate() | sending prompt to LLM ({} chars)", promptText.length());
        String result = chatClient.prompt(promptText).call().content();
        log.info("generate() | received LLM response ({} chars)", result == null ? 0 : result.length());

        if (result == null) {
            result = "";
        }

        log.debug("generate() | return={} chars", result.length());
        return result;
    }
}
