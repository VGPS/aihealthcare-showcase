package com.wgblackmon.aihealthcare.web.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.ProduceMarketDigestUseCase;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryBody;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEventType;
import com.wgblackmon.aihealthcare.domain.model.StateCode;
import com.wgblackmon.aihealthcare.domain.model.TrendSignal;
import com.wgblackmon.aihealthcare.domain.model.TrendDirection;
import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.model.WikiPage;
import com.wgblackmon.aihealthcare.domain.model.WikiPageType;
import com.wgblackmon.aihealthcare.domain.port.inbound.BrowseCompaniesUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectTrendsUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageStateLawsUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.MonitorRegulatoryEventsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.WikiQueryPort;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the six AIHealthcare MCP tool handlers.
 *
 * Each test verifies that the handler delegates to the correct use-case method
 * and returns a non-error {@link McpSchema.CallToolResult}.  Domain objects are
 * either mocked or constructed with the minimal set of required fields.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-01
 * @updated 2026-11-01
 */
@ExtendWith(MockitoExtension.class)
class AiHealthcareMcpToolHandlersTest {

    @Mock ManageStateLawsUseCase    stateLawsUseCase;
    @Mock BrowseCompaniesUseCase    companiesUseCase;
    @Mock WikiQueryPort             wikiQueryPort;
    @Mock ProduceMarketDigestUseCase marketDigestUseCase;
    @Mock MonitorRegulatoryEventsUseCase regulatoryUseCase;
    @Mock DetectTrendsUseCase       trendsUseCase;

    AiHealthcareMcpToolHandlers handlers;

    @BeforeEach
    void setUp() {
        handlers = new AiHealthcareMcpToolHandlers(
                stateLawsUseCase, companiesUseCase, wikiQueryPort,
                marketDigestUseCase, regulatoryUseCase, trendsUseCase,
                new ObjectMapper());
    }

    // -------------------------------------------------------------------------
    // search_legislation
    // -------------------------------------------------------------------------

    @Test
    void searchLegislation_noArgs_callsGetAll() {
        when(stateLawsUseCase.getAll()).thenReturn(List.of());
        var result = handlers.searchLegislation(req(Map.of()));
        assertSuccess(result);
        verify(stateLawsUseCase).getAll();
    }

    @Test
    void searchLegislation_withQuery_callsSearch() {
        when(stateLawsUseCase.search("Epic MCP")).thenReturn(List.of());
        var result = handlers.searchLegislation(req(Map.of("query", "Epic MCP")));
        assertSuccess(result);
        verify(stateLawsUseCase).search("Epic MCP");
    }

    @Test
    void searchLegislation_withStateCode_callsGetByState() {
        when(stateLawsUseCase.getByState(StateCode.CA)).thenReturn(List.of());
        var result = handlers.searchLegislation(req(Map.of("stateCode", "CA")));
        assertSuccess(result);
        verify(stateLawsUseCase).getByState(StateCode.CA);
    }

    @Test
    void searchLegislation_invalidEnum_returnsError() {
        var result = handlers.searchLegislation(req(Map.of("stateCode", "XX_NOT_VALID")));
        assertError(result);
    }

    // -------------------------------------------------------------------------
    // search_companies
    // -------------------------------------------------------------------------

    @Test
    void searchCompanies_noArgs_returnsAll() {
        when(companiesUseCase.listCompanies()).thenReturn(List.of());
        var result = handlers.searchCompanies(req(Map.of()));
        assertSuccess(result);
        verify(companiesUseCase).listCompanies();
    }

    @Test
    void searchCompanies_withNameQuery_filtersResults() {
        var company = mock(com.wgblackmon.aihealthcare.domain.model.HealthcareAiCompany.class);
        when(company.name()).thenReturn("Tempus AI");
        when(company.domain()).thenReturn("tempus.com");
        when(companiesUseCase.listCompanies()).thenReturn(List.of(company));

        var result = handlers.searchCompanies(req(Map.of("nameQuery", "tempus")));
        assertSuccess(result);
    }

