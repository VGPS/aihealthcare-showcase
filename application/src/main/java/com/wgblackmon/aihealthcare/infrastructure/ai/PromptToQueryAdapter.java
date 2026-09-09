package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.model.DataFeed;
import com.wgblackmon.aihealthcare.domain.model.DataParameter;
import com.wgblackmon.aihealthcare.domain.model.DataQueryPlan;
import com.wgblackmon.aihealthcare.domain.port.outbound.PromptToQueryPort;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * LLM adapter that resolves free-text customer input into a constrained
 * {@link DataQueryPlan}.
 *
 * <p>Uses the project's existing {@link ChatClient} + {@link PromptLoaderService}
 * pattern with temperature 0.0 for deterministic output. The LLM receives the
 * feed's parameter schema and is instructed to emit only the plan JSON — no SQL,
 * no table names, no fields outside the schema.
 *
 * <p>A null or unparseable LLM response is treated as a rejection (returns null),
 * never as an empty plan.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Slf4j
@Component
public class PromptToQueryAdapter implements PromptToQueryPort {

    private final ChatClient chatClient;
    private final PromptLoaderService promptLoaderService;
    private final ObjectMapper objectMapper;

    public PromptToQueryAdapter(ChatClient.Builder chatClientBuilder,
                                PromptLoaderService promptLoaderService,
                                ObjectMapper objectMapper) {
        log.debug("PromptToQueryAdapter() | promptLoaderService={}", promptLoaderService.getClass().getSimpleName());
        this.chatClient = chatClientBuilder
                .defaultOptions(org.springframework.ai.chat.prompt.ChatOptions.builder()
                        .temperature(0.0)
                        .build())
                .build();
        this.promptLoaderService = promptLoaderService;
        this.objectMapper = objectMapper;
    }

    @Override
    public DataQueryPlan resolve(String promptText, DataFeed feed) {
        log.debug("resolve() | promptText={}chars, feedId={}", promptText != null ? promptText.length() : 0, feed.feedId());

        String template = promptLoaderService.load("enterprise-query-plan.txt");
        String fullPrompt = template
                .replace("{feedId}", feed.feedId())
                .replace("{feedDescription}", feed.description())
                .replace("{parameterSchema}", buildParameterSchema(feed))
                .replace("{maxRows}", String.valueOf(feed.maxRowLimit()))
                .replace("{userQuery}", promptText != null ? promptText : "");

        String response;
        try {
            response = chatClient.prompt(fullPrompt).call().content();
        } catch (Exception e) {
            log.warn("resolve() | LLM call failed: {}", e.getMessage());
            log.debug("resolve() | return=null (LLM error)");
            return null;
        }

        if (response == null || response.isBlank()) {
            log.warn("resolve() | Empty LLM response");
            log.debug("resolve() | return=null (empty response)");
            return null;
        }

        log.debug("resolve() | LLM response length={} chars", response.length());

        DataQueryPlan result = parseResponse(response, feed);
        log.debug("resolve() | return={}", result);
        return result;
    }

    private DataQueryPlan parseResponse(String response, DataFeed feed) {
        try {
            String json = extractJson(response);
            if (json == null) {
                log.warn("parseResponse() | No JSON block found in response");
                return null;
            }

            var node = objectMapper.readTree(json);

            String feedId = node.has("feedId") ? node.get("feedId").asText() : feed.feedId();
            List<String> keywords = readStringList(node, "keywords");
            LocalDate dateFrom = readDate(node, "dateFrom");
            LocalDate dateTo = readDate(node, "dateTo");
            List<String> states = readStringList(node, "states");
            List<String> categories = readStringList(node, "categories");
            String sortBy = node.has("sortBy") && !node.get("sortBy").isNull()
                    ? node.get("sortBy").asText() : null;
            int limit = node.has("limit") ? node.get("limit").asInt(100) : 100;
            if (limit <= 0) limit = 100;

            DataQueryPlan plan = new DataQueryPlan(feedId, keywords, dateFrom, dateTo,
                    states, categories, sortBy, limit);
            return plan;
        } catch (Exception e) {
            log.warn("parseResponse() | Failed to parse LLM plan: {}", e.getMessage());
            return null;
        }
    }

    private String extractJson(String response) {
        int braceStart = response.indexOf('{');
        if (braceStart < 0) return null;
        int depth = 0;
        for (int i = braceStart; i < response.length(); i++) {
            char c = response.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return response.substring(braceStart, i + 1);
                }
            }
        }
        return null;
    }

    private List<String> readStringList(com.fasterxml.jackson.databind.JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) return List.of();
        var arr = node.get(field);
        if (arr.isArray()) {
            var builder = new java.util.ArrayList<String>();
            arr.forEach(n -> {
                String text = n.asText();
                if (text != null && !text.isBlank()) builder.add(text);
            });
            return List.copyOf(builder);
        }
        String text = arr.asText();
        if (text != null && !text.isBlank()) return List.of(text);
        return List.of();
    }

    private LocalDate readDate(com.fasterxml.jackson.databind.JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) return null;
        String text = node.get(field).asText();
        if (text == null || text.isBlank() || "null".equals(text)) return null;
        try {
            return LocalDate.parse(text);
        } catch (Exception e) {
            log.warn("readDate() | Unparseable date for {}: {}", field, text);
            return null;
        }
    }

    private String buildParameterSchema(DataFeed feed) {
        StringBuilder sb = new StringBuilder();
        for (DataParameter p : feed.parameters()) {
            sb.append("- ").append(p.name()).append(" (").append(p.type());
            if (p.required()) sb.append(", required");
            if (p.defaultValue() != null) sb.append(", default=").append(p.defaultValue());
            if (!p.allowedValues().isEmpty()) sb.append(", allowed=").append(p.allowedValues());
            sb.append("): ").append(p.label()).append("\n");
        }
        return sb.toString();
    }
}
