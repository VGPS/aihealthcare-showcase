package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.model.IncidentStatus;
import com.wgblackmon.aihealthcare.domain.model.LiabilityIncident;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LiabilityClassificationAdapter} response parsing.
 *
 * <p>Uses a mocked {@link ChatClient} to verify JSON parsing, confidence
 * filtering, and enum mapping without making real LLM calls.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-15
 * @updated 2026-11-15
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LiabilityClassificationAdapterTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callSpec;

    @Mock
    private Resource promptResource;

    private LiabilityClassificationAdapter adapter;

    private static final String PROMPT_TEMPLATE = "Classify: title={title} body={body}";

    @BeforeEach
    void setUp() throws Exception {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(promptResource.getInputStream())
                .thenReturn(new ByteArrayInputStream(PROMPT_TEMPLATE.getBytes(StandardCharsets.UTF_8)));
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.options(any())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        adapter = new LiabilityClassificationAdapter(
                chatClientBuilder, "claude-haiku-4-5-20251001", promptResource, new ObjectMapper());
    }

    @Test
    void classify_highConfidenceIncident_returnsProposal() {
        when(callSpec.content()).thenReturn("""
                {
                  "isIncident": true,
                  "confidence": "HIGH",
                  "incidentType": "LAWSUIT",
                  "harmType": "MEDICATION_ERROR",
                  "title": "Family sues OpenAI over wrongful death",
                  "defendant": "OpenAI",
                  "plaintiff": "Smith Family",
                  "legalTheory": "Negligence",
                  "court": "N.D. Cal.",
                  "caseNumber": "26-cv-1234",
                  "harmDate": "2026-10-15",
                  "filingDate": "2026-11-01"
                }
                """);

        NewsArticle article = buildArticle("lt1-001", "Wrongful death lawsuit against OpenAI",
                "Full body text about the lawsuit.");

        Optional<LiabilityIncident> result = adapter.classify(article);

        assertThat(result).isPresent();
        assertThat(result.get().defendant()).isEqualTo("OpenAI");
        assertThat(result.get().status()).isEqualTo(IncidentStatus.PENDING_REVIEW);
        assertThat(result.get().notes()).startsWith("Auto-proposed by LLM classifier");
    }

    @Test
    void classify_lowConfidence_returnsEmpty() {
        when(callSpec.content()).thenReturn("""
                {
                  "isIncident": true,
                  "confidence": "LOW",
                  "incidentType": "LAWSUIT",
                  "harmType": "MEDICATION_ERROR",
                  "title": "Vague lawsuit mention",
                  "defendant": "SomeAI",
                  "plaintiff": "Unknown",
                  "legalTheory": null,
                  "court": null,
                  "caseNumber": null,
                  "harmDate": null,
                  "filingDate": null
                }
                """);

        Optional<LiabilityIncident> result = adapter.classify(
                buildArticle("lt1-002", "Opinion: AI might face lawsuits", "Commentary only."));

        assertThat(result).isEmpty();
    }

    @Test
    void classify_notAnIncident_returnsEmpty() {
        when(callSpec.content()).thenReturn("""
                {
                  "isIncident": false,
                  "confidence": "HIGH",
                  "incidentType": null,
                  "harmType": null,
                  "title": null,
                  "defendant": null,
                  "plaintiff": null
                }
                """);

        Optional<LiabilityIncident> result = adapter.classify(
                buildArticle("lt1-003", "Academic paper on AI safety", "No legal claims."));

        assertThat(result).isEmpty();
    }

    @Test
    void classify_malformedJson_returnsEmpty() {
        when(callSpec.content()).thenReturn("Not JSON at all");

        Optional<LiabilityIncident> result = adapter.classify(
                buildArticle("lt1-004", "Some article", "Some body."));

        assertThat(result).isEmpty();
    }

    @Test
    void classify_llmThrowsException_returnsEmpty() {
        when(callSpec.content()).thenThrow(new RuntimeException("API timeout"));

        Optional<LiabilityIncident> result = adapter.classify(
                buildArticle("lt1-005", "Article", "Body."));

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------

    private NewsArticle buildArticle(String id, String title, String body) {
        return new NewsArticle(
                id, title, URI.create("https://example.com/" + id),
                body, "AI Healthcare Legal",
                null, null, null, "LEGAL", 0.9, null
        );
    }
}
