package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.CompanyProfile;
import com.wgblackmon.aihealthcare.domain.model.CompanySentiment;
import com.wgblackmon.aihealthcare.domain.model.DealContext;
import com.wgblackmon.aihealthcare.domain.model.DealSignal;
import com.wgblackmon.aihealthcare.domain.model.DealSignalType;
import com.wgblackmon.aihealthcare.domain.model.FrameworkAnalysis;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.SentimentLabel;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyProfilePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanySentimentPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.FrameworkAnalysisPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.RegulatoryEventPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DealEnrichmentService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-06
 * @updated 2026-08-06
 */
@ExtendWith(MockitoExtension.class)
class DealEnrichmentServiceTest {

    @Mock
    private CompanySentimentPort sentimentPort;

    @Mock
    private FrameworkAnalysisPort frameworkPort;

    @Mock
    private RegulatoryEventPort regulatoryPort;

    @Mock
    private CompanyProfilePort profilePort;

    private DealSignal testSignal(String companyName) {
        return new DealSignal("s1", "a1", "Test Deal",
                DealSignalType.FUNDING, companyName, "Summary", 0.85, Instant.now(),
                "$50M", "VC Firm", "https://example.com", null);
    }

    @Test
    void enrich_allPortsNull_returnsBasicContext() {
        DealEnrichmentService service = new DealEnrichmentService(null, null, null, null);
        DealSignal signal = testSignal("Tempus AI");

        DealContext result = service.enrich(signal);

        assertThat(result.signal()).isEqualTo(signal);
        assertThat(result.sentiment()).isNull();
        assertThat(result.framework()).isNull();
        assertThat(result.regulatoryEvents()).isEmpty();
        assertThat(result.companyProfile()).isNull();
    }

    @Test
    void enrich_withSentiment_returnsSentiment() {
        CompanySentiment sentiment = new CompanySentiment(
                "tempus-ai", "Tempus AI", SentimentLabel.POSITIVE, 0.82,
                5, 3, 1, 1, 0, "Low risk", List.of(), Instant.now());
        when(sentimentPort.findBySlug("tempus-ai")).thenReturn(Optional.of(sentiment));
        when(regulatoryPort.findByApplicant(anyString(), anyInt())).thenReturn(List.of());
        when(profilePort.findAll()).thenReturn(List.of());

        DealEnrichmentService service = new DealEnrichmentService(
                sentimentPort, null, regulatoryPort, profilePort);
        DealContext result = service.enrich(testSignal("Tempus AI"));

        assertThat(result.sentiment()).isNotNull();
        assertThat(result.sentiment().companySlug()).isEqualTo("tempus-ai");
    }

    @Test
    void enrich_withFramework_returnsFramework() {
        FrameworkAnalysis framework = new FrameworkAnalysis(
                "tempus-ai", "Tempus AI", "Strong AI diagnostics platform",
                List.of(), List.of(), List.of(), List.of(), 8, 10, Instant.now());
        when(frameworkPort.findBySlug("tempus-ai")).thenReturn(Optional.of(framework));
        when(regulatoryPort.findByApplicant(anyString(), anyInt())).thenReturn(List.of());
        when(profilePort.findAll()).thenReturn(List.of());

        DealEnrichmentService service = new DealEnrichmentService(
                null, frameworkPort, regulatoryPort, profilePort);
        DealContext result = service.enrich(testSignal("Tempus AI"));

        assertThat(result.framework()).isNotNull();
        assertThat(result.framework().companySlug()).isEqualTo("tempus-ai");
    }

    @Test
    void enrich_withRegulatoryByApplicant_returnsEvents() {
        RegulatoryEvent event = createRegulatoryEvent("Tempus AI");
        when(regulatoryPort.findByApplicant("Tempus AI", 10)).thenReturn(List.of(event));
        when(profilePort.findAll()).thenReturn(List.of());

        DealEnrichmentService service = new DealEnrichmentService(
                null, null, regulatoryPort, profilePort);
        DealContext result = service.enrich(testSignal("Tempus AI"));

        assertThat(result.regulatoryEvents()).hasSize(1);
    }

