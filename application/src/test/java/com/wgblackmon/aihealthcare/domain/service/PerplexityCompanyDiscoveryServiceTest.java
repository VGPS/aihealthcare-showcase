package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.HealthcareAiCompany;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyResearchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.HealthcareAiCompanyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.PerplexityCitationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PerplexityCompanyDiscoveryService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-02
 * @updated 2026-08-02
 */
class PerplexityCompanyDiscoveryServiceTest {

    private CompanyResearchPort researchPort;
    private HealthcareAiCompanyPort companyPort;
    private PerplexityCitationPort citationPort;
    private PerplexityCompanyDiscoveryService service;

    @BeforeEach
    void setUp() {
        researchPort = mock(CompanyResearchPort.class);
        companyPort = mock(HealthcareAiCompanyPort.class);
        citationPort = mock(PerplexityCitationPort.class);
        service = new PerplexityCompanyDiscoveryService(researchPort, companyPort, citationPort, 30);
    }

    @Test
    void runDiscoveryCycle_apiNotAvailable_returnsZero() {
        when(researchPort.isAvailable()).thenReturn(false);

        int result = service.runDiscoveryCycle();

        assertThat(result).isZero();
        verify(researchPort, never()).discoverCompanies(anyString());
    }

    @Test
    void runDiscoveryCycle_discoversAndPersistsNewCompanies() {
        when(researchPort.isAvailable()).thenReturn(true);
        when(researchPort.discoverCompanies(anyString())).thenReturn(
                new CompanyResearchPort.DiscoveryResult(
                        List.of("Tempus AI", "Viz.ai"), List.of("https://src.com"), "raw content"));
        when(companyPort.existsByNameOrDomain(anyString(), any())).thenReturn(false);
        when(researchPort.extractCompanyFields("Tempus AI")).thenReturn(
                new CompanyResearchPort.ExtractionResult(
                        Map.of("name", "Tempus AI", "description", "Precision medicine",
                                "domain", "tempus.com", "sector", "Healthcare AI",
                                "subSector", "diagnostics"), List.of("https://tempus.com")));
        when(researchPort.extractCompanyFields("Viz.ai")).thenReturn(
                new CompanyResearchPort.ExtractionResult(
                        Map.of("name", "Viz.ai", "description", "Medical imaging AI",
                                "domain", "viz.ai", "sector", "Healthcare AI"),
                        List.of("https://viz.ai")));
        when(researchPort.crossValidate(anyString())).thenReturn(
                new CompanyResearchPort.ValidationResult(true, List.of("https://cb.com"), "YES found on list"));

        int result = service.runDiscoveryCycle();

        assertThat(result).isEqualTo(2);
        verify(companyPort, times(2)).save(any(HealthcareAiCompany.class));
    }

    @Test
    void runDiscoveryCycle_skipsExistingCompanies() {
        when(researchPort.isAvailable()).thenReturn(true);
        when(researchPort.discoverCompanies(anyString())).thenReturn(
                new CompanyResearchPort.DiscoveryResult(
                        List.of("ExistingCo"), List.of(), "content"));
        when(companyPort.existsByNameOrDomain("existingco", null)).thenReturn(true);

        int result = service.runDiscoveryCycle();

        assertThat(result).isZero();
        verify(researchPort, never()).extractCompanyFields(anyString());
    }

    @Test
    void runDiscoveryCycle_limitsMaxCompaniesPerRun() {
        service = new PerplexityCompanyDiscoveryService(researchPort, companyPort, citationPort, 1);
        when(researchPort.isAvailable()).thenReturn(true);
        when(researchPort.discoverCompanies(anyString())).thenReturn(
                new CompanyResearchPort.DiscoveryResult(
                        List.of("Company A", "Company B", "Company C"), List.of(), "content"));
        when(companyPort.existsByNameOrDomain(anyString(), any())).thenReturn(false);
        when(researchPort.extractCompanyFields(anyString())).thenReturn(
                new CompanyResearchPort.ExtractionResult(
                        Map.of("name", "Company A", "description", "desc", "sector", "AI"),
                        List.of()));
        when(researchPort.crossValidate(anyString())).thenReturn(
                new CompanyResearchPort.ValidationResult(false, List.of(), "NO"));

        service.runDiscoveryCycle();

        // Only 1 extraction call (max per run = 1)
        verify(researchPort, times(1)).extractCompanyFields(anyString());
    }

    @Test
    void runDiscoveryCycle_logsCitationsForEachPhase() {
        when(researchPort.isAvailable()).thenReturn(true);
        when(researchPort.discoverCompanies(anyString())).thenReturn(
                new CompanyResearchPort.DiscoveryResult(
                        List.of("NewCo"), List.of("https://src1.com", "https://src2.com"), "content"));
        when(companyPort.existsByNameOrDomain(anyString(), any())).thenReturn(false);
        when(researchPort.extractCompanyFields("NewCo")).thenReturn(
                new CompanyResearchPort.ExtractionResult(
                        Map.of("name", "NewCo", "description", "desc", "sector", "AI"),
                        List.of("https://ext.com")));
        when(researchPort.crossValidate("NewCo")).thenReturn(
                new CompanyResearchPort.ValidationResult(true, List.of("https://val.com"), "YES"));

        service.runDiscoveryCycle();

        // 3 saveAll calls: discovery citations, extraction citations, validation citations
        verify(citationPort, times(3)).saveAll(any());
    }

    @Test
    void normalizeName_stripsIncSuffix() {
        assertThat(service.normalizeName("Tempus AI, Inc.")).isEqualTo("tempus ai");
    }

    @Test
    void normalizeName_stripsLlcSuffix() {
        assertThat(service.normalizeName("HealthTech LLC")).isEqualTo("healthtech");
    }

    @Test
    void normalizeName_handlesBlankInput() {
        assertThat(service.normalizeName("")).isEmpty();
        assertThat(service.normalizeName(null)).isEmpty();
    }

    @Test
    void processCompany_returnsNullForEmptyExtraction() {
        when(researchPort.extractCompanyFields("Unknown Co")).thenReturn(
                new CompanyResearchPort.ExtractionResult(Map.of(), List.of()));
        when(researchPort.crossValidate("Unknown Co")).thenReturn(
                new CompanyResearchPort.ValidationResult(false, List.of(), "NO"));

        HealthcareAiCompany result = service.processCompany("Unknown Co");

        assertThat(result).isNull();
    }
}
