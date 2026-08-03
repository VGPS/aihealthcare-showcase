package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyResearchPort;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.perplexity.PerplexityApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Infrastructure adapter implementing {@link CompanyResearchPort} via the
 * Perplexity Sonar API for AI healthcare company discovery.
 *
 * <p>Three API call types:
 * <ol>
 *   <li><strong>Discovery</strong> — {@code sonar-pro} broad research call to find companies</li>
 *   <li><strong>Extraction</strong> — {@code sonar-pro} with {@code response_format} JSON schema</li>
 *   <li><strong>Validation</strong> — {@code sonar} cross-check against curated lists</li>
 * </ol>
 *
 * <p>Includes exponential backoff retry (3 attempts, 2s/4s/8s) for rate limits
 * and transient errors. Graceful fallback when API key is absent.
 *
 * <p>Per Perplexity docs: {@code response_format} requires
 * {@code "additionalProperties": false} on all objects, no recursive schemas,
 * and a top-level {@code name} field. First schema-based call may take 10-30s
 * to compile.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-02
 * @updated 2026-08-02
 */
@Slf4j
@Component
public class PerplexityCompanyResearchAdapter implements CompanyResearchPort {

    private static final String BASE_URL = "https://api.perplexity.ai";
    private static final Pattern THINK_BLOCK = Pattern.compile(
            "<think>.*?</think>\\s*", Pattern.DOTALL);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int MAX_RETRIES = 3;
    private static final long[] BACKOFF_MS = {2000, 4000, 8000};

    private final String apiKey;
    private final String discoveryModel;
    private final String extractionModel;
    private final RestClient restClient;

    /**
     * Spring-managed constructor.
     */
    @Autowired
    public PerplexityCompanyResearchAdapter(
            @Value("${aihealthcare.perplexity.api-key:}") String apiKey,
            @Value("${aihealthcare.company-discovery.discovery-model:sonar-pro}") String discoveryModel,
            @Value("${aihealthcare.company-discovery.extraction-model:sonar-pro}") String extractionModel) {
        this(apiKey, discoveryModel, extractionModel, RestClient.builder().baseUrl(BASE_URL).build());
    }

    /**
     * Package-private constructor for testing.
     */
    PerplexityCompanyResearchAdapter(String apiKey, String discoveryModel,
                                      String extractionModel, RestClient restClient) {
        log.debug("PerplexityCompanyResearchAdapter() | apiKeyPresent={}, discoveryModel={}, extractionModel={}",
                apiKey != null && !apiKey.isBlank(), discoveryModel, extractionModel);
        this.apiKey = apiKey;
        this.discoveryModel = discoveryModel;
        this.extractionModel = extractionModel;
        this.restClient = restClient;
        if (apiKey == null || apiKey.isBlank()) {
            log.info("PerplexityCompanyResearchAdapter() | PERPLEXITY_API_KEY not set — adapter disabled");
        } else {
            log.info("PerplexityCompanyResearchAdapter() | Company research adapter active " +
                    "(discovery={}, extraction={})", discoveryModel, extractionModel);
        }
        log.debug("PerplexityCompanyResearchAdapter() | return=void");
    }

    @Override
    public DiscoveryResult discoverCompanies(String prompt) {
        log.debug("discoverCompanies() | promptLength={}", prompt != null ? prompt.length() : 0);

        if (!isAvailable()) {
            log.info("discoverCompanies() | API key not available — returning empty");
            DiscoveryResult result = new DiscoveryResult(List.of(), List.of(), "");
            log.debug("discoverCompanies() | return=empty");
            return result;
        }

        Map<String, Object> requestBody = buildChatRequest(discoveryModel, prompt, null);
        PerplexityApiResponse response = callWithRetry(requestBody);

        if (response == null) {
            DiscoveryResult result = new DiscoveryResult(List.of(), List.of(), "");
            log.debug("discoverCompanies() | return=empty (null response)");
            return result;
        }

        String content = extractContent(response);
        List<String> citations = response.citations() != null ? response.citations() : List.of();
        List<String> companyNames = parseCompanyNames(content);

        DiscoveryResult result = new DiscoveryResult(companyNames, citations, content);
        log.info("discoverCompanies() | discovered {} companies, {} citations",
                companyNames.size(), citations.size());
        log.debug("discoverCompanies() | return={} companies", companyNames.size());
        return result;
    }