    @Test
    void enrich_withRegulatoryByKeyword_fallsBackWhenApplicantEmpty() {
        RegulatoryEvent event = createRegulatoryEvent("Tempus AI");
        when(regulatoryPort.findByApplicant("Tempus AI", 10)).thenReturn(List.of());
        when(regulatoryPort.findByKeyword("Tempus AI", 10)).thenReturn(List.of(event));
        when(profilePort.findAll()).thenReturn(List.of());

        DealEnrichmentService service = new DealEnrichmentService(
                null, null, regulatoryPort, profilePort);
        DealContext result = service.enrich(testSignal("Tempus AI"));

        assertThat(result.regulatoryEvents()).hasSize(1);
    }

    @Test
    void enrich_noMatchesFound_returnsEmptyContext() {
        when(sentimentPort.findBySlug(anyString())).thenReturn(Optional.empty());
        when(frameworkPort.findBySlug(anyString())).thenReturn(Optional.empty());
        when(regulatoryPort.findByApplicant(anyString(), anyInt())).thenReturn(List.of());
        when(regulatoryPort.findByKeyword(anyString(), anyInt())).thenReturn(List.of());
        when(profilePort.findAll()).thenReturn(List.of());

        DealEnrichmentService service = new DealEnrichmentService(
                sentimentPort, frameworkPort, regulatoryPort, profilePort);
        DealContext result = service.enrich(testSignal("Unknown Corp"));

        assertThat(result.signal()).isNotNull();
        assertThat(result.sentiment()).isNull();
        assertThat(result.framework()).isNull();
        assertThat(result.regulatoryEvents()).isEmpty();
        assertThat(result.companyProfile()).isNull();
    }

    @Test
    void enrich_blankCompanyName_handlesGracefully() {
        DealEnrichmentService service = new DealEnrichmentService(null, null, null, null);
        DealSignal signal = new DealSignal("s1", "a1", "Deal",
                DealSignalType.FUNDING, "", "Sum", 0.5, Instant.now(),
                null, null, null, null);

        DealContext result = service.enrich(signal);

        assertThat(result.signal()).isEqualTo(signal);
        assertThat(result.regulatoryEvents()).isEmpty();
    }

    @Test
    void enrich_profileMatchByDerivedSlug_returnsProfile() {
        Instant now = Instant.now();
        CompanyProfile profile = new CompanyProfile(
                "tempus-ai-health", "Tempus AI", "https://tempus.com",
                "AI diagnostics", List.of("diagnostics"), List.of("a1"),
                now, now, 5,
                com.wgblackmon.aihealthcare.domain.model.TrendDirection.RISING);
        when(regulatoryPort.findByApplicant(anyString(), anyInt())).thenReturn(List.of());
        when(profilePort.findAll()).thenReturn(List.of(profile));

        DealEnrichmentService service = new DealEnrichmentService(
                null, null, regulatoryPort, profilePort);
        DealContext result = service.enrich(testSignal("Tempus AI"));

        assertThat(result.companyProfile()).isNotNull();
        assertThat(result.companyProfile().name()).isEqualTo("Tempus AI");
    }

    private RegulatoryEvent createRegulatoryEvent(String applicantName) {
        Instant now = Instant.now();
        return new RegulatoryEvent(
                "evt1",
                com.wgblackmon.aihealthcare.domain.model.RegulatoryEventType.FDA_510K_CLEARANCE,
                com.wgblackmon.aihealthcare.domain.model.RegulatoryBody.FDA,
                "Event Title",
                "Description",
                "REF-001",
                applicantName,
                "Device",
                "https://fda.gov",
                null,
                now,
                now,
                List.of("ai", "healthcare"),
                null,
                null,
                null,
                null
        );
    }
}
