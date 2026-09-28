package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigest;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestEntry;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.ProduceMarketDigestUseCase;
import com.wgblackmon.aihealthcare.domain.model.SocialPostDraft;
import com.wgblackmon.aihealthcare.domain.port.outbound.SocialPostDraftPort;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Spring AI agent adapter for social post drafting.
 *
 * <p>This is where the agent pattern lives. Instead of pre-loading all digest data
 * into a single prompt, Claude autonomously decides what it needs by calling tools:
 * <ol>
 *   <li>{@code getDigestEntries} — fetches today's ranked market entries from the DB</li>
 *   <li>{@code getCompanyArticles} — fetches article headlines for a specific company</li>
 * </ol>
 *
 * <p>Enable {@code org.springframework.ai: DEBUG} in logging config to watch the
 * tool call sequence in real time — Claude's requests, the returned data, and its
 * reasoning chain are all visible in the DEBUG output. That sequence is the agent loop.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-26
 * @updated 2026-09-26
 */
@Slf4j
@Component
public class SocialPostAgentAdapter implements SocialPostDraftPort {

    private final ChatClient chatClient;
    private final ProduceMarketDigestUseCase digestUseCase;
    private final NewsArticleRepository articleRepository;
    private final String systemPrompt;

    @Autowired
    public SocialPostAgentAdapter(ChatClient.Builder chatClientBuilder,
                                   ProduceMarketDigestUseCase digestUseCase,
                                   NewsArticleRepository articleRepository,
                                   PromptLoaderService promptLoaderService) {
        log.debug("SocialPostAgentAdapter() | wiring agent adapter");
        this.chatClient = chatClientBuilder.build();
        this.digestUseCase = digestUseCase;
        this.articleRepository = articleRepository;
        this.systemPrompt = promptLoaderService.load("social-post-agent.txt");
    }

    /** Package-private constructor for unit tests — avoids ChatClient.Builder and PromptLoaderService. */
    SocialPostAgentAdapter(ProduceMarketDigestUseCase digestUseCase,
                           NewsArticleRepository articleRepository) {
        this.chatClient = null;
        this.digestUseCase = digestUseCase;
        this.articleRepository = articleRepository;
        this.systemPrompt = "";
    }

    @Override
    public SocialPostDraft draft(LocalDate date) {
        log.debug("draft() | date={}", date);

        String userMessage = "Today is " + date + ". Use your tools to get the market digest "
                + "entries and draft LinkedIn and Facebook posts. Follow the output format "
                + "in your instructions exactly.";

        String response = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .tools(this)
                .call()
                .content();

        log.debug("draft() | raw response length={}", response != null ? response.length() : 0);

        SocialPostDraft result = parseResponse(response, date);
        log.debug("draft() | return={}", result);
        return result;
    }

    // -------------------------------------------------------------------------
    // Tool definitions — Claude will call these during the agent loop.
    // Each method must be public for Spring AI's reflection-based tool scanning.
    // Watch these in DEBUG logs as: "Tool call request: getDigestEntries(...)"
    // -------------------------------------------------------------------------

    @Tool(description = "Get ranked market digest entries for a date (format: yyyy-MM-dd). "
            + "Returns headline, category, rank (1=highest), fact classification, summary, and companies.")
    public String getDigestEntries(String date) {
        log.debug("getDigestEntries() | date={}", date);

        Optional<MarketDigest> digest;
        try {
            digest = digestUseCase.findByDate(LocalDate.parse(date));
        } catch (Exception e) {
            log.warn("getDigestEntries() | could not parse date={}, falling back to latest", date);
            digest = digestUseCase.findLatest();
        }
        if (digest.isEmpty()) {
            digest = digestUseCase.findLatest();
        }
        if (digest.isEmpty()) {
            log.debug("getDigestEntries() | return=no digest available");
            return "No market digest available. The pipeline may not have run yet.";
        }

        StringBuilder sb = new StringBuilder();
        for (MarketDigestEntry entry : digest.get().entries()) {
            sb.append("HEADLINE: ").append(entry.newsItem().headline()).append("\n");
            sb.append("CATEGORY: ").append(entry.newsItem().category()).append("\n");
            sb.append("RANK: ").append(entry.rank()).append("\n");
            sb.append("FACT: ").append(entry.factClassification()).append("\n");
            sb.append("SUMMARY: ").append(entry.newsItem().summary()).append("\n");
            if (entry.affectedCompanies() != null && !entry.affectedCompanies().isEmpty()) {
                String companies = entry.affectedCompanies().stream()
                        .map(c -> c.tickerSymbol() != null
                                ? c.name() + " (" + c.tickerSymbol() + ")"
                                : c.name())
                        .collect(Collectors.joining(", "));
                sb.append("COMPANIES: ").append(companies).append("\n");
            }
            sb.append("\n");
        }

        String result = sb.toString();
        log.debug("getDigestEntries() | return={} chars, {} entries",
                result.length(), digest.get().entries().size());
        return result;
    }

