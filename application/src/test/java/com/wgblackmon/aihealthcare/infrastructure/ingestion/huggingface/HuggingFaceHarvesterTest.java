package com.wgblackmon.aihealthcare.infrastructure.ingestion.huggingface;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.feed.FeedSourceConfig;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.feed.FeedSourceConfig.FeedTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HuggingFaceHarvester}.
 *
 * <p>Mocks the {@link HttpClient} to avoid real API calls and verifies
 * correct mapping from HuggingFace JSON response to {@link NewsArticle} records.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-19
 * @updated 2026-04-19
 */
@ExtendWith(MockitoExtension.class)
class HuggingFaceHarvesterTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private HuggingFaceHarvester harvester;

    private static final FeedSourceConfig HF_SOURCE = new FeedSourceConfig(
            1L, "HuggingFace Healthcare LLMs",
            "https://huggingface.co/api/models?search=healthcare",
            FeedTier.HUGGINGFACE, 0.5, 25);

    private static final String SAMPLE_RESPONSE = """
            [
              {
                "modelId": "microsoft/BioGPT",
                "author": "microsoft",
                "downloads": 50000,
                "pipeline_tag": "text-generation",
                "tags": ["medical", "biomedical", "healthcare"],
                "lastModified": "2025-01-15T10:00:00.000Z"
              },
              {
                "modelId": "medicalai/ClinicalBERT",
                "author": "medicalai",
                "downloads": 30000,
                "pipeline_tag": "fill-mask",
                "tags": ["clinical", "ehr", "healthcare"],
                "lastModified": "2025-02-20T14:30:00.000Z"
              }
            ]
            """;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        harvester = new HuggingFaceHarvester(List.of(HF_SOURCE), objectMapper, httpClient);
    }

    @SuppressWarnings("unchecked")
    @Test
    void harvestModels_successfulResponse_returnsArticles() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(SAMPLE_RESPONSE);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        List<NewsArticle> result = harvester.harvestModels();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).articleId()).isEqualTo("hf-microsoft/BioGPT");
        assertThat(result.get(0).title()).isEqualTo("microsoft/BioGPT");
        assertThat(result.get(0).url().toString()).isEqualTo("https://huggingface.co/microsoft/BioGPT");
        assertThat(result.get(0).sourceTier()).isEqualTo("HUGGINGFACE");
        assertThat(result.get(0).bodyText()).contains("text-generation");
        assertThat(result.get(0).bodyText()).contains("50000");
        assertThat(result.get(0).bodyText()).contains("medical");

        assertThat(result.get(1).articleId()).isEqualTo("hf-medicalai/ClinicalBERT");
        assertThat(result.get(1).title()).isEqualTo("medicalai/ClinicalBERT");
    }

    @SuppressWarnings("unchecked")
    @Test
    void harvestModels_apiReturnsError_returnsEmpty() throws Exception {
        when(httpResponse.statusCode()).thenReturn(500);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        List<NewsArticle> result = harvester.harvestModels();

        assertThat(result).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void harvestModels_emptyResponse_returnsEmpty() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("[]");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        List<NewsArticle> result = harvester.harvestModels();

        assertThat(result).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void harvestModels_connectionFails_returnsEmpty() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        List<NewsArticle> result = harvester.harvestModels();

        assertThat(result).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void harvestModels_respectsMaxItems() throws Exception {
        FeedSourceConfig limitedSource = new FeedSourceConfig(
                1L, "HF Limited", "https://huggingface.co/api/models",
                FeedTier.HUGGINGFACE, 0.5, 1);
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        HuggingFaceHarvester limitedHarvester = new HuggingFaceHarvester(
                List.of(limitedSource), objectMapper, httpClient);

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(SAMPLE_RESPONSE);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        List<NewsArticle> result = limitedHarvester.harvestModels();

        assertThat(result).hasSize(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    void harvestModels_authorPopulatedCorrectly() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(SAMPLE_RESPONSE);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        List<NewsArticle> result = harvester.harvestModels();

        assertThat(result.get(0).author()).isEqualTo("microsoft");
        assertThat(result.get(1).author()).isEqualTo("medicalai");
    }
}
