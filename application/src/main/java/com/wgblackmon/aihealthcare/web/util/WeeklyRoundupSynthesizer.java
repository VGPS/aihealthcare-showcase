package com.wgblackmon.aihealthcare.web.util;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * LLM-powered narrative synthesizer for the weekly roundup.
 *
 * <p>Takes a list of curated articles and produces an original analytical
 * narrative suitable for LinkedIn, Facebook, and Substack — replacing the
 * previous numbered-headline format with cohesive expert commentary.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-19
 * @updated 2026-09-19
 */
@Slf4j
@Component
public class WeeklyRoundupSynthesizer {

    private final ChatClient chatClient;
    private final String promptTemplate;

    public WeeklyRoundupSynthesizer(ChatClient.Builder chatClientBuilder,
                                    PromptLoaderService promptLoaderService) {
        log.debug("WeeklyRoundupSynthesizer() | chatClientBuilder={}, promptLoaderService={}",
                chatClientBuilder, promptLoaderService);
        this.chatClient = chatClientBuilder.build();
        this.promptTemplate = promptLoaderService.load("weekly-roundup-narrative.txt");
    }

    /**
     * Synthesize articles into an analytical narrative for LinkedIn/Facebook.
     *
     * @param articles        curated, sorted articles (max 10)
     * @param dateRange       display date range (e.g. "September 14 - September 19, 2026")
     * @param legalCount      number of legal/regulatory articles
     * @param competitorCount number of competitor articles
     * @return LLM-generated analytical narrative (plain text, no markdown)
     */
    public String synthesizeNarrative(List<NewsArticle> articles, String dateRange,
                                     int legalCount, int competitorCount) {
        log.debug("synthesizeNarrative() | articles={}, dateRange={}, legal={}, competitor={}",
                articles.size(), dateRange, legalCount, competitorCount);

        String articleBlock = buildArticleBlock(articles);

        String prompt = promptTemplate
                .replace("{articles}", articleBlock)
                .replace("{dateRange}", dateRange)
                .replace("{legalCount}", String.valueOf(legalCount))
                .replace("{competitorCount}", String.valueOf(competitorCount));

        String result = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        if (result == null || result.isBlank()) {
            log.warn("synthesizeNarrative() | LLM returned empty response, falling back");
            result = "AI healthcare saw " + articles.size() + " significant developments this week across legal, regulatory, and competitive fronts.";
        }

        result = result.trim();
        log.debug("synthesizeNarrative() | return=length:{}", result.length());
        return result;
    }

    private String buildArticleBlock(List<NewsArticle> articles) {
        log.debug("buildArticleBlock() | articles={}", articles.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < articles.size(); i++) {
            NewsArticle a = articles.get(i);
            sb.append(i + 1).append(". ");
            sb.append("[").append(tierLabel(a)).append("] ");
            sb.append(a.title() != null ? a.title() : "Untitled");
            sb.append("\n");
            if (a.bodyText() != null && !a.bodyText().isBlank()) {
                String body = a.bodyText().trim();
                if (body.length() > 300) {
                    body = body.substring(0, 300) + "...";
                }
                sb.append("   ").append(body).append("\n");
            }
            if (a.sourceName() != null && !a.sourceName().isBlank()) {
                sb.append("   Source: ").append(a.sourceName()).append("\n");
            }
            sb.append("\n");
        }
        String result = sb.toString();
        log.debug("buildArticleBlock() | return=length:{}", result.length());
        return result;
    }

    private String tierLabel(NewsArticle a) {
        if ("LEGAL".equalsIgnoreCase(a.sourceTier())) {
            return "LEGAL";
        }
        return "COMPETITOR";
    }
}