    @Tool(description = "Get up to 5 recent news article titles and snippets mentioning a company name. "
            + "Use this when you want more context about a company before writing about it.")
    public String getCompanyArticles(String companyName) {
        log.debug("getCompanyArticles() | companyName={}", companyName);

        List<NewsArticleEntity> articles = articleRepository
                .findRealArticlesByCompanyName(companyName)
                .stream()
                .limit(5)
                .collect(Collectors.toList());

        if (articles.isEmpty()) {
            log.debug("getCompanyArticles() | return=no articles for company={}", companyName);
            return "No recent articles found mentioning: " + companyName;
        }

        StringBuilder sb = new StringBuilder();
        for (NewsArticleEntity article : articles) {
            sb.append("TITLE: ").append(article.getTitle()).append("\n");
            String body = article.getBodyText();
            if (body != null && !body.isBlank()) {
                String snippet = body.length() > 250 ? body.substring(0, 250) + "..." : body;
                sb.append("SNIPPET: ").append(snippet).append("\n");
            }
            sb.append("\n");
        }

        String result = sb.toString();
        log.debug("getCompanyArticles() | return={} chars for company={}", result.length(), companyName);
        return result;
    }

    // -------------------------------------------------------------------------
    // Response parser — extracts labeled sections from the agent's final message.
    // -------------------------------------------------------------------------

    SocialPostDraft parseResponse(String response, LocalDate date) {
        log.debug("parseResponse() | date={}", date);

        if (response == null || response.isBlank()) {
            throw new IllegalStateException("Agent returned an empty response");
        }

        String linkedinBody    = extractSection(response, "LINKEDIN_BODY",    "LINKEDIN_COMMENT");
        String linkedinComment = extractSection(response, "LINKEDIN_COMMENT", "FACEBOOK_BODY");
        String facebookBody    = extractSection(response, "FACEBOOK_BODY",    "FACEBOOK_COMMENT");
        String facebookComment = extractSection(response, "FACEBOOK_COMMENT", "ENTRIES_SELECTED");
        String entriesRaw      = extractSection(response, "ENTRIES_SELECTED", "RATIONALE");
        String rationale       = extractSection(response, "RATIONALE",        null);

        List<String> entriesSelected = new ArrayList<>();
        if (entriesRaw != null && !entriesRaw.isBlank()) {
            entriesSelected = Arrays.stream(entriesRaw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toList());
        }

        SocialPostDraft result = new SocialPostDraft(
                nullToEmpty(linkedinBody),
                nullToEmpty(linkedinComment),
                nullToEmpty(facebookBody),
                nullToEmpty(facebookComment),
                entriesSelected,
                nullToEmpty(rationale),
                Instant.now()
        );
        log.debug("parseResponse() | return={}", result);
        return result;
    }

    private String extractSection(String text, String startLabel, String endLabel) {
        String marker = startLabel + ":";
        int start = text.indexOf(marker);
        if (start == -1) {
            return "";
        }
        start += marker.length();

        int end = text.length();
        if (endLabel != null) {
            int endIdx = text.indexOf(endLabel + ":", start);
            if (endIdx != -1) {
                end = endIdx;
            }
        }
        return text.substring(start, end).trim();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
