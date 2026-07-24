package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.ClinicalTrial;
import com.wgblackmon.aihealthcare.domain.model.ClinicalTrialPhase;
import com.wgblackmon.aihealthcare.domain.model.ClinicalTrialStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ClinicalTrialAdapter} using in-memory H2.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-23
 * @updated 2026-07-23
 */
@DataJpaTest
@Import(ClinicalTrialAdapter.class)
class ClinicalTrialAdapterTest {

    @Autowired
    private ClinicalTrialAdapter adapter;

    @Test
    void saveAndFindById() {
        ClinicalTrial trial = trial("t1", "NCT001", ClinicalTrialStatus.RECRUITING, ClinicalTrialPhase.PHASE_2);
        adapter.save(trial);

        Optional<ClinicalTrial> result = adapter.findById("t1");

        assertThat(result).isPresent();
        assertThat(result.get().nctId()).isEqualTo("NCT001");
        assertThat(result.get().title()).isEqualTo("AI Clinical Trial");
        assertThat(result.get().status()).isEqualTo(ClinicalTrialStatus.RECRUITING);
        assertThat(result.get().phase()).isEqualTo(ClinicalTrialPhase.PHASE_2);
    }

    @Test
    void existsByNctId() {
        adapter.save(trial("t1", "NCT001", ClinicalTrialStatus.RECRUITING, null));

        assertThat(adapter.existsByNctId("NCT001")).isTrue();
        assertThat(adapter.existsByNctId("NCT999")).isFalse();
    }

    @Test
    void findRecentReturnsLimited() {
        adapter.save(trial("t1", "NCT001", ClinicalTrialStatus.RECRUITING, null));
        adapter.save(trial("t2", "NCT002", ClinicalTrialStatus.COMPLETED, null));
        adapter.save(trial("t3", "NCT003", ClinicalTrialStatus.RECRUITING, null));

        List<ClinicalTrial> result = adapter.findRecent(2);

        assertThat(result).hasSize(2);
    }

    @Test
    void findByStatusFilters() {
        adapter.save(trial("t1", "NCT001", ClinicalTrialStatus.RECRUITING, null));
        adapter.save(trial("t2", "NCT002", ClinicalTrialStatus.COMPLETED, null));

        List<ClinicalTrial> recruiting = adapter.findByStatus(ClinicalTrialStatus.RECRUITING, 10);
        List<ClinicalTrial> completed = adapter.findByStatus(ClinicalTrialStatus.COMPLETED, 10);

        assertThat(recruiting).hasSize(1);
        assertThat(completed).hasSize(1);
    }

    @Test
    void findByPhaseFilters() {
        adapter.save(trial("t1", "NCT001", ClinicalTrialStatus.RECRUITING, ClinicalTrialPhase.PHASE_2));
        adapter.save(trial("t2", "NCT002", ClinicalTrialStatus.RECRUITING, ClinicalTrialPhase.PHASE_3));

        List<ClinicalTrial> phase2 = adapter.findByPhase(ClinicalTrialPhase.PHASE_2, 10);
        List<ClinicalTrial> phase3 = adapter.findByPhase(ClinicalTrialPhase.PHASE_3, 10);

        assertThat(phase2).hasSize(1);
        assertThat(phase3).hasSize(1);
    }

    @Test
    void saveAllBatchPersists() {
        List<ClinicalTrial> trials = List.of(
                trial("t1", "NCT001", ClinicalTrialStatus.RECRUITING, null),
                trial("t2", "NCT002", ClinicalTrialStatus.COMPLETED, null)
        );
        adapter.saveAll(trials);

        assertThat(adapter.findRecent(10)).hasSize(2);
    }

    @Test
    void conditionsAndKeywordsRoundTrip() {
        ClinicalTrial trial = new ClinicalTrial("t1", "NCT001",
                "AI Radiology Trial", "Tempus AI",
                ClinicalTrialStatus.RECRUITING, ClinicalTrialPhase.PHASE_3,
                List.of("Lung Cancer", "NSCLC"),
                "A study of AI in radiology",
                "https://clinicaltrials.gov/study/NCT001",
                "INTERVENTIONAL", Instant.now(), Instant.now(),
                List.of("AI", "radiology", "deep learning"));
        adapter.save(trial);

        Optional<ClinicalTrial> result = adapter.findById("t1");
        assertThat(result).isPresent();
        assertThat(result.get().conditions()).containsExactly("Lung Cancer", "NSCLC");
        assertThat(result.get().aiHealthcareKeywords()).containsExactly("AI", "radiology", "deep learning");
        assertThat(result.get().sponsor()).isEqualTo("Tempus AI");
        assertThat(result.get().studyType()).isEqualTo("INTERVENTIONAL");
    }

    @Test
    void nullPhaseRoundTrips() {
        adapter.save(trial("t1", "NCT001", ClinicalTrialStatus.RECRUITING, null));

        Optional<ClinicalTrial> result = adapter.findById("t1");

        assertThat(result).isPresent();
        assertThat(result.get().phase()).isNull();
    }

    // --- Helper ---

    private ClinicalTrial trial(String trialId, String nctId,
                                 ClinicalTrialStatus status, ClinicalTrialPhase phase) {
        return new ClinicalTrial(trialId, nctId, "AI Clinical Trial",
                "Sponsor Inc", status, phase, null, "Summary text",
                "https://clinicaltrials.gov/study/" + nctId,
                "INTERVENTIONAL", null, Instant.now(), List.of("AI"));
    }
}