    // -------------------------------------------------------------------------
    // search_wiki
    // -------------------------------------------------------------------------

    @Test
    void searchWiki_returnsPages() {
        WikiPage page = new WikiPage(
                "test-slug", "Test Title", WikiPageType.CONCEPT,
                List.of("ai", "regulation"), "# Content\n\nBody text here",
                List.of(), List.of(), Instant.now(), null, 1);
        when(wikiQueryPort.findRelevantPages(eq("FDA regulation"), anyInt())).thenReturn(List.of(page));

        var result = handlers.searchWiki(req(Map.of("query", "FDA regulation")));
        assertSuccess(result);
        verify(wikiQueryPort).findRelevantPages(eq("FDA regulation"), anyInt());
    }

    @Test
    void searchWiki_emptyQuery_returnsEmptyArray() {
        when(wikiQueryPort.findRelevantPages(eq(""), anyInt())).thenReturn(List.of());
        var result = handlers.searchWiki(req(Map.of("query", "")));
        assertSuccess(result);
    }

    // -------------------------------------------------------------------------
    // get_market_digest
    // -------------------------------------------------------------------------

    @Test
    void getMarketDigest_noDate_returnsLatestOrMessage() {
        when(marketDigestUseCase.findLatest()).thenReturn(Optional.empty());
        var result = handlers.getMarketDigest(req(Map.of()));
        assertSuccess(result);
        verify(marketDigestUseCase).findLatest();
    }

    // -------------------------------------------------------------------------
    // get_regulatory_events
    // -------------------------------------------------------------------------

    @Test
    void getRegulatoryEvents_noArgs_returnsRecentEvents() {
        when(regulatoryUseCase.getRecentEvents(20)).thenReturn(List.of());
        var result = handlers.getRegulatoryEvents(req(Map.of()));
        assertSuccess(result);
        verify(regulatoryUseCase).getRecentEvents(20);
    }

    @Test
    void getRegulatoryEvents_withBody_callsGetByBody() {
        when(regulatoryUseCase.getEventsByBody(RegulatoryBody.FDA, 20)).thenReturn(List.of());
        var result = handlers.getRegulatoryEvents(req(Map.of("regulatoryBody", "FDA")));
        assertSuccess(result);
        verify(regulatoryUseCase).getEventsByBody(RegulatoryBody.FDA, 20);
    }

    // -------------------------------------------------------------------------
    // get_ai_trends
    // -------------------------------------------------------------------------

    @Test
    void getTrends_emptySnapshot_returnsMessage() {
        when(trendsUseCase.getLatestSnapshot()).thenReturn(Optional.empty());
        // getTrends() never reads req.arguments(), so pass a plain mock
        var result = handlers.getTrends(mock(McpSchema.CallToolRequest.class));
        assertSuccess(result);
    }

    @Test
    void getTrends_withSnapshot_returnsKeywords() {
        TrendSignal signal = new TrendSignal(
                "large language model", 45L, 20L, 5L,
                45.0 / 30 / (20.0 / 60), TrendDirection.RISING,
                Instant.now());
        TrendSnapshot snapshot = new TrendSnapshot(
                Instant.now(), 30, List.of(signal), List.of(), List.of(), 120);
        when(trendsUseCase.getLatestSnapshot()).thenReturn(Optional.of(snapshot));

        var result = handlers.getTrends(mock(McpSchema.CallToolRequest.class));
        assertSuccess(result);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static McpSchema.CallToolRequest req(Map<String, Object> args) {
        McpSchema.CallToolRequest r = mock(McpSchema.CallToolRequest.class);
        when(r.arguments()).thenReturn(args);
        return r;
    }

    private static void assertSuccess(McpSchema.CallToolResult result) {
        assertThat(result).isNotNull();
        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
    }

    private static void assertError(McpSchema.CallToolResult result) {
        assertThat(result).isNotNull();
        assertThat(result.isError()).isEqualTo(Boolean.TRUE);
    }
}