    @Override
    public ExtractionResult extractCompanyFields(String companyName) {
        log.debug("extractCompanyFields() | companyName={}", companyName);

        if (!isAvailable()) {
            log.info("extractCompanyFields() | API key not available — returning empty");
            ExtractionResult result = new ExtractionResult(Map.of(), List.of());
            log.debug("extractCompanyFields() | return=empty");
            return result;
        }

        String prompt = "Extract detailed company information for \"" + companyName +
                "\" operating in the AI healthcare space. " +
                "Research their website, recent funding, leadership, and market position. " +
                "Provide accurate, factual data only. If information is not available, use null.";

        Map<String, Object> responseFormat = buildExtractionSchema();
        Map<String, Object> requestBody = buildChatRequest(extractionModel, prompt, responseFormat);
        PerplexityApiResponse response = callWithRetry(requestBody);

        if (response == null) {
            ExtractionResult result = new ExtractionResult(Map.of(), List.of());
            log.debug("extractCompanyFields() | return=empty (null response)");
            return result;
        }

        String content = extractContent(response);
        content = stripThinkBlocks(content);
        List<String> citations = response.citations() != null ? response.citations() : List.of();

        Map<String, Object> fields = parseJsonFields(content);
        ExtractionResult result = new ExtractionResult(fields, citations);
        log.info("extractCompanyFields() | extracted {} fields for '{}', {} citations",
                fields.size(), companyName, citations.size());
        log.debug("extractCompanyFields() | return={} fields", fields.size());
        return result;
    }

    @Override
    public ValidationResult crossValidate(String companyName) {
        log.debug("crossValidate() | companyName={}", companyName);

        if (!isAvailable()) {
            log.info("crossValidate() | API key not available — returning not validated");
            ValidationResult result = new ValidationResult(false, List.of(), "API key not available");
            log.debug("crossValidate() | return=not validated");
            return result;
        }

        String prompt = "Is \"" + companyName + "\" a real company operating in AI healthcare? " +
                "Check if it appears on any of these curated industry lists or reports: " +
                "CB Insights AI 100 healthcare companies, Rock Health digital health funding reports, " +
                "AVIA Marketplace health AI companies, StartUp Health portfolio, " +
                "Fierce Healthcare AI companies to watch. " +
                "Answer with YES or NO followed by which lists include this company and relevant details. " +
                "Cite your sources.";

        Map<String, Object> requestBody = buildChatRequest("sonar", prompt, null);
        PerplexityApiResponse response = callWithRetry(requestBody);

        if (response == null) {
            ValidationResult result = new ValidationResult(false, List.of(), "API call failed");
            log.debug("crossValidate() | return=not validated (null response)");
            return result;
        }

        String content = extractContent(response);
        List<String> citations = response.citations() != null ? response.citations() : List.of();

        boolean validated = content != null &&
                (content.toUpperCase().startsWith("YES") || content.toUpperCase().contains("\nYES"));

        ValidationResult result = new ValidationResult(validated, citations, content);
        log.info("crossValidate() | company='{}', validated={}, {} citations",
                companyName, validated, citations.size());
        log.debug("crossValidate() | return=validated={}", validated);
        return result;
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("placeholder-set-");
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a standard Perplexity chat completions request body.
     */
    Map<String, Object> buildChatRequest(String model, String prompt,
                                          Map<String, Object> responseFormat) {
        log.debug("buildChatRequest() | model={}, promptLength={}, hasResponseFormat={}",
                model, prompt.length(), responseFormat != null);

        Map<String, String> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(userMessage);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);

        if (responseFormat != null) {
            requestBody.put("response_format", responseFormat);
        }

        log.debug("buildChatRequest() | return=requestBody");
        return requestBody;
    }

