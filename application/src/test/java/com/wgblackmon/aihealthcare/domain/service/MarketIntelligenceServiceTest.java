package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.MarketIntelligenceReport;
import com.wgblackmon.aihealthcare.domain.model.SearchPromptConfig;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiReportPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SearchPromptPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MarketIntelligenceService}.
 *
 * <p>All outbound ports are mocked — no AI calls, no database access.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-02
 * @updated 2026-05-02
 */
@ExtendWith(MockitoExtension.class)
class MarketIntelligenceServiceTest {

    @Mock
    private SearchPromptPort searchPromptPort;

    @Mock
    private AiReportPort aiReportPort;

    private MarketIntelligenceService service;

    @BeforeEach
    void setUp() {
        service = new MarketIntelligenceService(searchPromptPort, aiReportPort);
    }

    // -------------------------------------------------------------------------
    // generate() — happy path
    // -------------------------------------------------------------------------

    @Test
    void generate_promptFound_returnsReportWithHtmlContent() {
        SearchPromptConfig config = new SearchPromptConfig(
                "MARKET_INTELLIGENCE", "Healthcare AI Market Intelligence",
                "Analyze healthcare AI frameworks...", "Monthly report", true);
        when(searchPromptPort.findByEngine("MARKET_INTELLIGENCE")).thenReturn(Optional.of(config));
        when(aiReportPort.generate("Analyze healthcare AI frameworks..."))
                .thenReturn("<html><body>Healthcare AI Report</body></html>");

        MarketIntelligenceReport report = service.generate();

        assertThat(report).isNotNull();
        assertThat(report.htmlContent()).contains("Healthcare AI Report");
        assertThat(report.promptEngine()).isEqualTo("MARKET_INTELLIGENCE");
        assertThat(report.reportDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void generate_callsAiPortWithExactPromptText() {
        String promptText = "My custom market intelligence prompt";
        SearchPromptConfig config = new SearchPromptConfig(
                "MARKET_INTELLIGENCE", "Test Prompt", promptText, null, true);
        when(searchPromptPort.findByEngine("MARKET_INTELLIGENCE")).thenReturn(Optional.of(config));
        when(aiReportPort.generate(promptText)).thenReturn("<html>result</html>");

        service.generate();

        verify(aiReportPort).generate(promptText);
    }

    @Test
    void generate_promptFound_reportDateIsToday() {
        SearchPromptConfig config = new SearchPromptConfig(
                "MARKET_INTELLIGENCE", "Test", "prompt text", null, true);
        when(searchPromptPort.findByEngine("MARKET_INTELLIGENCE")).thenReturn(Optional.of(config));
        when(aiReportPort.generate("prompt text")).thenReturn("<html>content</html>");

        MarketIntelligenceReport report = service.generate();

        assertThat(report.reportDate()).isEqualTo(LocalDate.now());
    }

    // -------------------------------------------------------------------------
    // generate() — prompt not configured
    // -------------------------------------------------------------------------

    @Test
    void generate_promptNotFound_throwsIllegalStateException() {
        when(searchPromptPort.findByEngine("MARKET_INTELLIGENCE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MARKET_INTELLIGENCE");
    }

    @Test
    void generate_promptNotFound_exceptionMessageMentionsRestEndpoint() {
        when(searchPromptPort.findByEngine("MARKET_INTELLIGENCE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/api/v1/search-prompts/MARKET_INTELLIGENCE");
    }

    // -------------------------------------------------------------------------
    // MarketIntelligenceReport record validation
    // -------------------------------------------------------------------------

    @Test
    void report_nullDate_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new MarketIntelligenceReport(null, "<html/>", "MARKET_INTELLIGENCE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reportDate");
    }

    @Test
    void report_blankHtml_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new MarketIntelligenceReport(LocalDate.now(), "  ", "MARKET_INTELLIGENCE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("htmlContent");
    }

    @Test
    void report_blankEngine_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new MarketIntelligenceReport(LocalDate.now(), "<html/>", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("promptEngine");
    }
}
