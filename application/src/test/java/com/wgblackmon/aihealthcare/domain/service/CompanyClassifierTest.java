package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.Company;
import com.wgblackmon.aihealthcare.domain.model.CompanyTags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CompanyClassifier} keyword-based heuristic classification.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-07
 * @updated 2026-06-07
 */
class CompanyClassifierTest {

    private CompanyClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new CompanyClassifier();
    }

    @Test
    void classify_scribeKeyword_setsScribeTag() {
        Company company = makeCompany("ScribeBot", "AI scribe for clinical documentation");
        Company result = classifier.classify(company);

        assertThat(result.tags().scribe()).isTrue();
        assertThat(result.isAI()).isTrue();
        assertThat(result.isHealth()).isTrue();
    }

    @Test
    void classify_agentKeyword_setsAgentTag() {
        Company company = makeCompany("CareNav", "AI agent for patient engagement and virtual care");
        Company result = classifier.classify(company);

        assertThat(result.tags().agent()).isTrue();
        assertThat(result.isAI()).isTrue();
        assertThat(result.isHealth()).isTrue();
    }

    @Test
    void classify_imagingKeyword_setsImagingTag() {
        Company company = makeCompany("RadiologyAI", "AI-powered radiology imaging analysis for hospitals");
        Company result = classifier.classify(company);

        assertThat(result.tags().imaging()).isTrue();
        assertThat(result.isAI()).isTrue();
        assertThat(result.isHealth()).isTrue();
    }

    @Test
    void classify_rcmKeyword_setsRcmTag() {
        Company company = makeCompany("BillFix", "Revenue cycle management and claims processing for healthcare");
        Company result = classifier.classify(company);

        assertThat(result.tags().rcm()).isTrue();
        assertThat(result.isHealth()).isTrue();
    }

    @Test
    void classify_infraKeyword_setsInfraTag() {
        Company company = makeCompany("HealthPlatform", "Clinical data platform with FHIR API for healthcare providers");
        Company result = classifier.classify(company);

        assertThat(result.tags().infra()).isTrue();
        assertThat(result.isHealth()).isTrue();
    }

    @Test
    void classify_multipleTags_setsAll() {
        Company company = makeCompany("OmniHealth",
                "AI copilot for clinical documentation and diagnostic imaging in hospitals");
        Company result = classifier.classify(company);

        assertThat(result.tags().scribe()).isTrue();
        assertThat(result.tags().agent()).isTrue();
        assertThat(result.tags().imaging()).isTrue();
        assertThat(result.isAI()).isTrue();
        assertThat(result.isHealth()).isTrue();
    }

    @Test
    void classify_noKeywords_allFlagsFalse() {
        Company company = makeCompany("RandomCo", "A company that makes widgets.");
        Company result = classifier.classify(company);

        assertThat(result.tags().scribe()).isFalse();
        assertThat(result.tags().agent()).isFalse();
        assertThat(result.tags().imaging()).isFalse();
        assertThat(result.tags().rcm()).isFalse();
        assertThat(result.tags().infra()).isFalse();
        assertThat(result.isAI()).isFalse();
        assertThat(result.isHealth()).isFalse();
    }

    @Test
    void classifyAll_processesList() {
        List<Company> companies = List.of(
                makeCompany("ScribeBot", "AI scribe for clinics"),
                makeCompany("Widget", "Unrelated product")
        );

        List<Company> results = classifier.classifyAll(companies);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).tags().scribe()).isTrue();
        assertThat(results.get(1).tags().scribe()).isFalse();
    }

    @Test
    void classify_aiFlag_matchesPaddedKeyword() {
        // " ai " with surrounding spaces
        Company company = makeCompany("HealthX", "An ai tool for clinics");
        Company result = classifier.classify(company);

        assertThat(result.isAI()).isTrue();
    }

    @Test
    void classify_healthFlag_matchesMedic() {
        Company company = makeCompany("MedicBot", "Helps medical staff with scheduling");
        Company result = classifier.classify(company);

        assertThat(result.isHealth()).isTrue();
    }

    private Company makeCompany(String name, String description) {
        return new Company(name, "test", null, null, description, CompanyTags.none(), false, false);
    }
}