    /**
     * Builds the JSON schema for structured extraction per Perplexity docs:
     * additionalProperties: false on all objects, top-level name field required.
     */
    Map<String, Object> buildExtractionSchema() {
        log.debug("buildExtractionSchema() |");

        // Founder object schema
        Map<String, Object> founderProperties = new LinkedHashMap<>();
        founderProperties.put("name", Map.of("type", "string", "description", "Founder's full name"));
        founderProperties.put("title", Map.of("type", "string", "description", "Founder's title or role"));

        Map<String, Object> founderSchema = new LinkedHashMap<>();
        founderSchema.put("type", "object");
        founderSchema.put("properties", founderProperties);
        founderSchema.put("required", List.of("name", "title"));
        founderSchema.put("additionalProperties", false);

        // Top-level properties
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", Map.of("type", "string", "description", "Company display name"));
        properties.put("domain", Map.of("type", "string", "description", "Company website domain (e.g. tempus.com)"));
        properties.put("description", Map.of("type", "string", "description", "One-paragraph company description"));
        properties.put("hqLocation", Map.of("type", "string", "description", "Headquarters city and state/country"));
        properties.put("foundedYear", Map.of("type", "integer", "description", "Year the company was founded"));
        properties.put("sector", Map.of("type", "string", "description", "Broad sector (e.g. Healthcare AI)"));
        properties.put("subSector", Map.of("type", "string", "description",
                "Specific focus area (e.g. clinical documentation, diagnostics, drug discovery, imaging, RCM)"));
        properties.put("fundingStage", Map.of("type", "string", "description",
                "Latest funding stage (e.g. Seed, Series A, Series B, Series C, Public, Acquired)"));
        properties.put("estimatedFunding", Map.of("type", "string", "description",
                "Total estimated funding amount as text (e.g. $200M, Undisclosed)"));
        properties.put("founders", Map.of(
                "type", "array",
                "items", founderSchema,
                "description", "Key founders or executives"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("name", "description", "sector"));
        schema.put("additionalProperties", false);

        Map<String, Object> jsonSchema = new LinkedHashMap<>();
        jsonSchema.put("name", "company_profile");
        jsonSchema.put("strict", true);
        jsonSchema.put("schema", schema);

        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "json_schema");
        responseFormat.put("json_schema", jsonSchema);

        log.debug("buildExtractionSchema() | return=schema");
        return responseFormat;
    }

    /**
     * Calls the Perplexity API with exponential backoff retry.
     */
    PerplexityApiResponse callWithRetry(Map<String, Object> requestBody) {
        log.debug("callWithRetry() | model={}", requestBody.get("model"));

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                PerplexityApiResponse response = restClient.post()
                        .uri("/chat/completions")
                        .header("Authorization", "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(PerplexityApiResponse.class);

                log.debug("callWithRetry() | return=response (attempt {})", attempt + 1);
                return response;
            } catch (Exception e) {
                log.warn("callWithRetry() | attempt {}/{} failed: {}",
                        attempt + 1, MAX_RETRIES, e.getMessage());

                if (attempt < MAX_RETRIES - 1) {
                    try {
                        Thread.sleep(BACKOFF_MS[attempt]);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("callWithRetry() | interrupted during backoff");
                        log.debug("callWithRetry() | return=null (interrupted)");
                        return null;
                    }
                }
            }
        }

