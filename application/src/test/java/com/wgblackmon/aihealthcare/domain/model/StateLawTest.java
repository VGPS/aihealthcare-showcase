package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link StateLaw} record validation and related enum behavior.
 *
 * <p>Verifies compact-constructor validation (required fields, defensive copies)
 * and the {@link StateCode#fromCode(String)} / {@link LawCategory#fromCode(String)}
 * safe-parsing methods.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
class StateLawTest {

    private static final Instant NOW = Instant.now();

    @Test
    void stateLaw_validConstruction_succeeds() {
        StateLaw law = createTestLaw("ca-ab-3030", StateCode.CA, "AI Disclosure Act");

        assertThat(law.id()).isEqualTo("ca-ab-3030");
        assertThat(law.stateCode()).isEqualTo(StateCode.CA);
        assertThat(law.stateName()).isEqualTo("California");
        assertThat(law.billNumber()).isEqualTo("HB 100");
        assertThat(law.title()).isEqualTo("AI Disclosure Act");
        assertThat(law.yearEnacted()).isEqualTo(2026);
        assertThat(law.dateSigned()).isEqualTo("2026-06-01");
        assertThat(law.effectiveDate()).isEqualTo("2027-01-01");
        assertThat(law.status()).isEqualTo(LawStatus.ENACTED);
        assertThat(law.categories()).containsExactly(LawCategory.PAYER_UTILIZATION_REVIEW);
        assertThat(law.sources()).hasSize(1);
        assertThat(law.createdAt()).isNotNull();
        assertThat(law.updatedAt()).isNotNull();
    }

    @Test
    void stateLaw_nullId_throws() {
        assertThatThrownBy(() -> createTestLaw(null, StateCode.CA, "Title"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void stateLaw_blankId_throws() {
        assertThatThrownBy(() -> createTestLaw("  ", StateCode.CA, "Title"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void stateLaw_nullStateCode_throws() {
        assertThatThrownBy(() -> createTestLaw("test-1", null, "Title"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stateCode");
    }

    @Test
    void stateLaw_nullTitle_throws() {
        assertThatThrownBy(() -> createTestLaw("test-1", StateCode.TX, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void stateLaw_blankTitle_throws() {
        assertThatThrownBy(() -> createTestLaw("test-1", StateCode.TX, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void stateLaw_defensiveCopy_categories() {
        List<LawCategory> mutableCategories = new ArrayList<>();
        mutableCategories.add(LawCategory.CLAIMS_DOWNCODING);

        StateLaw law = new StateLaw("test-1", StateCode.NY, "New York", "SB 200",
                "AI Claims Act", 2025, null, null, null, null,
                LawStatus.ENACTED, null, mutableCategories, null, null, null,
                List.of(), null, "1.0", NOW, NOW);

        assertThat(law.categories()).hasSize(1);
        assertThatThrownBy(() -> law.categories().add(LawCategory.OTHER))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void stateLaw_defensiveCopy_sources() {
        List<LawSource> mutableSources = new ArrayList<>();
        mutableSources.add(new LawSource(SourceType.OFFICIAL, "https://example.com",
                null, null, null, false));

        StateLaw law = new StateLaw("test-1", StateCode.FL, "Florida", "HB 300",
                "AI Healthcare Act", 2025, null, null, null, null,
                LawStatus.PENDING, null, List.of(), null, null, null,
                mutableSources, null, "1.0", NOW, NOW);

        assertThat(law.sources()).hasSize(1);
        assertThatThrownBy(() -> law.sources().add(
                new LawSource(SourceType.SECONDARY, "https://news.com", null, null, null, false)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void stateCode_fromCode_valid() {
        Optional<StateCode> result = StateCode.fromCode("CA");

        assertThat(result).isPresent().contains(StateCode.CA);
        assertThat(StateCode.CA.displayName()).isEqualTo("California");
    }

    @Test
    void stateCode_fromCode_invalid() {
        Optional<StateCode> result = StateCode.fromCode("XX");

        assertThat(result).isEmpty();
    }

    @Test
    void stateCode_fromCode_caseInsensitive() {
        Optional<StateCode> result = StateCode.fromCode("ca");

        assertThat(result).isPresent().contains(StateCode.CA);
    }

    @Test
    void stateCode_fromCode_nullReturnsEmpty() {
        assertThat(StateCode.fromCode(null)).isEmpty();
        assertThat(StateCode.fromCode("")).isEmpty();
        assertThat(StateCode.fromCode("  ")).isEmpty();
    }

    @Test
    void lawCategory_fromCode_valid() {
        Optional<LawCategory> result = LawCategory.fromCode("PAYER_UTILIZATION_REVIEW");

        assertThat(result).isPresent().contains(LawCategory.PAYER_UTILIZATION_REVIEW);
        assertThat(LawCategory.PAYER_UTILIZATION_REVIEW.displayLabel())
                .isEqualTo("Payer Utilization Review");
    }

    @Test
    void lawCategory_fromCode_invalid() {
        Optional<LawCategory> result = LawCategory.fromCode("BOGUS");

        assertThat(result).isEmpty();
    }

    @Test
    void lawCategory_fromCode_nullReturnsEmpty() {
        assertThat(LawCategory.fromCode(null)).isEmpty();
        assertThat(LawCategory.fromCode("")).isEmpty();
    }

    @Test
    void lawStatus_displayLabel() {
        assertThat(LawStatus.ENACTED.displayLabel()).isEqualTo("Enacted");
        assertThat(LawStatus.ENACTED_STAYED.displayLabel()).isEqualTo("Enacted (Stayed)");
        assertThat(LawStatus.NOT_ENACTED.displayLabel()).isEqualTo("Not Enacted");
        assertThat(LawStatus.PENDING.displayLabel()).isEqualTo("Pending");
    }

    @Test
    void sourceType_displayLabel() {
        assertThat(SourceType.OFFICIAL.displayLabel()).isEqualTo("Official");
        assertThat(SourceType.SECONDARY.displayLabel()).isEqualTo("Secondary");
    }

    @Test
    void stateLaw_nullCategories_defaultsToEmptyList() {
        StateLaw law = new StateLaw("test-1", StateCode.AL, "Alabama", "SB 1",
                "Test Law", 2025, null, null, null, null,
                LawStatus.ENACTED, null, null, null, null, null,
                null, null, "1.0", NOW, NOW);

        assertThat(law.categories()).isEmpty();
        assertThat(law.sources()).isEmpty();
    }

    @Test
    void lawSource_nullSourceType_throws() {
        assertThatThrownBy(() -> new LawSource(null, "https://example.com",
                null, null, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceType");
    }

    @Test
    void lawSource_nullUrl_throws() {
        assertThatThrownBy(() -> new LawSource(SourceType.OFFICIAL, null,
                null, null, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url");
    }

    @Test
    void lawSource_blankUrl_throws() {
        assertThatThrownBy(() -> new LawSource(SourceType.OFFICIAL, "  ",
                null, null, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url");
    }

    // --- Helper ---

    private StateLaw createTestLaw(String id, StateCode state, String title) {
        return new StateLaw(id, state,
                state != null ? state.displayName() : null,
                "HB 100", title, 2026,
                "2026-06-01", null, "2027-01-01", null,
                LawStatus.ENACTED, "Enacted",
                List.of(LawCategory.PAYER_UTILIZATION_REVIEW),
                "Insurers", "Must disclose AI use", "Dept of Insurance",
                List.of(new LawSource(SourceType.OFFICIAL, "https://example.com",
                        null, null, null, false)),
                null, "2026-09-06", NOW, NOW);
    }
}
