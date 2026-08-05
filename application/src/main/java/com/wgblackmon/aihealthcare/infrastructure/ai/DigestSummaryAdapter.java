package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.DigestSummaryPort;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring AI adapter that generates an executive summary of daily articles
 * for the FREE-tier digest email.
 *
 * <p>Sends a numbered article list to the LLM with a summary prompt, expecting
 * 3-5 paragraphs of flowing prose with {@code [N]} citation markers. The prompt
 * template is loaded from {@code prompts/digest-summary.txt}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-05
 * @updated 2026-08-05
 */
@Slf4j
@Component
public class DigestSummaryAdapter implements DigestSummaryPort {

    private final ChatClient chatClient;
    private final PromptLoaderService promptLoaderService;

    public DigestSummaryAdapter(ChatClient.Builder chatClientBuilder,
                                 PromptLoaderService promptLoaderService) {
        log.debug("DigestSummaryAdapter() | chatClientBuilder={}, promptLoaderService={}",
                  chatClientBuilder, promptLoaderService.getClass().getSimpleName());
        this.chatClient = chatClientBuilder.build();
        this.promptLoaderService = promptLoaderService;
    }

    @Override
    public String generateDigestSummary(List<NewsArticle> articles) {
        log.debug("generateDigestSummary() | articleCount={}", articles != null ? articles.size() : 0);

        if (articles == null || articles.isEmpty()) {
            log.debug("generateDigestSummary() | return=empty (no articles)");
            return "";
        }

        String prompt = buildPrompt(articles);
        log.debug("generateDigestSummary() | prompt length={} chars", prompt.length());

        String response;
        try {
            response = chatClient.prompt(prompt).call().content();
        } catch (Exception e) {
            log.warn("generateDigestSummary() | LLM call failed: {}", e.getMessage());
            log.debug("generateDigestSummary() | return=empty (LLM error)");
            return "";
        }

        if (response == null || response.isBlank()) {
            log.warn("generateDigestSummary() | Empty LLM response");
            log.debug("generateDigestSummary() | return=empty (empty response)");
            return "";
        }

        log.debug("generateDigestSummary() | return={} chars", response.length());
        return response.trim();
    }

    String buildPrompt(List<NewsArticle> articles) {
        log.debug("buildPrompt() | articleCount={}", articles.size());

        String template = promptLoaderService.load("digest-summary.txt");

        StringBuilder numberedList = new StringBuilder();
        for (int i = 0; i < articles.size(); i++) {
            NewsArticle article = articles.get(i);
            numberedList.append("[").append(i + 1).append("] ");
            numberedList.append(article.title());
            if (article.bodyText() != null && !article.bodyText().isBlank()) {
                String snippet = article.bodyText();
                if (snippet.length() > 300) {
                    snippet = snippet.substring(0, 300) + "...";
                }
                numberedList.append(" — ").append(snippet);
            }
            numberedList.append("\n");
        }

        String result = template
                .replace("{articleCount}", String.valueOf(articles.size()))
                .replace("{numberedArticleList}", numberedList.toString().trim());

        log.debug("buildPrompt() | return=prompt ({} chars)", result.length());
        return result;
    }
}
