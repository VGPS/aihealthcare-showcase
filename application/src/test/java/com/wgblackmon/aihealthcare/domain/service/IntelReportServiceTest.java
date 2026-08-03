package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.IntelReport;
import com.wgblackmon.aihealthcare.domain.model.ResearchAnswer;
import com.wgblackmon.aihealthcare.domain.model.ResearchRequest;
import com.wgblackmon.aihealthcare.domain.model.SourceCitation;
import com.wgblackmon.aihealthcare.domain.port.inbound.ConductResearchUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiReportPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.IntelReportPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IntelReportService} with mocked ports.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
class IntelReportServiceTest {

    private ConductResearchUseCase researchUseCase;
    private AiReportPort aiReportPort;
    private IntelReportPort intelReportPort;
    private IntelReportService service;

    private static final String TEMPLATE = "Subject: {query}\nSources ({sourceCount}):\n{sources}";

    @BeforeEach
    void setUp() {
        researchUseCase = mock(ConductResearchUseCase.class);
        aiReportPort = mock(AiReportPort.class);
        intelReportPort = mock(IntelReportPort.class);
        service = new IntelReportService(researchUseCase, aiReportPort, intelReportPort, TEMPLATE);
    }

    @Test
    void generate_callsResearchPipelineAndAiPort() {
        List<SourceCitation> citations = List.of(
                new SourceCitation(1, "AI in Radiology", "https://example.com/1", Instant.now()),
                new SourceCitation(2, "Healthcare ML", "https://example.com/2", Instant.now()));

        ResearchAnswer answer = new ResearchAnswer(
                "ans-001", "Anthropic healthcare", Collections.emptyList(), citations, Instant.now());

        when(researchUseCase.conduct(any(ResearchRequest.class))).thenReturn(answer);
        when(aiReportPort.generate(anyString())).thenReturn("<h2>Executive Summary</h2><p>Analysis</p>");

        IntelReport report = service.generate("Anthropic healthcare", "user@test.com");

        assertThat(report.query()).isEqualTo("Anthropic healthcare");
        assertThat(report.sourceCount()).isEqualTo(2);
        assertThat(report.userEmail()).isEqualTo("user@test.com");
        assertThat(report.htmlContent()).contains("Executive Summary");
        assertThat(report.reportId()).isNotBlank();

        verify(researchUseCase).conduct(any(ResearchRequest.class));
        verify(aiReportPort).generate(anyString());
        verify(intelReportPort).save(any(IntelReport.class));
    }

    @Test
    void generate_promptContainsQueryAndSources() {
        List<SourceCitation> citations = List.of(
                new SourceCitation(1, "AI Article", "https://example.com/ai", Instant.now()));

        ResearchAnswer answer = new ResearchAnswer(
                "ans-002", "radiology AI", Collections.emptyList(), citations, Instant.now());

        when(researchUseCase.conduct(any(ResearchRequest.class))).thenReturn(answer);
        when(aiReportPort.generate(anyString())).thenReturn("<p>Report content</p>");

        service.generate("radiology AI", "user@test.com");

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiReportPort).generate(promptCaptor.capture());

        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("radiology AI");
        assertThat(prompt).contains("AI Article");
        assertThat(prompt).contains("https://example.com/ai");
    }

    @Test
    void generate_persistsReport() {
        ResearchAnswer answer = new ResearchAnswer(
                "ans-003", "Tempus", Collections.emptyList(),
                Collections.emptyList(), Instant.now());

        when(researchUseCase.conduct(any(ResearchRequest.class))).thenReturn(answer);
        when(aiReportPort.generate(anyString())).thenReturn("<p>Tempus report</p>");

        service.generate("Tempus", "admin@test.com");

        ArgumentCaptor<IntelReport> captor = ArgumentCaptor.forClass(IntelReport.class);
        verify(intelReportPort).save(captor.capture());

        IntelReport saved = captor.getValue();
        assertThat(saved.query()).isEqualTo("Tempus");
        assertThat(saved.userEmail()).isEqualTo("admin@test.com");
    }

    @Test
    void findById_delegatesToPort() {
        IntelReport report = new IntelReport(
                "rpt-001", "query", "<p>content</p>", 5, "user@test.com", Instant.now(), List.of());
        when(intelReportPort.findById("rpt-001")).thenReturn(Optional.of(report));

        Optional<IntelReport> result = service.findById("rpt-001");

        assertThat(result).isPresent();
        assertThat(result.get().reportId()).isEqualTo("rpt-001");
        verify(intelReportPort).findById("rpt-001");
    }

    @Test
    void findById_returnsEmptyWhenNotFound() {
        when(intelReportPort.findById("missing")).thenReturn(Optional.empty());

        Optional<IntelReport> result = service.findById("missing");

        assertThat(result).isEmpty();
    }

    @Test
    void findAll_delegatesToPort() {
        IntelReport r1 = new IntelReport("rpt-1", "q1", "<p>c1</p>", 3, "u@t.com", Instant.now(), List.of());
        IntelReport r2 = new IntelReport("rpt-2", "q2", "<p>c2</p>", 7, "u@t.com", Instant.now(), List.of());
        when(intelReportPort.findAll()).thenReturn(List.of(r1, r2));

        List<IntelReport> result = service.findAll();

        assertThat(result).hasSize(2);
        verify(intelReportPort).findAll();
    }

    @Test
    void generate_stripsMarkdownFencesFromAiResponse() {
        ResearchAnswer answer = new ResearchAnswer(
                "ans-004", "fenced", Collections.emptyList(),
                Collections.emptyList(), Instant.now());

        when(researchUseCase.conduct(any(ResearchRequest.class))).thenReturn(answer);
        when(aiReportPort.generate(anyString())).thenReturn("```html\n<h2>Summary</h2>\n```");

        IntelReport report = service.generate("fenced", "user@test.com");

        assertThat(report.htmlContent()).isEqualTo("<h2>Summary</h2>");
        assertThat(report.htmlContent()).doesNotContain("```");
    }
}
