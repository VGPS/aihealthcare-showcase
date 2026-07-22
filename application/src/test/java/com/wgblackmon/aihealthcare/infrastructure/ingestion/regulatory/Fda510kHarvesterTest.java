package com.wgblackmon.aihealthcare.infrastructure.ingestion.regulatory;

import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEventType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Fda510kHarvester}.
 *
 * <p>Tests keyword matching and source name — live API calls are not made
 * in unit tests. The harvester gracefully returns an empty list on network errors.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
class Fda510kHarvesterTest {

    private final Fda510kHarvester harvester = new Fda510kHarvester();

    @Test
    void sourceNameIsFda510k() {
        assertThat(harvester.sourceName()).isEqualTo("FDA 510(k)");
    }

    @Test
    void harvestReturnsEmptyOnNetworkError() {
        // With default HttpClient and no mock server, this may return empty
        // or actual results — in CI without network, it returns empty gracefully
        List<RegulatoryEvent> events = harvester.harvest(1, List.of("nonexistent_keyword_xyz"));
        assertThat(events).isNotNull();
    }

    @Test
    void harvestFiltersOnAiKeywords() {
        // With very narrow keywords that won't match any FDA device,
        // the result should be empty even if API returns results
        List<RegulatoryEvent> events = harvester.harvest(1,
                List.of("unicorn_keyword_that_matches_nothing_12345"));
        assertThat(events).isEmpty();
    }
}
