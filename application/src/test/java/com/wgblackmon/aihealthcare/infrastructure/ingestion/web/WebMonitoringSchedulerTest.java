package com.wgblackmon.aihealthcare.infrastructure.ingestion.web;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleStoragePort;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.huggingface.HuggingFaceHarvester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WebMonitoringScheduler}.
 *
 * <p>Verifies that the scheduler delegates to the harvester and storage
 * port correctly, and handles empty results gracefully.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-19
 * @updated 2026-04-19
 */
@ExtendWith(MockitoExtension.class)
class WebMonitoringSchedulerTest {

    @Mock
    private WebPageHarvester webPageHarvester;

    @Mock
    private HuggingFaceHarvester huggingFaceHarvester;

    @Mock
    private ArticleStoragePort articleStoragePort;

    private WebMonitoringScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new WebMonitoringScheduler(webPageHarvester, huggingFaceHarvester,
                                               articleStoragePort);
    }

    @Test
    void harvestCompetitorPages_withChanges_savesArticles() {
        NewsArticle article = new NewsArticle(
                "test-id", "Test Article", URI.create("https://example.com/page#snapshot-123"),
                "body text", "Test Competitor", "Test Competitor",
                1L, "Test Competitor", "COMPETITOR", 0.7, Instant.now());
        when(webPageHarvester.harvestChangedPages()).thenReturn(List.of(article));

        scheduler.harvestCompetitorPages();

        verify(articleStoragePort).save(List.of(article));
    }

    @Test
    void harvestCompetitorPages_noChanges_doesNotCallStorage() {
        when(webPageHarvester.harvestChangedPages()).thenReturn(List.of());

        scheduler.harvestCompetitorPages();

        verify(articleStoragePort, never()).save(anyList());
    }

    @Test
    void harvestCompetitorPages_harvesterThrows_doesNotPropagate() {
        when(webPageHarvester.harvestChangedPages()).thenThrow(new RuntimeException("network error"));

        // Should not throw — exception is caught and logged
        scheduler.harvestCompetitorPages();

        verify(articleStoragePort, never()).save(anyList());
    }

    @Test
    void harvestHuggingFaceModels_withModels_savesArticles() {
        NewsArticle model = new NewsArticle(
                "hf-microsoft/BioGPT", "microsoft/BioGPT",
                URI.create("https://huggingface.co/microsoft/BioGPT"),
                "Pipeline: text-generation\nDownloads: 50000",
                "HuggingFace Healthcare LLMs", "microsoft",
                1L, "HuggingFace Healthcare LLMs", "HUGGINGFACE", 0.5, Instant.now());
        when(huggingFaceHarvester.harvestModels()).thenReturn(List.of(model));

        scheduler.harvestHuggingFaceModels();

        verify(articleStoragePort).save(List.of(model));
    }

    @Test
    void harvestHuggingFaceModels_noModels_doesNotCallStorage() {
        when(huggingFaceHarvester.harvestModels()).thenReturn(List.of());

        scheduler.harvestHuggingFaceModels();

        verify(articleStoragePort, never()).save(anyList());
    }

    @Test
    void harvestHuggingFaceModels_harvesterThrows_doesNotPropagate() {
        when(huggingFaceHarvester.harvestModels()).thenThrow(new RuntimeException("API timeout"));

        scheduler.harvestHuggingFaceModels();

        verify(articleStoragePort, never()).save(anyList());
    }
}
