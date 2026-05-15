package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.RetrievedSource;
import com.wgblackmon.aihealthcare.domain.model.SourceCitation;
import com.wgblackmon.aihealthcare.domain.model.VendorAssessment;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiReportPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VendorAssessmentService}.
 *
 * <p>All AI calls are mocked — no real API calls are made.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-14
 * @updated 2026-05-14
 */
@ExtendWith(MockitoExtension.class)
class VendorAssessmentServiceTest {

    @Mock
    private AiReportPort aiReportPort;

    private static final String TEMPLATE =
            "Research: {query}\nSources: {sources}";

    private VendorAssessmentService service;

    @BeforeEach
    void setUp() {
        service = new VendorAssessmentService(aiReportPort, TEMPLATE);
    }

    // -------------------------------------------------------------------------
    // Empty sources
    // -------------------------------------------------------------------------

    @Test
    void assess_emptySourcesReturnsEmptyListWithoutCallingAi() {
        List<VendorAssessment> result = service.assess(
                "AI diagnostics", Collections.emptyList(), Collections.emptyList());

        assertThat(result).isEmpty();
        verify(aiReportPort, never()).generate(anyString());
    }

    @Test
    void assess_nullSourcesReturnsEmptyListWithoutCallingAi() {
        List<VendorAssessment> result = service.assess(
                "AI diagnostics", null, Collections.emptyList());

        assertThat(result).isEmpty();
        verify(aiReportPort, never()).generate(anyString());
    }

    // -------------------------------------------------------------------------
    // Happy-path parsing
    // -------------------------------------------------------------------------

    @Test
    void assess_parsesVendorSectionsFromAiResponse() {
        List<RetrievedSource> sources = List.of(
                source("src-1", "Anthropic Claude in Radiology", "https://ex.com/1"));

        String aiResponse = "## Anthropic\n"
                + "STRENGTHS: Strong reasoning; Clinical note generation; Privacy controls\n"
                + "WEAKNESSES: High API cost; Limited fine-tuning\n"
                + "RELEVANCE: 0.9\n"
                + "ANALYSIS: Anthropic's Claude demonstrates strong performance [1].\n"
                + "\n"
                + "## OpenAI\n"
                + "STRENGTHS: GPT-4 vision; Large ecosystem\n"
                + "WEAKNESSES: Data retention concerns\n"
                + "RELEVANCE: 0.7\n"
                + "ANALYSIS: OpenAI offers multimodal capabilities [1].\n";

        when(aiReportPort.generate(anyString())).thenReturn(aiResponse);

        List<VendorAssessment> result = service.assess(
                "AI in radiology", sources, Collections.emptyList());

        assertThat(result).hasSize(2);

        VendorAssessment anthropic = result.get(0);
        assertThat(anthropic.vendorName()).isEqualTo("Anthropic");
        assertThat(anthropic.strengths()).containsExactly(
                "Strong reasoning", "Clinical note generation", "Privacy controls");
        assertThat(anthropic.weaknesses()).containsExactly("High API cost", "Limited fine-tuning");
        assertThat(anthropic.relevanceScore()).isEqualTo(0.9);

        VendorAssessment openai = result.get(1);
        assertThat(openai.vendorName()).isEqualTo("OpenAI");
        assertThat(openai.relevanceScore()).isEqualTo(0.7);
    }

    // -------------------------------------------------------------------------
    // Resilience
    // -------------------------------------------------------------------------

    @Test
    void assess_handlesInvalidRelevanceScoreWithDefault() {
        List<RetrievedSource> sources = List.of(
                source("src-1", "Google MedPaLM", "https://ex.com/1"));

        String aiResponse = "## Google\n"
                + "STRENGTHS: MedPaLM expertise\n"
                + "WEAKNESSES: Limited availability\n"
                + "RELEVANCE: not-a-number\n"
                + "ANALYSIS: Google leads in medical AI.\n";

        when(aiReportPort.generate(anyString())).thenReturn(aiResponse);

        List<VendorAssessment> result = service.assess(
                "medical AI", sources, Collections.emptyList());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).relevanceScore()).isEqualTo(0.5); // default
    }

    @Test
    void assess_skipsEmptyHeadingsInResponse() {
        List<RetrievedSource> sources = List.of(
                source("src-1", "AI Vendors Article", "https://ex.com/1"));

        // Response with a valid section and one empty/blank section
        String aiResponse = "## Microsoft\n"
                + "STRENGTHS: Azure HIPAA; Strong enterprise support\n"
                + "WEAKNESSES: Complex pricing\n"
                + "RELEVANCE: 0.8\n"
                + "ANALYSIS: Microsoft Azure provides HIPAA-compliant AI [1].\n"
                + "\n"
                + "##   \n"   // blank heading — should be skipped
                + "STRENGTHS: Something\n";

        when(aiReportPort.generate(anyString())).thenReturn(aiResponse);

        List<VendorAssessment> result = service.assess(
                "enterprise healthcare AI", sources, Collections.emptyList());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).vendorName()).isEqualTo("Microsoft");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private RetrievedSource source(String id, String title, String url) {
        return new RetrievedSource(id, title, url, "Excerpt text", "PERPLEXITY", Instant.now());
    }
}
