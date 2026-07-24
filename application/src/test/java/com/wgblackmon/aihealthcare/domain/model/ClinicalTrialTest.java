package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ClinicalTrial} record validation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-23
 * @updated 2026-07-23
 */
class ClinicalTrialTest {

    private static final Instant NOW = Instant.now();

    @Test
    void validTrialCreatesSuccessfully() {
        ClinicalTrial trial = trial("t1", "NCT06123456", "AI Radiology Trial");

        assertThat(trial.trialId()).isEqualTo("t1");
        assertThat(trial.nctId()).isEqualTo("NCT06123456");
        assertThat(trial.title()).isEqualTo("AI Radiology Trial");
        assertThat(trial.status()).isEqualTo(ClinicalTrialStatus.RECRUITING);
    }

    @Test
    void nullTrialIdThrows() {
        assertThatThrownBy(() -> trial(null, "NCT001", "Title"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trialId");
    }

    @Test
    void blankTrialIdThrows() {
        assertThatThrownBy(() -> trial("  ", "NCT001", "Title"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trialId");
    }

    @Test
    void nullNctIdThrows() {
        assertThatThrownBy(() -> trial("t1", null, "Title"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nctId");
    }

    @Test
    void blankNctIdThrows() {
        assertThatThrownBy(() -> trial("t1", "", "Title"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nctId");
    }

    @Test
    void nullTitleThrows() {
        assertThatThrownBy(() -> trial("t1", "NCT001", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void blankTitleThrows() {
        assertThatThrownBy(() -> trial("t1", "NCT001", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void nullStatusThrows() {
        assertThatThrownBy(() -> new ClinicalTrial("t1", "NCT001", "Title",
                null, null, null, null, null,
                "https://clinicaltrials.gov/study/NCT001", null, null, NOW, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");
    }

    @Test
    void nullSourceUrlThrows() {
        assertThatThrownBy(() -> new ClinicalTrial("t1", "NCT001", "Title",
                null, ClinicalTrialStatus.RECRUITING, null, null, null,
                null, null, null, NOW, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceUrl");
    }

    @Test
    void nullDiscoveredAtThrows() {
        assertThatThrownBy(() -> new ClinicalTrial("t1", "NCT001", "Title",
                null, ClinicalTrialStatus.RECRUITING, null, null, null,
                "https://clinicaltrials.gov/study/NCT001", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("discoveredAt");
    }

    @Test
    void nullConditionsDefaultsToEmptyList() {
        ClinicalTrial trial = trial("t1", "NCT001", "Title");

        assertThat(trial.conditions()).isEmpty();
    }

    @Test
    void conditionsAreDefensivelyCopied() {
        List<String> conditions = new ArrayList<>();
        conditions.add("Lung Cancer");
        conditions.add("COPD");

        ClinicalTrial trial = new ClinicalTrial("t1", "NCT001", "Title",
                "Sponsor Co", ClinicalTrialStatus.RECRUITING, ClinicalTrialPhase.PHASE_2,
                conditions, null, "https://clinicaltrials.gov/study/NCT001",
                "INTERVENTIONAL", null, NOW, null);

        assertThat(trial.conditions()).hasSize(2);
        assertThatThrownBy(() -> trial.conditions().add("new"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullKeywordsDefaultsToEmptyList() {
        ClinicalTrial trial = trial("t1", "NCT001", "Title");

        assertThat(trial.aiHealthcareKeywords()).isEmpty();
    }

    @Test
    void keywordsAreDefensivelyCopied() {
        List<String> keywords = new ArrayList<>();
        keywords.add("AI");
        keywords.add("radiology");

        ClinicalTrial trial = new ClinicalTrial("t1", "NCT001", "Title",
                null, ClinicalTrialStatus.RECRUITING, null, null, null,
                "https://clinicaltrials.gov/study/NCT001", null, null, NOW, keywords);

        assertThat(trial.aiHealthcareKeywords()).hasSize(2);
        assertThatThrownBy(() -> trial.aiHealthcareKeywords().add("new"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void optionalFieldsCanBeNull() {
        ClinicalTrial trial = trial("t1", "NCT001", "Title");

        assertThat(trial.sponsor()).isNull();
        assertThat(trial.phase()).isNull();
        assertThat(trial.briefSummary()).isNull();
        assertThat(trial.studyType()).isNull();
        assertThat(trial.startDate()).isNull();
    }

    @Test
    void allFieldsPopulated() {
        ClinicalTrial trial = new ClinicalTrial("t1", "NCT06123456",
                "AI-Assisted Lung Cancer Screening",
                "Tempus AI", ClinicalTrialStatus.RECRUITING, ClinicalTrialPhase.PHASE_3,
                List.of("Lung Cancer", "NSCLC"),
                "A study evaluating AI-assisted screening for early lung cancer detection.",
                "https://clinicaltrials.gov/study/NCT06123456",
                "INTERVENTIONAL",
                Instant.parse("2026-01-15T00:00:00Z"), NOW,
                List.of("radiology", "lung", "AI"));

        assertThat(trial.sponsor()).isEqualTo("Tempus AI");
        assertThat(trial.phase()).isEqualTo(ClinicalTrialPhase.PHASE_3);
        assertThat(trial.conditions()).containsExactly("Lung Cancer", "NSCLC");
        assertThat(trial.studyType()).isEqualTo("INTERVENTIONAL");
        assertThat(trial.aiHealthcareKeywords()).containsExactly("radiology", "lung", "AI");
    }

    // --- Helper ---

    private ClinicalTrial trial(String trialId, String nctId, String title) {
        return new ClinicalTrial(trialId, nctId, title,
                null, ClinicalTrialStatus.RECRUITING, null, null, null,
                "https://clinicaltrials.gov/study/" + nctId, null, null, NOW, null);
    }
}
