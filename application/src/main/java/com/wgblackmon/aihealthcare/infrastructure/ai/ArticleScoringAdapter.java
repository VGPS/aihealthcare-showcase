package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.ScoredArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleScoringPort;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI adapter that implements {@link ArticleScoringPort} using a large-language
 * model to evaluate articles for technological significance.
 *
 * <p>Sends a batch of articles with a scoring rubric prompt to the LLM, then parses
 * the structured response to extract scores and rationales. Articles are batched
 * in groups of up to 20 to stay within context window limits.
 *
 * <p>The prompt template is loaded from {@code prompts/tech-trend-score.txt} and
 * uses placeholders for theme name, description, threshold, and article list.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-24
 * @updated 2026-07-24
 */
@Slf4j
@Component
public class ArticleScoringAdapter implements ArticleScoringPort {

    private static final int BATCH_SIZE = 20;

    private final ChatClient chatClient;
    private final PromptLoaderService promptLoaderService;

    /**
     * Constructs the adapter with a ChatClient builder and prompt loader.
     *
     * @param chatClientBuilder   auto-configured builder from Spring AI
     * @param promptLoaderService service to load prompt templates
     */
    public ArticleScoringAdapter(ChatClient.Builder chatClientBuilder,
                                  PromptLoaderService promptLoaderService) {
        log.debug("ArticleScoringAdapter() | chatClientBuilder={}, promptLoaderService={}",
                  chatClientBuilder, promptLoaderService.getClass().getSimpleName());
        this.chatClient = chatClientBuilder.build();
        this.promptLoaderService = promptLoaderService;
        log.debug("ArticleScoringAdapter() | return=void");
    }

    @Override
    public List<ScoredArticle> scoreArticles(List<NewsArticle> articles,
                                              String theme,
                                              String themeDescription,
                                              int scoreThreshold) {
        log.debug("scoreArticles() | theme={}, themeDescription={}, scoreThreshold={}, articleCount={}",
                  theme, themeDescription, scoreThreshold, articles != null ? articles.size() : 0);

        if (articles == null || articles.isEmpty()) {
            log.debug("scoreArticles() | return=[] (empty input)");
            return List.of();
        }

        List<ScoredArticle> allScored = new ArrayList<>();

        // Batch articles to stay within LLM context limits
        for (int batchStart = 0; batchStart < articles.size(); batchStart += BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + BATCH_SIZE, articles.size());
            List<NewsArticle> batch = articles.subList(batchStart, batchEnd);

            List<ScoredArticle> batchResults = scoreBatch(batch, theme, themeDescription,
                    scoreThreshold, batchStart);
            for (ScoredArticle sa : batchResults) {
                allScored.add(sa);
            }
        }

        // Sort by score descending
        sortByScoreDescending(allScored);

