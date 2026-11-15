package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.model.HarmType;
import com.wgblackmon.aihealthcare.domain.model.IncidentStatus;
import com.wgblackmon.aihealthcare.domain.model.IncidentType;
import com.wgblackmon.aihealthcare.domain.model.LiabilityIncident;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.LiabilityClassificationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Calls Anthropic Claude to classify a {@link NewsArticle} as an AI healthcare
 * liability incident. Returns a {@link LiabilityIncident} with
 * {@code status = PENDING_REVIEW} on HIGH/MEDIUM confidence hits, or
 * {@code Optional.empty()} otherwise.
 *
 * <p>Body text is truncated to {@value #BODY_CHAR_LIMIT} characters before
 * sending to keep per-call token cost low.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-15
 * @updated 2026-11-15
 */
@Slf4j
@Component
public class LiabilityClassificationAdapter implements LiabilityClassificationPort {

    static final int BODY_CHAR_LIMIT = 2000;

    private final ChatClient chatClient;
    private final String     classificationModel;
    private final String     promptTemplate;
    private final ObjectMapper objectMapper;

    public LiabilityClassificationAdapter(
            ChatClient.Builder chatClientBuilder,
            @Value("${aihealthcare.liability.classification-model:claude-haiku-4-5-20251001}") String classificationModel,
            @Value("classpath:prompts/liability-classify.txt") Resource promptResource,
            ObjectMapper objectMapper) throws IOException {
        log.debug("LiabilityClassificationAdapter() | classificationModel={}", classificationModel);
        this.chatClient          = chatClientBuilder.build();
        this.classificationModel = classificationModel;
        this.promptTemplate      = StreamUtils.copyToString(promptResource.getInputStream(), StandardCharsets.UTF_8);
        this.objectMapper        = objectMapper;
    }

    @Override
    public Optional<LiabilityIncident> classify(NewsArticle article) {
        log.debug("classify() | articleId={}, title={}", article.articleId(), article.title());

        String body = article.bodyText() != null
                ? article.bodyText().substring(0, Math.min(article.bodyText().length(), BODY_CHAR_LIMIT))
                : "";
        String prompt = promptTemplate
                .replace("{title}", article.title() != null ? article.title() : "")
                .replace("{body}", body);

        String raw;
        try {
            raw = chatClient.prompt()
                    .options(AnthropicChatOptions.builder()
                            .model(classificationModel)
                            .maxTokens(512)
                            .build())
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("classify() | LLM call failed for articleId={}: {}", article.articleId(), e.getMessage());
            log.debug("classify() | return=empty (LLM error)");
            return Optional.empty();
        }

        log.debug("classify() | raw response length={}", raw != null ? raw.length() : 0);

        ClassificationResponse response;
        try {
            response = objectMapper.readValue(extractJson(raw), ClassificationResponse.class);
        } catch (Exception e) {
            log.warn("classify() | JSON parse failed for articleId={}: {}", article.articleId(), e.getMessage());
            log.debug("classify() | return=empty (parse error)");
            return Optional.empty();
        }

        if (!response.isIncident || "LOW".equals(response.confidence)) {
            log.debug("classify() | return=empty (isIncident={}, confidence={})", response.isIncident, response.confidence);
            return Optional.empty();
        }

        IncidentType incidentType;
        HarmType harmType;
        try {
            incidentType = IncidentType.valueOf(response.incidentType);
            harmType     = HarmType.valueOf(response.harmType);
        } catch (IllegalArgumentException e) {
            log.warn("classify() | unknown enum for articleId={}: {}", article.articleId(), e.getMessage());
            log.debug("classify() | return=empty (enum error)");
            return Optional.empty();
        }

        String sourceUrl = article.url() != null ? article.url().toString() : null;
        Instant now = Instant.now();

        LiabilityIncident incident = new LiabilityIncident(
                UUID.randomUUID().toString(),
                incidentType,
                harmType,
                IncidentStatus.PENDING_REVIEW,
                truncate(response.title, 500),
                truncate(response.plaintiff, 500),
                truncate(response.defendant, 500),
                truncate(response.legalTheory, 200),
                truncate(response.court, 200),
                truncate(response.caseNumber, 100),
                parseDate(response.harmDate),
                parseDate(response.filingDate),
                null,
                truncate(sourceUrl, 2000),
                "Auto-proposed by LLM classifier (confidence=" + response.confidence + ")",
                now,
                now
        );

        log.debug("classify() | return=incident title={}, type={}, confidence={}",
                  incident.title(), incident.incidentType(), response.confidence);
        return Optional.of(incident);
    }

    private String extractJson(String raw) {
        if (raw == null) return "{}";
        String stripped = raw
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*", "")
                .trim();
        int start = stripped.indexOf('{');
        int end   = stripped.lastIndexOf('}');
        return (start >= 0 && end > start) ? stripped.substring(start, end + 1) : stripped;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank() || "null".equalsIgnoreCase(dateStr)) return null;
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            log.debug("parseDate() | could not parse '{}': {}", dateStr, e.getMessage());
            return null;
        }
    }

    private String truncate(String value, int max) {
        if (value == null || "null".equalsIgnoreCase(value)) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }

    // -------------------------------------------------------------------------
    // Internal DTO for Jackson deserialization — not part of the domain
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ClassificationResponse {
        public boolean isIncident;
        public String  confidence;
        public String  incidentType;
        public String  harmType;
        public String  title;
        public String  defendant;
        public String  plaintiff;
        public String  legalTheory;
        public String  court;
        public String  caseNumber;
        public String  harmDate;
        public String  filingDate;
    }
}
