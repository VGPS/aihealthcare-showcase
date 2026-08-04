package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.ArticleSentiment;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.SentimentLabel;
import com.wgblackmon.aihealthcare.domain.port.outbound.SentimentAnalysisPort;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI adapter that implements {@link SentimentAnalysisPort} using a
 * large-language model to classify article sentiment toward a company.
 *
 * <p>Sends a batch of articles with a sentiment classification prompt to the
 * LLM, then parses the structured response to extract labels, confidence
 * scores, and rationales. Articles are batched in groups of 20.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
@Slf4j
@Component
public class SentimentAnalysisAdapter implements SentimentAnalysisPort {

    private static final int BATCH_SIZE = 20;

    private final ChatClient chatClient;
    private final PromptLoaderService promptLoaderService;

    public SentimentAnalysisAdapter(ChatClient.Builder chatClientBuilder,
                                    PromptLoaderService promptLoaderService) {
        log.debug("SentimentAnalysisAdapter() | chatClientBuilder={}, promptLoaderService={}",
                  chatClientBuilder, promptLoaderService.getClass().getSimpleName());
        this.chatClient = chatClientBuilder.build();
        this.promptLoaderService = promptLoaderService;
        log.debug("SentimentAnalysisAdapter() | return=void");
    }

    @Override
    public List<ArticleSentiment> analyzeSentiment(List<NewsArticle> articles, String companyName) {
        log.debug("analyzeSentiment() | companyName={}, articleCount={}",
                  companyName, articles != null ? articles.size() : 0);

        if (articles == null || articles.isEmpty()) {
            log.debug("analyzeSentiment() | return=[] (empty input)");
            return List.of();
        }

        List<ArticleSentiment> allResults = new ArrayList<>();

        for (int batchStart = 0; batchStart < articles.size(); batchStart += BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + BATCH_SIZE, articles.size());
            List<NewsArticle> batch = articles.subList(batchStart, batchEnd);

            List<ArticleSentiment> batchResults = analyzeBatch(batch, companyName);
            for (ArticleSentiment s : batchResults) {
                allResults.add(s);
            }
        }

        log.debug("analyzeSentiment() | return={} sentiments", allResults.size());
        return allResults;
    }

    private List<ArticleSentiment> analyzeBatch(List<NewsArticle> batch, String companyName) {
        log.debug("analyzeBatch() | batchSize={}, companyName={}", batch.size(), companyName);

        String prompt = buildPrompt(batch, companyName);

        String response;
        try {
            response = chatClient.prompt(prompt).call().content();
        } catch (Exception e) {
            log.warn("analyzeBatch() | LLM call failed for company={}: {}", companyName, e.getMessage());
            log.debug("analyzeBatch() | return=[] (LLM error)");
            return List.of();
        }

        if (response == null || response.isBlank()) {
            log.warn("analyzeBatch() | Empty LLM response for company={}", companyName);
            log.debug("analyzeBatch() | return=[] (empty response)");
            return List.of();
        }

        log.debug("analyzeBatch() | LLM response length={} chars", response.length());
        List<ArticleSentiment> result = parseResponse(response, batch);
        log.debug("analyzeBatch() | return={} sentiments", result.size());
        return result;
    }

    String buildPrompt(List<NewsArticle> articles, String companyName) {
        log.debug("buildPrompt() | articleCount={}, companyName={}", articles.size(), companyName);

        String template = promptLoaderService.load("sentiment-analysis.txt");

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
                .replace("{companyName}", companyName)
                .replace("{numberedArticleList}", numberedList.toString().trim());

        log.debug("buildPrompt() | return=prompt ({} chars)", result.length());
        return result;
    }

    /**
     * Parses the LLM response to extract sentiment results.
     * Expected format after "SENTIMENT_RESULTS:" line:
     * [N] POSITIVE | 0.85 | rationale text
     */
    List<ArticleSentiment> parseResponse(String response, List<NewsArticle> articles) {
        log.debug("parseResponse() | responseLength={}, articleCount={}",
                  response.length(), articles.size());

        List<ArticleSentiment> results = new ArrayList<>();
        boolean inResultsSection = false;

        String[] lines = response.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("SENTIMENT_RESULTS:")) {
                inResultsSection = true;
                continue;
            }

            if (!inResultsSection) {
                continue;
            }

            if (trimmed.isEmpty() || !trimmed.startsWith("[")) {
                continue;
            }

            try {
                int closeBracket = trimmed.indexOf(']');
                if (closeBracket < 0) {
                    continue;
                }
                int articleIndex = Integer.parseInt(trimmed.substring(1, closeBracket).trim());

                String afterBracket = trimmed.substring(closeBracket + 1).trim();

                // Parse: LABEL | confidence | rationale
                String[] parts = afterBracket.split("\\|", 3);
                if (parts.length < 3) {
                    continue;
                }

                String labelStr = parts[0].trim().toUpperCase();
                SentimentLabel label = parseSentimentLabel(labelStr);
                if (label == null) {
                    log.warn("parseResponse() | unknown sentiment label: {}", labelStr);
                    continue;
                }

                double confidence;
                try {
                    confidence = Double.parseDouble(parts[1].trim());
                } catch (NumberFormatException e) {
                    confidence = 0.5;
                }
                if (confidence < 0.0) confidence = 0.0;
                if (confidence > 1.0) confidence = 1.0;

                String rationale = parts[2].trim();
                if (rationale.isEmpty()) {
                    rationale = "No rationale provided";
                }

                int batchIndex = articleIndex - 1;
                if (batchIndex < 0 || batchIndex >= articles.size()) {
                    log.warn("parseResponse() | article index {} out of range (batch size {})",
                              articleIndex, articles.size());
                    continue;
                }

                NewsArticle article = articles.get(batchIndex);
                results.add(new ArticleSentiment(
                        article.articleId(),
                        article.title(),
                        label,
                        confidence,
                        rationale
                ));
            } catch (NumberFormatException e) {
                log.warn("parseResponse() | failed to parse line: {}", trimmed);
            }
        }

        log.debug("parseResponse() | return={} sentiments", results.size());
        return results;
    }

    private SentimentLabel parseSentimentLabel(String label) {
        switch (label) {
            case "POSITIVE": return SentimentLabel.POSITIVE;
            case "NEGATIVE": return SentimentLabel.NEGATIVE;
            case "MIXED":    return SentimentLabel.MIXED;
            case "NEUTRAL":  return SentimentLabel.NEUTRAL;
            default:         return null;
        }
    }
}
