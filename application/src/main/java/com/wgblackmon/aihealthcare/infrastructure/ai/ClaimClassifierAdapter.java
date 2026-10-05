package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.ClaimType;
import com.wgblackmon.aihealthcare.domain.model.ClaimVerdict;
import com.wgblackmon.aihealthcare.domain.model.FrontierClaim;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ClaimClassifierPort;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * LLM-powered adapter implementing {@link ClaimClassifierPort}.
 *
 * <p>Articles are sent to the Claude LLM in batches of up to
 * {@value #BATCH_SIZE}. The structured {@code CLAIM_RESULTS:} block in
 * each response is parsed into {@link FrontierClaim} records. Malformed
 * lines are logged at WARN and skipped — they never abort the batch.
 *
 * <p>Response line format (pipe-delimited):
 * {@code COMPANY|TYPE|VERDICT|CLAIM_TEXT|EVIDENCE_NOTES|SOURCE_URL|ARTICLE_ID}
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-05
 * @updated 2026-10-05
 */
@Slf4j
@Component
public class ClaimClassifierAdapter implements ClaimClassifierPort {

    static final int BATCH_SIZE = 10;

    private final ChatClient chatClient;
    private final PromptLoaderService promptLoaderService;

    public ClaimClassifierAdapter(ChatClient.Builder chatClientBuilder,
                                   PromptLoaderService promptLoaderService) {
        log.debug("ClaimClassifierAdapter() | initializing");
        this.chatClient = chatClientBuilder.build();
        this.promptLoaderService = promptLoaderService;
    }

    @Override
    public List<FrontierClaim> classifyClaims(List<NewsArticle> articles) {
        log.debug("classifyClaims() | articles={}", articles.size());

        List<FrontierClaim> allClaims = new ArrayList<>();

        for (int i = 0; i < articles.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, articles.size());
            List<NewsArticle> batch = articles.subList(i, end);
            List<FrontierClaim> batchClaims = classifyBatch(batch);
            allClaims.addAll(batchClaims);
        }

        log.debug("classifyClaims() | return={} claims", allClaims.size());
        return allClaims;
    }

    private List<FrontierClaim> classifyBatch(List<NewsArticle> batch) {
        log.debug("classifyBatch() | batchSize={}", batch.size());

        String promptTemplate = promptLoaderService.load("claim-classifier.txt");
        String articleList = buildNumberedList(batch);
        String prompt = promptTemplate
                .replace("{articleCount}", String.valueOf(batch.size()))
                .replace("{numberedArticleList}", articleList);

        String response;
        try {
            response = chatClient.prompt(prompt).call().content();
        } catch (Exception e) {
            log.warn("classifyBatch() | LLM call failed: {}", e.getMessage());
            return List.of();
        }

        List<FrontierClaim> claims = parseResponse(response, batch);
        log.debug("classifyBatch() | return={} claims from batch", claims.size());
        return claims;
    }

    private String buildNumberedList(List<NewsArticle> articles) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < articles.size(); i++) {
            NewsArticle a = articles.get(i);
            sb.append("[").append(i + 1).append("] ")
              .append("Title: ").append(a.title()).append("\n")
              .append("ArticleId: ").append(a.articleId()).append("\n")
              .append("URL: ").append(a.url()).append("\n");
            if (a.bodyText() != null && !a.bodyText().isBlank()) {
                String body = a.bodyText().length() > 500
                        ? a.bodyText().substring(0, 500) : a.bodyText();
                sb.append("Body: ").append(body).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private List<FrontierClaim> parseResponse(String response, List<NewsArticle> batch) {
        log.debug("parseResponse() | responseLength={}", response == null ? 0 : response.length());

        if (response == null || !response.contains("CLAIM_RESULTS:")) {
            log.debug("parseResponse() | no CLAIM_RESULTS block found");
            return List.of();
        }

        List<FrontierClaim> claims = new ArrayList<>();
        boolean inBlock = false;
        Instant now = Instant.now();

        for (String line : response.split("\n")) {
            String trimmed = line.trim();

            if (trimmed.equals("CLAIM_RESULTS:")) {
                inBlock = true;
                continue;
            }
            if (!inBlock || trimmed.isEmpty() || trimmed.startsWith("COMPANY|")) {
                continue;
            }

            String[] parts = trimmed.split("\\|", -1);
            if (parts.length < 4) {
                log.warn("parseResponse() | skipping malformed line: {}", trimmed);
                continue;
            }

            try {
                String company = toTitleCase(parts[0].trim());
                ClaimType claimType = parseClaimType(parts[1].trim());
                ClaimVerdict verdict = parseVerdict(parts[2].trim());
                String claimText = parts[3].trim();
                String evidenceNotes = parts.length > 4 ? parts[4].trim() : null;
                String sourceUrl = parts.length > 5 && !parts[5].trim().isEmpty() ? parts[5].trim() : null;
                String articleId = parts.length > 6 && !parts[6].trim().isEmpty() ? parts[6].trim() : null;

                if (company.isBlank() || claimText.isBlank()) {
                    log.warn("parseResponse() | skipping line with blank company or claimText");
                    continue;
                }

                FrontierClaim claim = new FrontierClaim(
                        UUID.randomUUID().toString(),
                        company,
                        claimText,
                        null,
                        sourceUrl,
                        null,
                        claimType,
                        verdict,
                        evidenceNotes,
                        articleId,
                        now,
                        null
                );
                claims.add(claim);

            } catch (Exception e) {
                log.warn("parseResponse() | skipping line due to parse error: {} — {}", trimmed, e.getMessage());
            }
        }

        log.debug("parseResponse() | return={} claims", claims.size());
        return claims;
    }

    private ClaimType parseClaimType(String raw) {
        try {
            return ClaimType.valueOf(raw.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            log.warn("parseClaimType() | unknown type '{}', defaulting to CAPABILITY_CLAIM", raw);
            return ClaimType.CAPABILITY_CLAIM;
        }
    }

    private ClaimVerdict parseVerdict(String raw) {
        try {
            return ClaimVerdict.valueOf(raw.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            log.warn("parseVerdict() | unknown verdict '{}', defaulting to ALLEGED_UNVERIFIED", raw);
            return ClaimVerdict.ALLEGED_UNVERIFIED;
        }
    }

    private String toTitleCase(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        String[] words = s.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)))
                  .append(w.substring(1).toLowerCase())
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }
}
