package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.Company;
import com.wgblackmon.aihealthcare.domain.model.CompanyDiscoveryResult;
import com.wgblackmon.aihealthcare.domain.model.CompanyTags;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleStoragePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyScrapingPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CompanyDiscoveryService} pipeline orchestration.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-07
 * @updated 2026-06-07
 */
@ExtendWith(MockitoExtension.class)
class CompanyDiscoveryServiceTest {

    @Mock
    private CompanyScrapingPort scrapingPort;

    @Mock
    private ArticleStoragePort storagePort;

    @Captor
    private ArgumentCaptor<List<NewsArticle>> articlesCaptor;

    private CompanyDiscoveryService service;

    @BeforeEach
    void setUp() {
        service = new CompanyDiscoveryService(
                scrapingPort, storagePort,
                new CompanyClassifier(),
                new CompanyDeduplicator(),
                new CompanyNewsletterRenderer()
        );
    }

    @Test
    void discover_fullPipeline_classifiesAndPersists() {
        when(scrapingPort.scrapeAll()).thenReturn(List.of(
                makeCompany("ScribeBot", "AI scribe for clinical documentation in hospitals"),
                makeCompany("WidgetCo", "Makes industrial widgets")
        ));

        CompanyDiscoveryResult result = service.discover();

        assertThat(result.totalScraped()).isEqualTo(2);
        // Only ScribeBot matches AI + Health
        assertThat(result.aiHealthFiltered()).isEqualTo(1);
        assertThat(result.companies()).hasSize(1);
        assertThat(result.companies().get(0).name()).isEqualTo("ScribeBot");

        verify(storagePort).save(articlesCaptor.capture());
        assertThat(articlesCaptor.getValue()).hasSize(1);
        assertThat(articlesCaptor.getValue().get(0).topic()).isEqualTo("New AI Healthcare Companies");
    }

    @Test
    void discover_deduplicatesByName() {
        when(scrapingPort.scrapeAll()).thenReturn(List.of(
                makeCompany("HealthAI", "AI copilot for healthcare providers"),
                makeCompany("HealthAI", "AI copilot for healthcare providers — expanded description with more detail")
        ));

        CompanyDiscoveryResult result = service.discover();

        assertThat(result.totalScraped()).isEqualTo(2);
        assertThat(result.afterDedup()).isEqualTo(1);
    }

    @Test
    void discover_emptyScrapingResult_returnsEmpty() {
        when(scrapingPort.scrapeAll()).thenReturn(List.of());

        CompanyDiscoveryResult result = service.discover();

        assertThat(result.totalScraped()).isEqualTo(0);
        assertThat(result.companies()).isEmpty();
        assertThat(result.markdown()).contains("No AI healthcare companies discovered");

        verify(storagePort).save(List.of());
    }

    @Test
    void discover_markdownGenerated() {
        when(scrapingPort.scrapeAll()).thenReturn(List.of(
                makeCompany("ScribeBot", "AI scribe for clinical documentation in hospitals")
        ));

        CompanyDiscoveryResult result = service.discover();

        assertThat(result.markdown()).contains("# New AI Healthcare Companies");
        assertThat(result.markdown()).contains("**ScribeBot**");
    }

    @Test
    void discover_articleIdNormalized() {
        when(scrapingPort.scrapeAll()).thenReturn(List.of(
                makeCompany("Health AI Corp", "AI agent for patient care and telehealth")
        ));

        service.discover();

        verify(storagePort).save(articlesCaptor.capture());
        String articleId = articlesCaptor.getValue().get(0).articleId();
        assertThat(articleId).startsWith("startup-");
        assertThat(articleId).doesNotContain(" ");
    }

    private Company makeCompany(String name, String description) {
        return new Company(name, "YC Health Tech", null, null, description,
                CompanyTags.none(), false, false);
    }
}
