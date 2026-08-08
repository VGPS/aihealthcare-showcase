package com.wgblackmon.aihealthcare.domain.port.outbound;

/**
 * Outbound port — send a free-form prompt to an AI model and receive the full response text.
 *
 * <p>Unlike {@link AiSummarizationPort}, which is article-centric and returns structured
 * domain objects, this port is intentionally generic: it accepts any prompt string and
 * returns whatever the model produces (typically HTML, Markdown, or plain text).
 *
 * <p>This design supports use cases where the AI is asked to perform open-ended generation
 * — such as producing a market intelligence report — rather than summarizing a fixed set
 * of articles into a predefined schema.
 *
 * <p>Implementations live in {@code infrastructure/ai}. Unit tests inject a mock so no
 * real AI calls are made outside the {@code ai-integration} Spring profile.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-02
 * @updated 2026-05-02
 */
public interface AiReportPort {

    /**
     * Send {@code promptText} to the configured AI model and return the full response.
     *
     * @param promptText The complete prompt to send; must not be blank.
     * @return The model's response as a raw string; never null.
     */
    String generate(String promptText);
}
