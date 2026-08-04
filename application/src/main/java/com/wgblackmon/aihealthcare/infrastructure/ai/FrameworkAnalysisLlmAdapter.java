package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.FrameworkAnalysis;
import com.wgblackmon.aihealthcare.domain.model.FrameworkDimension;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.FrameworkLlmPort;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI adapter that implements {@link FrameworkLlmPort} using a
 * large-language model to perform deep healthcare framework competitive
 * analysis across six scored dimensions.
 *
 * <p>Sends up to 50 articles with the framework analysis prompt,
 * parses the structured response to extract dimensional scores,
 * strengths, weaknesses, and recent developments.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
@Slf4j
@Component
public class FrameworkAnalysisLlmAdapter implements FrameworkLlmPort {

    private static final int MAX_ARTICLES = 50;

    private final ChatClient chatClient;
    private final PromptLoaderService promptLoaderService;

    public FrameworkAnalysisLlmAdapter(ChatClient.Builder chatClientBuilder,
                                       PromptLoaderService promptLoaderService) {
        log.debug("FrameworkAnalysisLlmAdapter() | chatClientBuilder={}, promptLoaderService={}",
                  chatClientBuilder, promptLoaderService.getClass().getSimpleName());
        this.chatClient = chatClientBuilder.build();
        this.promptLoaderService = promptLoaderService;
        log.debug("FrameworkAnalysisLlmAdapter() | return=void");
    }

    @Override
    public FrameworkAnalysis analyze(String companySlug, String companyName,
                                     List<NewsArticle> articles) {
        log.debug("analyze() | companySlug={}, companyName={}, articleCount={}",
                  companySlug, companyName, articles.size());

        if (articles == null || articles.isEmpty()) {
            log.debug("analyze() | return=null (no articles)");
            return null;
        }

        List<NewsArticle> capped = articles;
        if (articles.size() > MAX_ARTICLES) {
            capped = articles.subList(0, MAX_ARTICLES);
        }

        String prompt = buildPrompt(capped, companyName);

        String response;
        try {
            response = chatClient.prompt(prompt).call().content();
        } catch (Exception e) {
            log.warn("analyze() | LLM call failed for {}: {}", companyName, e.getMessage());
            log.debug("analyze() | return=null (LLM error)");
            return null;
        }

        if (response == null || response.isBlank()) {
            log.warn("analyze() | Empty LLM response for {}", companyName);
            log.debug("analyze() | return=null (empty response)");
            return null;
        }

        log.debug("analyze() | LLM response length={} chars", response.length());
        FrameworkAnalysis result = parseResponse(response, companySlug, companyName, capped.size());
        log.debug("analyze() | return={}", result != null ? result.companySlug() : "null");
        return result;
    }

    String buildPrompt(List<NewsArticle> articles, String companyName) {
        log.debug("buildPrompt() | articleCount={}, companyName={}", articles.size(), companyName);

        String template = promptLoaderService.load("framework-analysis.txt");

        StringBuilder numberedList = new StringBuilder();
        for (int i = 0; i < articles.size(); i++) {
            NewsArticle article = articles.get(i);
            numberedList.append("[").append(i + 1).append("] ");
            numberedList.append(article.title());
            if (article.bodyText() != null && !article.bodyText().isBlank()) {
                String snippet = article.bodyText();
                if (snippet.length() > 500) {
                    snippet = snippet.substring(0, 500) + "...";
                }
                numberedList.append("\n    ").append(snippet);
            }
            numberedList.append("\n\n");
        }

        String result = template
                .replace("{companyName}", companyName)
                .replace("{numberedArticleList}", numberedList.toString().trim());

        log.debug("buildPrompt() | return=prompt ({} chars)", result.length());
        return result;
    }

    FrameworkAnalysis parseResponse(String response, String companySlug,
                                    String companyName, int articleCount) {
        log.debug("parseResponse() | responseLength={}, companySlug={}",
                  response.length(), companySlug);

        String overallAssessment = extractSection(response, "OVERALL_ASSESSMENT:", "DIMENSIONS:");
        List<FrameworkDimension> dimensions = parseDimensions(response);
        List<String> strengths = parseBulletSection(response, "STRENGTHS:", "WEAKNESSES:");
        List<String> weaknesses = parseBulletSection(response, "WEAKNESSES:", "RECENT_DEVELOPMENTS:");
        List<String> recentDevelopments = parseBulletSection(response, "RECENT_DEVELOPMENTS:", null);

        if (overallAssessment.isBlank() || dimensions.isEmpty()) {
            log.warn("parseResponse() | failed to parse essential sections for {}", companySlug);
            log.debug("parseResponse() | return=null (parse failure)");
            return null;
        }

        int overallScore = 0;
        for (FrameworkDimension dim : dimensions) {
            overallScore += dim.score();
        }
        if (!dimensions.isEmpty()) {
            overallScore = overallScore / dimensions.size();
        }

        FrameworkAnalysis result = new FrameworkAnalysis(
                companySlug, companyName, overallAssessment,
                dimensions, strengths, weaknesses, recentDevelopments,
                overallScore, articleCount, Instant.now());

        log.debug("parseResponse() | return={} (overallScore={}, dimensions={})",
                  companySlug, overallScore, dimensions.size());
        return result;
    }

    private String extractSection(String response, String startMarker, String endMarker) {
        int startIdx = response.indexOf(startMarker);
        if (startIdx < 0) {
            return "";
        }
        startIdx += startMarker.length();

        int endIdx = endMarker != null ? response.indexOf(endMarker, startIdx) : response.length();
        if (endIdx < 0) {
            endIdx = response.length();
        }

        return response.substring(startIdx, endIdx).trim();
    }

    private List<FrameworkDimension> parseDimensions(String response) {
        List<FrameworkDimension> dimensions = new ArrayList<>();
        String section = extractSection(response, "DIMENSIONS:", "STRENGTHS:");

        String[] lines = section.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("[")) {
                continue;
            }

            try {
                int closeBracket = trimmed.indexOf(']');
                if (closeBracket < 0) {
                    continue;
                }

                String dimensionName = trimmed.substring(1, closeBracket).trim();
                String afterBracket = trimmed.substring(closeBracket + 1).trim();

                String[] parts = afterBracket.split("\\|", 2);
                if (parts.length < 2) {
                    continue;
                }

                int score;
                try {
                    score = Integer.parseInt(parts[0].trim());
                } catch (NumberFormatException e) {
                    continue;
                }
                if (score < 1) score = 1;
                if (score > 10) score = 10;

                String rationale = parts[1].trim();
                if (rationale.isEmpty()) {
                    rationale = "No rationale provided";
                }

                dimensions.add(new FrameworkDimension(dimensionName, score, rationale));
            } catch (Exception e) {
                log.warn("parseDimensions() | failed to parse dimension line: {}", trimmed);
            }
        }

        return dimensions;
    }

    private List<String> parseBulletSection(String response, String startMarker,
                                             String endMarker) {
        List<String> items = new ArrayList<>();
        String section = extractSection(response, startMarker,
                endMarker != null ? endMarker : null);

        String[] lines = section.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("-") || trimmed.startsWith("•")) {
                String item = trimmed.substring(1).trim();
                if (!item.isEmpty()) {
                    items.add(item);
                }
            }
        }

        return items;
    }
}
