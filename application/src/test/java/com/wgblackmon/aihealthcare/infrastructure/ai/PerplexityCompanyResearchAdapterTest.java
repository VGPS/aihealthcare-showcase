package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyResearchPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PerplexityCompanyResearchAdapter}.
 *
 * <p>Tests parsing logic, schema building, and graceful fallback behavior.
 * Does not make live API calls — the adapter's RestClient is not exercised.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-02
 * @updated 2026-08-02
 */
class PerplexityCompanyResearchAdapterTest {

    @Test
    void isAvailable_falseWhenApiKeyBlank() {
        PerplexityCompanyResearchAdapter adapter = adapterWithKey("");
        assertThat(adapter.isAvailable()).isFalse();
    }

    @Test
    void isAvailable_falseWhenApiKeyPlaceholder() {
        PerplexityCompanyResearchAdapter adapter = adapterWithKey("placeholder-set-PERPLEXITY");
        assertThat(adapter.isAvailable()).isFalse();
    }

    @Test
    void isAvailable_trueWhenApiKeySet() {
        PerplexityCompanyResearchAdapter adapter = adapterWithKey("pplx-test-key");
        assertThat(adapter.isAvailable()).isTrue();
    }

    @Test
    void discoverCompanies_returnsEmptyWhenNotAvailable() {
        PerplexityCompanyResearchAdapter adapter = adapterWithKey("");
        CompanyResearchPort.DiscoveryResult result = adapter.discoverCompanies("find companies");

        assertThat(result.companyNames()).isEmpty();
        assertThat(result.citations()).isEmpty();
    }

    @Test
    void extractCompanyFields_returnsEmptyWhenNotAvailable() {
        PerplexityCompanyResearchAdapter adapter = adapterWithKey("");
        CompanyResearchPort.ExtractionResult result = adapter.extractCompanyFields("Tempus AI");

        assertThat(result.fields()).isEmpty();
        assertThat(result.citations()).isEmpty();
    }

    @Test
    void crossValidate_returnsNotValidatedWhenNotAvailable() {
        PerplexityCompanyResearchAdapter adapter = adapterWithKey("");
        CompanyResearchPort.ValidationResult result = adapter.crossValidate("Tempus AI");

        assertThat(result.validated()).isFalse();
    }

    @Test
    void parseCompanyNames_extractsBoldNames() {
        PerplexityCompanyResearchAdapter adapter = adapterWithKey("key");
        String content = "Here are companies: **Tempus AI** is a leader. **Viz.ai** does imaging. " +
                "**Abridge** focuses on documentation.";

        List<String> names = adapter.parseCompanyNames(content);

        assertThat(names).containsExactly("Tempus AI", "Viz.ai", "Abridge");
    }

    @Test
    void parseCompanyNames_deduplicates() {
        PerplexityCompanyResearchAdapter adapter = adapterWithKey("key");
        String content = "**Tempus AI** is great. Later, **Tempus AI** raised funding.";

        List<String> names = adapter.parseCompanyNames(content);

        assertThat(names).containsExactly("Tempus AI");
    }

    @Test
    void parseCompanyNames_filtersSectionHeaders() {
        PerplexityCompanyResearchAdapter adapter = adapterWithKey("key");
        String content = "**Key Companies in Healthcare AI** section. **Tempus AI** is one.";

        List<String> names = adapter.parseCompanyNames(content);

        assertThat(names).containsExactly("Tempus AI");
    }

    @Test
    void parseCompanyNames_returnsEmptyForNullContent() {
        PerplexityCompanyResearchAdapter adapter = adapterWithKey("key");
        assertThat(adapter.parseCompanyNames(null)).isEmpty();
        assertThat(adapter.parseCompanyNames("")).isEmpty();
    }

    @Test
    void parseJsonFields_parsesValidJson() {
        PerplexityCompanyResearchAdapter adapter = adapterWithKey("key");
        String json = "{\"name\":\"Tempus AI\",\"description\":\"Precision medicine\",\"foundedYear\":2015}";

        Map<String, Object> fields = adapter.parseJsonFields(json);

        assertThat(fields).containsEntry("name", "Tempus AI");
        assertThat(fields).containsEntry("description", "Precision medicine");
        assertThat(fields).containsEntry("foundedYear", 2015);
    }

    @Test
    void parseJsonFields_handlesMarkdownCodeBlock() {
        PerplexityCompanyResearchAdapter adapter = adapterWithKey("key");
        String json = "```json\n{\"name\":\"Test Co\"}\n```";

        Map<String, Object> fields = adapter.parseJsonFields(json);

        assertThat(fields).containsEntry("name", "Test Co");
    }

    @Test
    void parseJsonFields_convertsFoundersToJson() {
        PerplexityCompanyResearchAdapter adapter = adapterWithKey("key");
        String json = "{\"name\":\"Co\",\"founders\":[{\"name\":\"CEO\",\"title\":\"Chief\"}]}";

        Map<String, Object> fields = adapter.parseJsonFields(json);

        assertThat(fields).containsKey("foundersJson");
        assertThat(fields).doesNotContainKey("founders");
        assertThat(fields.get("foundersJson").toString()).contains("CEO");
    }

    @Test
    void parseJsonFields_returnsEmptyForInvalidJson() {
        PerplexityCompanyResearchAdapter adapter = adapterWithKey("key");

        Map<String, Object> fields = adapter.parseJsonFields("not json at all");

        assertThat(fields).isEmpty();
    }

    @Test
    void parseJsonFields_returnsEmptyForNull() {
        PerplexityCompanyResearchAdapter adapter = adapterWithKey("key");
        assertThat(adapter.parseJsonFields(null)).isEmpty();
    }

    @Test
    void stripThinkBlocks_removesThinkTags() {
        String content = "<think>reasoning here</think>\nActual content";
        assertThat(PerplexityCompanyResearchAdapter.stripThinkBlocks(content))
                .isEqualTo("Actual content");
    }

    @Test
    void stripThinkBlocks_handlesNull() {
        assertThat(PerplexityCompanyResearchAdapter.stripThinkBlocks(null)).isNull();
    }

    @Test
    void buildExtractionSchema_hasRequiredStructure() {
        PerplexityCompanyResearchAdapter adapter = adapterWithKey("key");
        Map<String, Object> responseFormat = adapter.buildExtractionSchema();

        assertThat(responseFormat).containsEntry("type", "json_schema");
        @SuppressWarnings("unchecked")
        Map<String, Object> jsonSchema = (Map<String, Object>) responseFormat.get("json_schema");
        assertThat(jsonSchema).containsEntry("name", "company_profile");
        assertThat(jsonSchema).containsEntry("strict", true);

        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) jsonSchema.get("schema");
        assertThat(schema).containsEntry("additionalProperties", false);
    }

    private PerplexityCompanyResearchAdapter adapterWithKey(String key) {
        return new PerplexityCompanyResearchAdapter(key, "sonar-pro", "sonar-pro", null);
    }
}
