package com.wgblackmon.aihealthcare.infrastructure.ingestion.clinicaltrial;

import com.wgblackmon.aihealthcare.domain.model.ClinicalTrial;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ClinicalTrialsGovHarvester}.
 *
 * <p>Tests disabled-flag handling and graceful network error behavior.
 * Live API calls may or may not succeed in CI — the harvester gracefully
 * returns an empty list on network errors.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-23
 * @updated 2026-07-23
 */
class ClinicalTrialsGovHarvesterTest {

    @Test
    void harvestAllReturnsEmptyWhenDisabled() {
        ClinicalTrialHarvestProperties props = new ClinicalTrialHarvestProperties();
        props.setEnabled(false);
        ClinicalTrialsGovHarvester harvester = new ClinicalTrialsGovHarvester(props);

        List<ClinicalTrial> result = harvester.harvestAll();

        assertThat(result).isEmpty();
    }

    @Test
    void harvestAllReturnsNonNullOnNetworkError() {
        ClinicalTrialHarvestProperties props = new ClinicalTrialHarvestProperties();
        props.setEnabled(true);
        props.setLookbackDays(1);
        props.setMaxResults(5);
        props.setAiKeywords(List.of("unicorn_keyword_that_matches_nothing_12345"));
        ClinicalTrialsGovHarvester harvester = new ClinicalTrialsGovHarvester(props);

        List<ClinicalTrial> result = harvester.harvestAll();

        assertThat(result).isNotNull();
    }

    @Test
    void harvestAllRespectsMaxResults() {
        ClinicalTrialHarvestProperties props = new ClinicalTrialHarvestProperties();
        props.setEnabled(true);
        props.setMaxResults(5);
        ClinicalTrialsGovHarvester harvester = new ClinicalTrialsGovHarvester(props);

        // Even if API returns results, the harvester should respect maxResults
        List<ClinicalTrial> result = harvester.harvestAll();
        assertThat(result).isNotNull();
        assertThat(result.size()).isLessThanOrEqualTo(5);
    }

    @Test
    void defaultPropertiesAreReasonable() {
        ClinicalTrialHarvestProperties props = new ClinicalTrialHarvestProperties();

        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getLookbackDays()).isEqualTo(7);
        assertThat(props.getMaxResults()).isEqualTo(100);
        assertThat(props.getAiKeywords()).isNotEmpty();
        assertThat(props.getSchedule()).isEqualTo("0 0 5 * * *");
    }

    @Test
    void propertiesKeywordsContainExpectedTerms() {
        ClinicalTrialHarvestProperties props = new ClinicalTrialHarvestProperties();

        assertThat(props.getAiKeywords()).contains(
                "artificial intelligence",
                "machine learning",
                "deep learning"
        );
    }
}
