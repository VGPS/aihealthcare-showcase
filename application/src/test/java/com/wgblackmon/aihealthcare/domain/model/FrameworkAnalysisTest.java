package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link FrameworkAnalysis} and {@link FrameworkDimension} domain records.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
class FrameworkAnalysisTest {

    @Test
    void dimension_validConstruction() {
        FrameworkDimension dim = new FrameworkDimension("Technical Maturity", 7, "Strong API surface");
        assertThat(dim.name()).isEqualTo("Technical Maturity");
        assertThat(dim.score()).isEqualTo(7);
        assertThat(dim.rationale()).isEqualTo("Strong API surface");
    }

    @Test
    void dimension_rejectsScoreBelowOne() {
        assertThatThrownBy(() -> new FrameworkDimension("Test", 0, "rationale"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Score must be between 1 and 10");
    }

    @Test
    void dimension_rejectsScoreAboveTen() {
        assertThatThrownBy(() -> new FrameworkDimension("Test", 11, "rationale"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Score must be between 1 and 10");
    }

    @Test
    void dimension_rejectsBlankName() {
        assertThatThrownBy(() -> new FrameworkDimension("", 5, "rationale"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name must not be blank");
    }

    @Test
    void dimension_rejectsBlankRationale() {
        assertThatThrownBy(() -> new FrameworkDimension("Test", 5, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rationale must not be blank");
    }

    @Test
    void analysis_validConstruction() {
        List<FrameworkDimension> dims = List.of(
                new FrameworkDimension("Technical Maturity", 8, "Strong APIs"));
        FrameworkAnalysis analysis = new FrameworkAnalysis(
                "anthropic", "Anthropic", "Great platform",
                dims, List.of("Strength 1"), List.of("Weakness 1"),
                List.of("Dev 1"), 8, 25, Instant.now());

        assertThat(analysis.companySlug()).isEqualTo("anthropic");
        assertThat(analysis.companyName()).isEqualTo("Anthropic");
        assertThat(analysis.dimensions()).hasSize(1);
        assertThat(analysis.strengths()).containsExactly("Strength 1");
        assertThat(analysis.weaknesses()).containsExactly("Weakness 1");
        assertThat(analysis.overallScore()).isEqualTo(8);
    }

    @Test
    void analysis_rejectsBlankSlug() {
        assertThatThrownBy(() -> new FrameworkAnalysis(
                "", "Name", "Assessment",
                List.of(), List.of(), List.of(), List.of(), 5, 10, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("companySlug must not be blank");
    }

    @Test
    void analysis_rejectsNullAssessment() {
        assertThatThrownBy(() -> new FrameworkAnalysis(
                "slug", "Name", null,
                List.of(), List.of(), List.of(), List.of(), 5, 10, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overallAssessment must not be blank");
    }

    @Test
    void analysis_defensivelyCopiesLists() {
        List<String> strengths = new java.util.ArrayList<>();
        strengths.add("Strength 1");

        FrameworkAnalysis analysis = new FrameworkAnalysis(
                "slug", "Name", "Assessment",
                List.of(new FrameworkDimension("Dim", 5, "OK")),
                strengths, List.of(), List.of(), 5, 10, Instant.now());

        strengths.add("Strength 2");
        assertThat(analysis.strengths()).hasSize(1);
    }

    @Test
    void analysis_nullListsDefaultToEmpty() {
        FrameworkAnalysis analysis = new FrameworkAnalysis(
                "slug", "Name", "Assessment",
                List.of(new FrameworkDimension("Dim", 5, "OK")),
                null, null, null, 5, 10, Instant.now());

        assertThat(analysis.strengths()).isEmpty();
        assertThat(analysis.weaknesses()).isEmpty();
        assertThat(analysis.recentDevelopments()).isEmpty();
    }

    @Test
    void frameworkCompany_validConstruction() {
        FrameworkCompany company = new FrameworkCompany(
                "anthropic", "Anthropic", "https://www.anthropic.com",
                List.of("Anthropic Healthcare"));
        assertThat(company.slug()).isEqualTo("anthropic");
        assertThat(company.topics()).containsExactly("Anthropic Healthcare");
    }

    @Test
    void frameworkCompany_rejectsBlankSlug() {
        assertThatThrownBy(() -> new FrameworkCompany("", "Name", null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slug must not be blank");
    }

    @Test
    void frameworkCompany_nullTopicsDefaultsToEmpty() {
        FrameworkCompany company = new FrameworkCompany("slug", "Name", null, null);
        assertThat(company.topics()).isEmpty();
    }
}
