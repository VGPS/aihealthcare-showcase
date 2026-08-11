package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.GapItem;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.WikiGapAnalysisResult;
import com.wgblackmon.aihealthcare.domain.port.outbound.WikiGapAnalysisPort;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Spring AI adapter implementing {@link WikiGapAnalysisPort}.
 *
 * <p>Builds a structured prompt with article IDs and titles alongside
 * existing wiki page summaries, calls Claude to identify coverage gaps,
 * and parses the structured response into domain records.
 *
 * <p>Article IDs are included in the prompt so Claude can reference them
 * per gap. The parser validates returned IDs against the input set to
 * catch hallucinations.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-11
 * @updated 2026-08-11
 */
@Slf4j
public class WikiGapAnalysisAdapter implements WikiGapAnalysisPort {

    private static final int MAX_ARTICLES = 200;

    private final ChatClient chatClient;
    private final WikiGapResponseParser responseParser;
    private final String gapAnalysisPrompt;

    public WikiGapAnalysisAdapter(ChatClient.Builder chatClientBuilder,
                                   WikiGapResponseParser responseParser,
                                   String gapAnalysisPrompt) {
        log.debug("WikiGapAnalysisAdapter() | promptLength={}",
                gapAnalysisPrompt != null ? gapAnalysisPrompt.length() : 0);
        this.chatClient = chatClientBuilder.build();
        this.responseParser = responseParser;
        this.gapAnalysisPrompt = gapAnalysisPrompt;
        log.debug("WikiGapAnalysisAdapter() | return=void");
    }

    @Override
    public WikiGapAnalysisResult analyzeGaps(List<NewsArticle> recentArticles,
                                              List<WikiPageEntity> existingPages) {
        log.debug("analyzeGaps() | articles={}, pages={}",
                recentArticles.size(), existingPages.size());

        int articleCount = Math.min(recentArticles.size(), MAX_ARTICLES);
        List<NewsArticle> capped = recentArticles.subList(0, articleCount);

        Set<String> validArticleIds = new LinkedHashSet<>();
        StringBuilder articleList = new StringBuilder();
        int num = 1;
        for (NewsArticle article : capped) {
            String id = article.articleId();
            validArticleIds.add(id);
            articleList.append("[").append(num).append("] ID: ").append(id)
                    .append("  Title: ").append(article.title())
                    .append("\n");
            num++;
        }

        StringBuilder pageSummaries = new StringBuilder();
        if (existingPages.isEmpty()) {
            pageSummaries.append("(No existing wiki pages.)\n");
        } else {
            for (WikiPageEntity page : existingPages) {
                pageSummaries.append("- ").append(page.getTitle())
                        .append(" (").append(page.getPageType()).append(")");
                if (page.getTags() != null && !page.getTags().isBlank()) {
                    pageSummaries.append(" [tags: ").append(page.getTags()).append("]");
                }
                pageSummaries.append("\n");
            }
        }

        String prompt = gapAnalysisPrompt
                .replace("{articleCount}", String.valueOf(articleCount))
                .replace("{articleList}", articleList.toString())
                .replace("{wikiPageCount}", String.valueOf(existingPages.size()))
                .replace("{wikiPageSummaries}", pageSummaries.toString());

        log.debug("analyzeGaps() | promptLength={}", prompt.length());

        String response = chatClient.prompt()
                .user(prompt)
                .options(AnthropicChatOptions.builder().maxTokens(8000).build())
                .call().content();
        log.debug("analyzeGaps() | responseLength={}", response != null ? response.length() : 0);

        List<GapItem> gaps = responseParser.parseGaps(response, validArticleIds);
        String summary = responseParser.parseSummary(response);

        WikiGapAnalysisResult result = new WikiGapAnalysisResult(
                gaps, summary, articleCount, existingPages.size());
        log.debug("analyzeGaps() | return=WikiGapAnalysisResult[gaps={}, articles={}, pages={}]",
                result.gaps().size(), result.articlesAnalyzed(), result.wikiPagesChecked());
        return result;
    }
}
