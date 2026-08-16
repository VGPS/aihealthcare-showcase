package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleBodyFormattingPort;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * Spring AI adapter implementing {@link ArticleBodyFormattingPort}.
 *
 * <p>Sends the article title and body to Claude with the
 * {@code prompts/article-format.txt} prompt, which instructs the model to
 * rewrite the text as clean paragraphs with {@code **bold**} markers around
 * key entities. Returns the raw LLM response; callers convert bold markers
 * to HTML.
 *
 * <p>Body text is capped at 3,000 characters before sending to stay within
 * a safe context budget for articles that include embedded metadata or boilerplate.
 *
 * @author  Bill Blackmon
 * @since   2026-08-16
 * @updated 2026-08-16
 */
@Slf4j
@Component
public class ArticleBodyFormattingAdapter implements ArticleBodyFormattingPort {

    private static final String PROMPT_FILE  = "article-format.txt";
    private static final int    MAX_BODY_LEN = 3000;

    private final ChatClient          chatClient;
    private final PromptLoaderService promptLoaderService;

    public ArticleBodyFormattingAdapter(ChatClient.Builder chatClientBuilder,
                                        PromptLoaderService promptLoaderService) {
        log.debug("ArticleBodyFormattingAdapter() | promptLoaderService={}",
                  promptLoaderService.getClass().getSimpleName());
        this.chatClient          = chatClientBuilder.build();
        this.promptLoaderService = promptLoaderService;
        log.debug("ArticleBodyFormattingAdapter() | return=void");
    }

    @Override
    public String formatWithEntityBolding(String title, String bodyText) {
        log.debug("formatWithEntityBolding() | title={}, bodyLen={}",
                  title, bodyText != null ? bodyText.length() : 0);

        if (bodyText == null || bodyText.isBlank()) {
            log.debug("formatWithEntityBolding() | return=empty (no body text)");
            return "";
        }

        try {
            String truncated = bodyText.length() > MAX_BODY_LEN
                    ? bodyText.substring(0, MAX_BODY_LEN) : bodyText;

            String prompt = promptLoaderService.load(PROMPT_FILE)
                    .replace("{title}",    title != null ? title : "")
                    .replace("{bodyText}", truncated);

            String response = chatClient.prompt(prompt).call().content();
            String result   = (response != null) ? response.trim() : "";

            log.debug("formatWithEntityBolding() | return={} chars", result.length());
            return result;

        } catch (Exception e) {
            log.warn("formatWithEntityBolding() | LLM call failed — returning empty", e);
            return "";
        }
    }
}
