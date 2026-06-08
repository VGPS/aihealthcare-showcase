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
            "Research: {query}\nSources: {sources}\nTotal: {totalSources}\nMin: {minVendors}";

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
                "AI diagnostics", Collections.emptyList(), Collections.emptyList(), 5, "DOC_FREQUENCY");

        assertThat(result).isEmpty();
        verify(aiReportPort, never()).generate(anyString());
    }

    @Test
    void assess_nullSourcesReturnsEmptyListWithoutCallingAi() {
        List<VendorAssessment> result = service.assess(
                "AI diagnostics", null, Collections.emptyList(), 5, "DOC_FREQUENCY");

        assertThat(result).isEmpty();
        verify(aiReportPort, never()).generate(anyString());
    }

    // -------------------------------------------------------------------------
    // Happy-path parsing
    // -------------------------------------------------------------------------

    @Test
    void assess_parsesVendorSectionsFromAiResponse() {
        List<RetrievedSource> sources = List.of(
                source("src-1", "Anthropic Claude in Radiology", "https://ex.com/1"),
                source("src-2", "OpenAI in Healthcare", "https://ex.com/2"),
                source("src-3", "AI Diagnostics Overview", "https://ex.com/3"),
                source("src-4", "Claude for Clinical Notes", "https://ex.com/4"),
                source("src-5", "GPT-4 Medical Vision", "https://ex.com/5"));

        String aiResponse = "## Anthropic\n"
                + "STRENGTHS: Strong reasoning; Clinical note generation; Privacy controls\n"
                + "WEAKNESSES: High API cost; Limited fine-tuning\n"
                + "MENTIONS: 4\n"
                + "ANALYSIS: Anthropic's Claude demonstrates strong performance [1].\n"
                + "\n"
                + "## OpenAI\n"
                + "STRENGTHS: GPT-4 vision; Large ecosystem\n"
                + "WEAKNESSES: Data retention concerns\n"
                + "MENTIONS: 3\n"
                + "ANALYSIS: OpenAI offers multimodal capabilities [1].\n";

        when(aiReportPort.generate(anyString())).thenReturn(aiResponse);

        List<VendorAssessment> result = service.assess(
                "AI in radiology", sources, Collections.emptyList(), 5, "DOC_FREQUENCY");

        assertThat(result).hasSize(2);

        VendorAssessment anthropic = result.get(0);
        assertThat(anthropic.vendorName()).isEqualTo("Anthropic");
        assertThat(anthropic.strengths()).containsExactly(
                "Strong reasoning", "Clinical note generation", "Privacy controls");
        assertThat(anthropic.weaknesses()).containsExactly("High API cost", "Limited fine-tuning");
        assertThat(anthropic.mentionCount()).isEqualTo(4);
        assertThat(anthropic.totalSources()).isEqualTo(5);
        assertThat(anthropic.relevanceScore()).isEqualTo(0.8); // 4/5

        VendorAssessment openai = result.get(1);
        assertThat(openai.vendorName()).isEqualTo("OpenAI");
        assertThat(openai.mentionCount()).isEqualTo(3);
        assertThat(openai.relevanceScore()).isEqualTo(0.6); // 3/5
    }

    // -------------------------------------------------------------------------
    // Resilience
    // -------------------------------------------------------------------------

    @Test
    void assess_handlesInvalidMentionsWithDefault() {
        List<RetrievedSource> sources = List.of(
                source("src-1", "Google MedPaLM", "https://ex.com/1"));

        String aiResponse = "## Google\n"
                + "STRENGTHS: MedPaLM expertise\n"
                + "WEAKNESSES: Limited availability\n"
                + "MENTIONS: not-a-number\n"
                + "ANALYSIS: Google leads in medical AI.\n";

        when(aiReportPort.generate(anyString())).thenReturn(aiResponse);

        List<VendorAssessment> result = service.assess(
                "medical AI", sources, Collections.emptyList(), 5, "DOC_FREQUENCY");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).mentionCount()).isEqualTo(1); // default
        assertThat(result.get(0).relevanceScore()).isEqualTo(1.0); // 1/1
    }

    @Test
    void assess_skipsEmptyHeadingsInResponse() {
        List<RetrievedSource> sources = List.of(
                source("src-1", "AI Vendors Article", "https://ex.com/1"));

        // Response with a valid section and one empty/blank section
        String aiResponse = "## Microsoft\n"
                + "STRENGTHS: Azure HIPAA; Strong enterprise support\n"
                + "WEAKNESSES: Complex pricing\n"
                + "MENTIONS: 1\n"
                + "ANALYSIS: Microsoft Azure provides HIPAA-compliant AI [1].\n"
                + "\n"
                + "##   \n"   // blank heading — should be skipped
                + "STRENGTHS: Something\n";

        when(aiReportPort.generate(anyString())).thenReturn(aiResponse);

        List<VendorAssessment> result = service.assess(
                "enterprise healthcare AI", sources, Collections.emptyList(), 5, "DOC_FREQUENCY");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).vendorName()).isEqualTo("Microsoft");
    }

    // -------------------------------------------------------------------------
    // Sort ordering
    // -------------------------------------------------------------------------

    @Test
    void assess_sortsVendorsByRelevanceDescending() {
        List<RetrievedSource> sources = List.of(
                source("src-1", "AI Vendors", "https://ex.com/1"),
                source("src-2", "More AI Vendors", "https://ex.com/2"),
                source("src-3", "Even More", "https://ex.com/3"),
                source("src-4", "Yet More", "https://ex.com/4"),
                source("src-5", "Final", "https://ex.com/5"),
                source("src-6", "Extra", "https://ex.com/6"),
                source("src-7", "Bonus", "https://ex.com/7"),
                source("src-8", "Last", "https://ex.com/8"),
                source("src-9", "End", "https://ex.com/9"),
                source("src-10", "Done", "https://ex.com/10"));

        String aiResponse = "## LowVendor\n"
                + "STRENGTHS: Basic features\n"
                + "WEAKNESSES: Limited scope\n"
                + "MENTIONS: 2\n"
                + "ANALYSIS: Low relevance vendor [1].\n"
                + "\n"
                + "## HighVendor\n"
                + "STRENGTHS: Top performance\n"
                + "WEAKNESSES: None identified\n"
                + "MENTIONS: 8\n"
                + "ANALYSIS: Highly relevant vendor [1].\n"
                + "\n"
                + "## MidVendor\n"
                + "STRENGTHS: Solid offering\n"
                + "WEAKNESSES: Some gaps\n"
                + "MENTIONS: 5\n"
                + "ANALYSIS: Mid-range vendor [1].\n";

        when(aiReportPort.generate(anyString())).thenReturn(aiResponse);

        List<VendorAssessment> result = service.assess(
                "vendor comparison", sources, Collections.emptyList(), 3, "DOC_FREQUENCY");

        assertThat(result).hasSize(3);
        assertThat(result.get(0).vendorName()).isEqualTo("HighVendor");  // 8/10 = 0.8
        assertThat(result.get(1).vendorName()).isEqualTo("MidVendor");   // 5/10 = 0.5
        assertThat(result.get(2).vendorName()).isEqualTo("LowVendor");   // 2/10 = 0.2
    }

    // -------------------------------------------------------------------------
    // TF-IDF scoring
    // -------------------------------------------------------------------------

    @Test
    void assess_tfIdfScoringRewardsSpecificityOverFrequency() {
        List<RetrievedSource> sources = List.of(
                source("src-1", "A", "https://ex.com/1"),
                source("src-2", "B", "https://ex.com/2"),
                source("src-3", "C", "https://ex.com/3"),
                source("src-4", "D", "https://ex.com/4"),
                source("src-5", "E", "https://ex.com/5"),
                source("src-6", "F", "https://ex.com/6"),
                source("src-7", "G", "https://ex.com/7"),
                source("src-8", "H", "https://ex.com/8"),
                source("src-9", "I", "https://ex.com/9"),
                source("src-10", "J", "https://ex.com/10"));

        // GenericVendor appears in ALL 10 docs, SpecificVendor in 5
        String aiResponse = "## GenericVendor\n"
                + "STRENGTHS: Ubiquitous\n"
                + "WEAKNESSES: Generic\n"
                + "MENTIONS: 10\n"
                + "ANALYSIS: Appears everywhere [1].\n"
                + "\n"
                + "## SpecificVendor\n"
                + "STRENGTHS: Targeted solution\n"
                + "WEAKNESSES: Niche\n"
                + "MENTIONS: 5\n"
                + "ANALYSIS: Appears in specific sources [1].\n";

        when(aiReportPort.generate(anyString())).thenReturn(aiResponse);

        List<VendorAssessment> result = service.assess(
                "vendor comparison", sources, Collections.emptyList(), 2, "TF_IDF");

        assertThat(result).hasSize(2);
        // TF-IDF: GenericVendor tf=1.0, idf=log(1+1)=0.693 → raw=0.693
        //         SpecificVendor tf=0.5, idf=log(1+2)=1.099 → raw=0.549
        // GenericVendor still scores higher but the gap is narrower than with doc frequency
        assertThat(result.get(0).vendorName()).isEqualTo("GenericVendor");
        assertThat(result.get(1).vendorName()).isEqualTo("SpecificVendor");
        // Both should have non-trivial scores (TF-IDF normalizes top to 1.0)
        assertThat(result.get(0).relevanceScore()).isEqualTo(1.0);
        assertThat(result.get(1).relevanceScore()).isGreaterThan(0.7); // much closer than 0.5 with doc-freq
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private RetrievedSource source(String id, String title, String url) {
        return new RetrievedSource(id, title, url, "Excerpt text", "PERPLEXITY", Instant.now());
    }
}