        log.error("callWithRetry() | all {} attempts failed", MAX_RETRIES);
        log.debug("callWithRetry() | return=null (exhausted retries)");
        return null;
    }

    /**
     * Extracts content from the first choice in the response.
     */
    String extractContent(PerplexityApiResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            return null;
        }
        PerplexityApiResponse.PerplexityChoice choice = response.choices().get(0);
        if (choice.message() == null) {
            return null;
        }
        return choice.message().content();
    }

    /**
     * Strips {@code <think>...</think>} reasoning blocks from content.
     */
    static String stripThinkBlocks(String content) {
        if (content == null) {
            return null;
        }
        Matcher matcher = THINK_BLOCK.matcher(content);
        return matcher.replaceAll("").trim();
    }

    /**
     * Parses company names from discovery response content.
     * Looks for patterns like "**CompanyName**" or "CompanyName -" or numbered lists.
     */
    List<String> parseCompanyNames(String content) {
        log.debug("parseCompanyNames() | contentLength={}", content != null ? content.length() : 0);

        if (content == null || content.isBlank()) {
            log.debug("parseCompanyNames() | return=[]");
            return List.of();
        }

        List<String> names = new ArrayList<>();

        // Pattern 1: **Bold company names** (most common in Perplexity responses)
        Pattern boldPattern = Pattern.compile("\\*\\*([^*]+?)\\*\\*");
        Matcher boldMatcher = boldPattern.matcher(content);
        while (boldMatcher.find()) {
            String candidate = boldMatcher.group(1).trim();
            // Filter out section headers and descriptions
            if (isLikelyCompanyName(candidate)) {
                names.add(candidate);
            }
        }

        // Deduplicate preserving order
        List<String> deduped = new ArrayList<>();
        for (String name : names) {
            boolean duplicate = false;
            for (String existing : deduped) {
                if (existing.equalsIgnoreCase(name)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                deduped.add(name);
            }
        }

        log.debug("parseCompanyNames() | return={} names", deduped.size());
        return deduped;
    }

    /**
     * Heuristic filter: likely a company name if it's short, not a full sentence,
     * and not a common section header.
     */
    private boolean isLikelyCompanyName(String candidate) {
        if (candidate.length() > 60 || candidate.length() < 2) {
            return false;
        }
        // Skip section headers
        String lower = candidate.toLowerCase();
        if (lower.startsWith("key ") || lower.startsWith("notable ") ||
                lower.startsWith("overview") || lower.startsWith("summary") ||
                lower.startsWith("conclusion") || lower.startsWith("sources") ||
                lower.contains("companies") || lower.contains("startups")) {
            return false;
        }
        // Skip full sentences (contain verbs/many words)
        if (candidate.split("\\s+").length > 6) {
            return false;
        }
        return true;
    }

    /**
     * Parses JSON content into a field map. Handles both raw JSON and JSON
     * embedded in markdown code blocks.
     */
    Map<String, Object> parseJsonFields(String content) {
        log.debug("parseJsonFields() | contentLength={}", content != null ? content.length() : 0);

        if (content == null || content.isBlank()) {
            log.debug("parseJsonFields() | return=empty map (null content)");
            return Map.of();
        }

        // Strip markdown code block if present
        String json = content.trim();
        if (json.startsWith("```json")) {
            json = json.substring(7);
        } else if (json.startsWith("```")) {
            json = json.substring(3);
        }
        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3);
        }
        json = json.trim();

        try {
            Map<String, Object> fields = MAPPER.readValue(json,
                    new TypeReference<Map<String, Object>>() {});

            // Convert founders array to JSON string for storage
            Object founders = fields.get("founders");
            if (founders != null) {
                fields.put("foundersJson", MAPPER.writeValueAsString(founders));
                fields.remove("founders");
            }

            log.debug("parseJsonFields() | return={} fields", fields.size());
            return fields;
        } catch (Exception e) {
            log.warn("parseJsonFields() | JSON parse failed: {}", e.getMessage());
            log.debug("parseJsonFields() | return=empty map (parse error)");
            return Map.of();
        }
    }
}
