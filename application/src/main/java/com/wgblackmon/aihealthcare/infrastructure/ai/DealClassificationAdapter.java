package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.DealSignal;
import com.wgblackmon.aihealthcare.domain.model.DealSignalType;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.DealClassificationPort;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Spring AI adapter that implements {@link DealClassificationPort} using a
 * large-language model to confirm deal signals and extract structured
 * deal details (amount, counterparty, analysis).
 *
 * <p>Batches articles in groups of 10 to keep LLM costs low while still
 * getting rich deal classification. Articles the LLM identifies as
 * non-deals are filtered out.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-06
 * @updated 2026-08-06
 */
@Slf4j
@Component
public class DealClassificationAdapter implements DealClassificationPort {

    private static final int BATCH_SIZE = 10;

    private final ChatClient chatClient;
    private final PromptLoaderService promptLoaderService;

    public DealClassificationAdapter(ChatClient.Builder chatClientBuilder,
                                      PromptLoaderService promptLoaderService) {
        log.debug("DealClassificationAdapter() | chatClientBuilder={}, promptLoaderService={}",
                  chatClientBuilder, promptLoaderService.getClass().getSimpleName());
        this.chatClient = chatClientBuilder.build();
        this.promptLoaderService = promptLoaderService;
    }

    @Override
    public List<DealSignal> classifyDeals(List<NewsArticle> candidateArticles) {
        if (candidateArticles == null || candidateArticles.isEmpty()) {
            log.debug("classifyDeals() | return=[] (empty input)");
            return List.of();
        }
        log.debug("classifyDeals() | candidateCount={}", candidateArticles.size());

        List<DealSignal> allResults = new ArrayList<>();

        for (int batchStart = 0; batchStart < candidateArticles.size(); batchStart += BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + BATCH_SIZE, candidateArticles.size());
            List<NewsArticle> batch = candidateArticles.subList(batchStart, batchEnd);

            List<DealSignal> batchResults = classifyBatch(batch);
            for (DealSignal signal : batchResults) {
                allResults.add(signal);
            }
        }

        log.debug("classifyDeals() | return={} signals", allResults.size());
        return allResults;
    }

    private List<DealSignal> classifyBatch(List<NewsArticle> batch) {
        log.debug("classifyBatch() | batchSize={}", batch.size());

        String prompt = buildPrompt(batch);

        String response;
        try {
            response = chatClient.prompt(prompt).call().content();
        } catch (Exception e) {
            log.warn("classifyBatch() | LLM call failed: {}", e.getMessage());
            log.debug("classifyBatch() | return=[] (LLM error)");
            return List.of();
        }

        if (response == null || response.isBlank()) {
            log.warn("classifyBatch() | Empty LLM response");
            log.debug("classifyBatch() | return=[] (empty response)");
            return List.of();
        }

        log.debug("classifyBatch() | LLM response length={} chars", response.length());
        List<DealSignal> result = parseResponse(response, batch);
        log.debug("classifyBatch() | return={} signals", result.size());
        return result;
    }

    String buildPrompt(List<NewsArticle> articles) {
        log.debug("buildPrompt() | articleCount={}", articles.size());

        String template = promptLoaderService.load("deal-classification.txt");

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
                numberedList.append(" — ").append(snippet);
            }
            numberedList.append("\n");
        }

        String result = template.replace("{numberedArticleList}", numberedList.toString().trim());
        log.debug("buildPrompt() | return=prompt ({} chars)", result.length());
        return result;
    }

    List<DealSignal> parseResponse(String response, List<NewsArticle> articles) {
        log.debug("parseResponse() | responseLength={}, articleCount={}",
                  response.length(), articles.size());

        List<DealSignal> results = new ArrayList<>();
        boolean inResultsSection = false;
        Instant now = Instant.now();

        String[] lines = response.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("DEAL_RESULTS:")) {
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

                if (afterBracket.startsWith("NO_DEAL") || afterBracket.contains("NO_DEAL")) {
                    continue;
                }

                int batchIndex = articleIndex - 1;
                if (batchIndex < 0 || batchIndex >= articles.size()) {
                    log.warn("parseResponse() | article index {} out of range", articleIndex);
                    continue;
                }

                NewsArticle article = articles.get(batchIndex);
                DealSignal signal = parseDealLine(afterBracket, article, now);
                if (signal != null) {
                    results.add(signal);
                }
            } catch (NumberFormatException e) {
                log.warn("parseResponse() | failed to parse line: {}", trimmed);
            }
        }

        log.debug("parseResponse() | return={} signals", results.size());
        return results;
    }

    private DealSignal parseDealLine(String line, NewsArticle article, Instant now) {
        String type = extractField(line, "TYPE:");
        String amount = extractField(line, "AMOUNT:");
        String company = extractField(line, "COMPANY:");
        String counterparty = extractField(line, "COUNTERPARTY:");
        String confidenceStr = extractField(line, "CONFIDENCE:");
        String summary = extractField(line, "SUMMARY:");
        String analysis = extractField(line, "ANALYSIS:");

        DealSignalType signalType = parseDealType(type);
        if (signalType == null) {
            log.warn("parseDealLine() | unknown deal type: {}", type);
            return null;
        }

        double confidence;
        try {
            confidence = Double.parseDouble(confidenceStr);
        } catch (NumberFormatException e) {
            confidence = 0.7;
        }
        if (confidence < 0.0) {
            confidence = 0.0;
        }
        if (confidence > 1.0) {
            confidence = 1.0;
        }

        if (company == null || company.isBlank()) {
            company = article.sourceName() != null ? article.sourceName() : "Unknown";
        }

        if ("N/A".equalsIgnoreCase(counterparty) || "n/a".equals(counterparty)) {
            counterparty = null;
        }

        if (summary == null || summary.isBlank()) {
            summary = article.title();
        }

        String sourceUrl = article.url() != null ? article.url().toString() : null;

        return new DealSignal(
                UUID.randomUUID().toString(),
                article.articleId(),
                article.title(),
                signalType,
                company,
                summary,
                confidence,
                now,
                amount,
                counterparty,
                sourceUrl,
                analysis
        );
    }

    private String extractField(String line, String fieldName) {
        int idx = line.indexOf(fieldName);
        if (idx < 0) {
            return null;
        }
        String after = line.substring(idx + fieldName.length()).trim();
        int pipeIdx = after.indexOf('|');
        if (pipeIdx >= 0) {
            return after.substring(0, pipeIdx).trim();
        }
        return after.trim();
    }

    private DealSignalType parseDealType(String type) {
        if (type == null) {
            return null;
        }
        String upper = type.trim().toUpperCase();
        switch (upper) {
            case "FUNDING":        return DealSignalType.FUNDING;
            case "ACQUISITION":    return DealSignalType.ACQUISITION;
            case "PARTNERSHIP":    return DealSignalType.PARTNERSHIP;
            case "IPO":            return DealSignalType.IPO;
            case "PRODUCT_LAUNCH": return DealSignalType.PRODUCT_LAUNCH;
            default:               return null;
        }
    }
}
