package com.wgblackmon.aihealthcare.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SlugUtils} — the canonical slug generator.
 *
 * Tests cover the edge cases that previously caused cross-service mismatches:
 * names with '+', consecutive special chars, leading/trailing dashes.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
class SlugUtilsTest {

    @ParameterizedTest
    @CsvSource({
            "'Tempus AI, Inc.',    tempus-ai-inc",
            "'GE HealthCare',     ge-healthcare",
            "'Olive AI',          olive-ai",
            "'23andMe',           23andme",
            "'R1 / RCM',          r1-rcm",
            "'Health+AI',         health-ai",
            "'---Foo---',         foo",
            "'hello world',       hello-world",
            "'UPPER CASE',        upper-case",
    })
    void toSlug_producesConsistentKebabCase(String input, String expected) {
        assertThat(SlugUtils.toSlug(input)).isEqualTo(expected);
    }

    @Test
    void toSlug_nullReturnsEmpty() {
        assertThat(SlugUtils.toSlug(null)).isEmpty();
    }

    @Test
    void toSlug_blankReturnsEmpty() {
        assertThat(SlugUtils.toSlug("   ")).isEmpty();
    }

    @Test
    void toSlug_emptyStringReturnsEmpty() {
        assertThat(SlugUtils.toSlug("")).isEmpty();
    }

    @Test
    void toSlug_singleWordNoChange() {
        assertThat(SlugUtils.toSlug("anthropic")).isEqualTo("anthropic");
    }

    @Test
    void toSlug_consecutiveSpecialCharsCollapsedToSingleDash() {
        assertThat(SlugUtils.toSlug("a...b")).isEqualTo("a-b");
    }

    @Test
    void toSlug_leadingAndTrailingSpecialCharsStripped() {
        assertThat(SlugUtils.toSlug("!!foo!!")).isEqualTo("foo");
    }
}
