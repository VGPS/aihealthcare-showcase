package com.wgblackmon.aihealthcare.infrastructure.ingestion.legal;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryBody;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEventType;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleStoragePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.RegulatoryEventPort;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.regulatory.RegulatoryHarvestProperties;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.regulatory.RegulatorySourceHarvester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LegalBackfillService}.
 *
 * <p>Verifies orchestration logic: all sources are invoked, failures
 * are isolated, articles and regulatory events are saved via the
 * correct ports, and result counts are accurate.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-07-30
 */
class LegalBackfillServiceTest {

    private CourtListenerHarvester courtListenerHarvester;
    private PubMedLegalHarvester pubMedLegalHarvester;
    private ArticleStoragePort articleStoragePort;
    private RegulatoryEventPort regulatoryEventPort;
    private RegulatoryHarvestProperties regulatoryProperties;
    private RegulatorySourceHarvester mockRegHarvester;
    private LegalBackfillService service;

    @BeforeEach
    void setUp() {
        courtListenerHarvester = mock(CourtListenerHarvester.class);
        pubMedLegalHarvester = mock(PubMedLegalHarvester.class);
        articleStoragePort = mock(ArticleStoragePort.class);
        regulatoryEventPort = mock(RegulatoryEventPort.class);
        regulatoryProperties = new RegulatoryHarvestProperties();
        mockRegHarvester = mock(RegulatorySourceHarvester.class);
        when(mockRegHarvester.sourceName()).thenReturn("TestRegSource");

        service = new LegalBackfillService(
                courtListenerHarvester, pubMedLegalHarvester,
                articleStoragePort, regulatoryEventPort,
                regulatoryProperties, List.of(mockRegHarvester));
    }

    @Test
    void runBackfill_callsAllSources() {
        when(courtListenerHarvester.harvest(365)).thenReturn(List.of());
        when(pubMedLegalHarvester.harvest(365)).thenReturn(List.of());
        when(mockRegHarvester.harvest(eq(365), anyList())).thenReturn(List.of());

        service.runBackfill(365);

        verify(courtListenerHarvester).harvest(365);
        verify(pubMedLegalHarvester).harvest(365);
        verify(mockRegHarvester).harvest(eq(365), anyList());
    }

    @Test
    void runBackfill_isolatesFailures() {
        when(courtListenerHarvester.harvest(365)).thenThrow(new RuntimeException("network error"));
        when(pubMedLegalHarvester.harvest(365)).thenReturn(List.of(
                makeArticle("pubmed-1", "Test Article")));
        when(mockRegHarvester.harvest(eq(365), anyList())).thenReturn(List.of());

        LegalBackfillService.BackfillResult result = service.runBackfill(365);

        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).contains("CourtListener");
        assertThat(result.pubmedCount()).isEqualTo(1);
        verify(articleStoragePort).save(anyList());
    }

    @Test
    void runBackfill_savesArticlesViaStoragePort() {
        List<NewsArticle> opinions = List.of(makeArticle("cl-1", "Case A"));
        List<NewsArticle> papers = List.of(makeArticle("pm-1", "Paper B"), makeArticle("pm-2", "Paper C"));
        when(courtListenerHarvester.harvest(1095)).thenReturn(opinions);
        when(pubMedLegalHarvester.harvest(1095)).thenReturn(papers);
        when(mockRegHarvester.harvest(eq(1095), anyList())).thenReturn(List.of());

        service.runBackfill(1095);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NewsArticle>> captor = ArgumentCaptor.forClass(List.class);
        verify(articleStoragePort, times(2)).save(captor.capture());
        List<List<NewsArticle>> allSaves = captor.getAllValues();
        assertThat(allSaves.get(0)).hasSize(1);
        assertThat(allSaves.get(1)).hasSize(2);
    }

    @Test
    void runBackfill_savesRegulatoryEventsViaPort() {
        when(courtListenerHarvester.harvest(1095)).thenReturn(List.of());
        when(pubMedLegalHarvester.harvest(1095)).thenReturn(List.of());

        RegulatoryEvent event = new RegulatoryEvent(
                "evt-1", RegulatoryEventType.FDA_510K_CLEARANCE, RegulatoryBody.FDA,
                "Test Device", "Summary", "K241234", "Acme Corp",
                "AI Device", "https://fda.gov/test", null,
                Instant.now(), Instant.now(), List.of("AI"));
        when(mockRegHarvester.harvest(eq(1095), anyList())).thenReturn(List.of(event));
        when(regulatoryEventPort.existsByReferenceNumber("K241234")).thenReturn(false);
        when(regulatoryEventPort.existsBySourceUrl("https://fda.gov/test")).thenReturn(false);

        LegalBackfillService.BackfillResult result = service.runBackfill(1095);

        verify(regulatoryEventPort).saveAll(anyList());
        assertThat(result.regulatoryCount()).isEqualTo(1);
    }

    @Test
    void runBackfill_reportsCorrectCounts() {
        when(courtListenerHarvester.harvest(365)).thenReturn(
                List.of(makeArticle("cl-1", "Case 1"), makeArticle("cl-2", "Case 2")));
        when(pubMedLegalHarvester.harvest(365)).thenReturn(
                List.of(makeArticle("pm-1", "Paper 1")));
        when(mockRegHarvester.harvest(eq(365), anyList())).thenReturn(List.of());

        LegalBackfillService.BackfillResult result = service.runBackfill(365);

        assertThat(result.courtListenerCount()).isEqualTo(2);
        assertThat(result.pubmedCount()).isEqualTo(1);
        assertThat(result.regulatoryCount()).isEqualTo(0);
        assertThat(result.errors()).isEmpty();
    }

    private NewsArticle makeArticle(String id, String title) {
        return new NewsArticle(id, title, URI.create("https://example.com/" + id),
                "body", "AI Healthcare Legal", null, null, "Test", "ACADEMIC", 0.8, Instant.now());
    }
}