        log.debug("scoreArticles() | return={} scored articles", allScored.size());
        return allScored;
    }

    /**
     * Scores a single batch of articles via LLM call.
     */
    private List<ScoredArticle> scoreBatch(List<NewsArticle> batch,
                                            String theme,
                                            String themeDescription,
                                            int scoreThreshold,
                                            int globalOffset) {
        log.debug("scoreBatch() | batchSize={}, theme={}, globalOffset={}",
                  batch.size(), theme, globalOffset);

        String prompt = buildPrompt(batch, theme, themeDescription, scoreThreshold);
        log.debug("scoreBatch() | prompt length={} chars", prompt.length());

        String response;
        try {
            response = chatClient.prompt(prompt).call().content();
        } catch (Exception e) {
            log.warn("scoreBatch() | LLM call failed for theme={}: {}", theme, e.getMessage());
            log.debug("scoreBatch() | return=[] (LLM error)");
            return List.of();
        }

        if (response == null || response.isBlank()) {
            log.warn("scoreBatch() | Empty LLM response for theme={}", theme);
            log.debug("scoreBatch() | return=[] (empty response)");
            return List.of();
        }

        log.debug("scoreBatch() | LLM response length={} chars", response.length());
        List<ScoredArticle> result = parseResponse(response, batch, theme, globalOffset);
        log.debug("scoreBatch() | return={} scored articles", result.size());
        return result;
    }

    /**
     * Builds the scoring prompt from the template and article list.
     */
    String buildPrompt(List<NewsArticle> articles, String theme,
                       String themeDescription, int scoreThreshold) {
        log.debug("buildPrompt() | articleCount={}, theme={}", articles.size(), theme);

        String template = promptLoaderService.load("tech-trend-score.txt");

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
                .replace("{themeName}", theme)
                .replace("{themeDescription}", themeDescription != null ? themeDescription : theme)
                .replace("{scoreThreshold}", String.valueOf(scoreThreshold))
                .replace("{numberedArticleList}", numberedList.toString().trim());

        log.debug("buildPrompt() | return=prompt ({} chars)", result.length());
        return result;
    }

    /**
     * Parses the LLM response to extract scored articles.
     * Expected format after "SCORED_ARTICLES:" line:
     * [N] SCORE: X | rationale text
     */
    List<ScoredArticle> parseResponse(String response, List<NewsArticle> articles,
                                       String theme, int globalOffset) {
        log.debug("parseResponse() | responseLength={}, articleCount={}, theme={}",
                  response.length(), articles.size(), theme);

        List<ScoredArticle> scored = new ArrayList<>();
        boolean inScoredSection = false;

        String[] lines = response.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("SCORED_ARTICLES:")) {
                inScoredSection = true;
                continue;
            }

            if (!inScoredSection) {
                continue;
            }

            if (trimmed.isEmpty()) {
                continue;
            }

            // Parse: [N] SCORE: X | rationale
            if (!trimmed.startsWith("[")) {
                continue;
            }

            try {
                int closeBracket = trimmed.indexOf(']');
                if (closeBracket < 0) {
                    continue;
                }
                int articleIndex = Integer.parseInt(trimmed.substring(1, closeBracket).trim());

                int scoreStart = trimmed.indexOf("SCORE:");
                if (scoreStart < 0) {
                    continue;
                }
                String afterScore = trimmed.substring(scoreStart + 6).trim();

                int pipeIndex = afterScore.indexOf('|');
                if (pipeIndex < 0) {
                    continue;
                }

                int score = Integer.parseInt(afterScore.substring(0, pipeIndex).trim());
                String rationale = afterScore.substring(pipeIndex + 1).trim();

                // articleIndex is 1-based within this batch
                int batchIndex = articleIndex - 1;
                if (batchIndex < 0 || batchIndex >= articles.size()) {
                    log.warn("parseResponse() | article index {} out of range (batch size {})",
                              articleIndex, articles.size());
                    continue;
                }

                if (score < 1 || score > 10) {
                    log.warn("parseResponse() | invalid score {} for article index {}",
                              score, articleIndex);
                    continue;
                }

                if (rationale.isEmpty()) {
                    rationale = "No rationale provided";
                }

                NewsArticle article = articles.get(batchIndex);
                scored.add(new ScoredArticle(
                        article.articleId(),
                        article.title(),
                        score,
                        rationale,
                        theme));
            } catch (NumberFormatException e) {
                log.warn("parseResponse() | failed to parse line: {}", trimmed);
            }
        }

        log.debug("parseResponse() | return={} scored articles", scored.size());
        return scored;
    }

    private void sortByScoreDescending(List<ScoredArticle> articles) {
        for (int i = 0; i < articles.size() - 1; i++) {
            for (int j = i + 1; j < articles.size(); j++) {
                if (articles.get(j).score() > articles.get(i).score()) {
                    ScoredArticle temp = articles.get(i);
                    articles.set(i, articles.get(j));
                    articles.set(j, temp);
                }
            }
        }
    }
}
