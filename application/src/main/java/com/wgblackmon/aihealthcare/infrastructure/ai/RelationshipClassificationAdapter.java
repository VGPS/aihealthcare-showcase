package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.CompanyRelationship;
import com.wgblackmon.aihealthcare.domain.model.CompanyRelationshipType;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.RelationshipClassificationPort;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Spring AI adapter that implements {@link RelationshipClassificationPort}
 * using a large-language model to confirm inter-company relationships and
 * extract clean source/target company names.
 *
 * <p>Batches articles in groups of 10 to keep LLM costs low. Unlike the
 * deterministic keyword-window extraction in {@code CompanyRelationshipService},
 * the LLM understands which spans are actual organization names, avoiding
 * trigger-phrase-adjacent junk (years, generic nouns) that a fixed word
 * window can capture.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-07
 * @updated 2026-10-07
 */
@Slf4j
@Component
public class RelationshipClassificationAdapter implements RelationshipClassificationPort {

    private static final int BATCH_SIZE = 10;

    private final ChatClient chatClient;
    private final PromptLoaderService promptLoaderService;

    public RelationshipClassificationAdapter(ChatClient.Builder chatClientBuilder,
                                              PromptLoaderService promptLoaderService,
                                              @Value("${aihealthcare.ai.classification-model:claude-haiku-4-5}") String classificationModel) {
        log.debug("RelationshipClassificationAdapter() | chatClientBuilder={}, promptLoaderService={}, classificationModel={}",
                  chatClientBuilder, promptLoaderService.getClass().getSimpleName(), classificationModel);
        this.chatClient = chatClientBuilder
                .defaultOptions(AnthropicChatOptions.builder().model(classificationModel).build())
                .build();
        this.promptLoaderService = promptLoaderService;
    }

    @Override
    public List<CompanyRelationship> classifyRelationships(List<NewsArticle> candidateArticles) {
        if (candidateArticles == null || candidateArticles.isEmpty()) {
            log.debug("classifyRelationships() | return=[] (empty input)");
            return List.of();
        }
        log.debug("classifyRelationships() | candidateCount={}", candidateArticles.size());

        List<CompanyRelationship> allResults = new ArrayList<>();

        for (int batchStart = 0; batchStart < candidateArticles.size(); batchStart += BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + BATCH_SIZE, candidateArticles.size());
            List<NewsArticle> batch = candidateArticles.subList(batchStart, batchEnd);

            List<CompanyRelationship> batchResults = classifyBatch(batch);
            for (CompanyRelationship rel : batchResults) {
                allResults.add(rel);
            }
        }

        log.debug("classifyRelationships() | return={} relationships", allResults.size());
        return allResults;
    }

    private List<CompanyRelationship> classifyBatch(List<NewsArticle> batch) {
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
        List<CompanyRelationship> result = parseResponse(response, batch);
        log.debug("classifyBatch() | return={} relationships", result.size());
        return result;
    }

    String buildPrompt(List<NewsArticle> articles) {
        log.debug("buildPrompt() | articleCount={}", articles.size());

        String template = promptLoaderService.load("relationship-classification.txt");

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

    List<CompanyRelationship> parseResponse(String response, List<NewsArticle> articles) {
        log.debug("parseResponse() | responseLength={}, articleCount={}",
                  response.length(), articles.size());

        List<CompanyRelationship> results = new ArrayList<>();
        boolean inResultsSection = false;
        Instant now = Instant.now();

        String[] lines = response.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("RELATIONSHIP_RESULTS:")) {
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

                if (afterBracket.startsWith("NO_RELATIONSHIP") || afterBracket.contains("NO_RELATIONSHIP")) {
                    continue;
                }

                int batchIndex = articleIndex - 1;
                if (batchIndex < 0 || batchIndex >= articles.size()) {
                    log.warn("parseResponse() | article index {} out of range", articleIndex);
                    continue;
                }

                NewsArticle article = articles.get(batchIndex);
                CompanyRelationship rel = parseRelationshipLine(afterBracket, article, now);
                if (rel != null) {
                    results.add(rel);
                }
            } catch (NumberFormatException e) {
                log.warn("parseResponse() | failed to parse line: {}", trimmed);
            }
        }

        log.debug("parseResponse() | return={} relationships", results.size());
        return results;
    }

    private CompanyRelationship parseRelationshipLine(String line, NewsArticle article, Instant now) {
        String type = extractField(line, "TYPE:");
        String source = extractField(line, "SOURCE:");
        String target = extractField(line, "TARGET:");
        String confidenceStr = extractField(line, "CONFIDENCE:");
        String summary = extractField(line, "SUMMARY:");

        CompanyRelationshipType relationshipType = parseRelationshipType(type);
        if (relationshipType == null) {
            log.warn("parseRelationshipLine() | unknown relationship type: {}", type);
            return null;
        }

        if (source == null || source.isBlank() || target == null || target.isBlank()) {
            log.warn("parseRelationshipLine() | missing source/target: {}", line);
            return null;
        }
        if (source.trim().equalsIgnoreCase(target.trim())) {
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

        if (summary == null || summary.isBlank()) {
            summary = article.title();
        }

        String evidenceArticleId = article.url() != null ? article.url().toString() : article.articleId();

        return new CompanyRelationship(
                UUID.randomUUID().toString(),
                source.trim(),
                target.trim(),
                relationshipType,
                evidenceArticleId,
                summary,
                confidence,
                now
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

    private CompanyRelationshipType parseRelationshipType(String type) {
        if (type == null) {
            return null;
        }
        String upper = type.trim().toUpperCase();
        switch (upper) {
            case "PARTNERSHIP": return CompanyRelationshipType.PARTNERSHIP;
            case "ACQUISITION": return CompanyRelationshipType.ACQUISITION;
            case "INVESTMENT":  return CompanyRelationshipType.INVESTMENT;
            case "COMPETITOR":  return CompanyRelationshipType.COMPETITOR;
            case "SUPPLIER":    return CompanyRelationshipType.SUPPLIER;
            case "INTEGRATION": return CompanyRelationshipType.INTEGRATION;
            default:            return null;
        }
    }
}
