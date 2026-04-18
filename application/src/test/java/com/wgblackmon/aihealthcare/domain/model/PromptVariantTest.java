package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link PromptVariant} domain record.
 *
 * <p>Verifies that the compact constructor enforces all required field constraints
 * and that optional fields ({@code description}) accept blank values.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-17
 * @updated 2026-04-17
 */
class PromptVariantTest {

    private static final String  VARIANT_ID = "summarize-v2-concise";
    private static final String  NAME       = "Concise V2";
    private static final String  TEMPLATE   = "Summarize {topic} articles in {toneInstruction} style.";
    private static final String  DESC       = "Shorter summaries for mobile readers";
    private static final Instant NOW        = Instant.parse("2026-04-17T00:00:00Z");

    @Test
    void validVariant_constructsSuccessfully() {
        PromptVariant variant = new PromptVariant(VARIANT_ID, NAME, TEMPLATE, DESC, NOW);

        assertThat(variant.variantId()).isEqualTo(VARIANT_ID);
        assertThat(variant.name()).isEqualTo(NAME);
        assertThat(variant.templateText()).isEqualTo(TEMPLATE);
        assertThat(variant.description()).isEqualTo(DESC);
        assertThat(variant.createdAt()).isEqualTo(NOW);
    }

    @Test
    void blankDescription_isAllowed() {
        PromptVariant variant = new PromptVariant(VARIANT_ID, NAME, TEMPLATE, "", NOW);

        assertThat(variant.description()).isEmpty();
    }

    @Test
    void nullDescription_isAllowed() {
        PromptVariant variant = new PromptVariant(VARIANT_ID, NAME, TEMPLATE, null, NOW);

        assertThat(variant.description()).isNull();
    }

    @Test
    void nullVariantId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new PromptVariant(null, NAME, TEMPLATE, DESC, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("variantId");
    }

    @Test
    void blankVariantId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new PromptVariant("  ", NAME, TEMPLATE, DESC, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("variantId");
    }

    @Test
    void nullName_throwsIllegalArgument() {
        assertThatThrownBy(() -> new PromptVariant(VARIANT_ID, null, TEMPLATE, DESC, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void blankName_throwsIllegalArgument() {
        assertThatThrownBy(() -> new PromptVariant(VARIANT_ID, "", TEMPLATE, DESC, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void nullTemplateText_throwsIllegalArgument() {
        assertThatThrownBy(() -> new PromptVariant(VARIANT_ID, NAME, null, DESC, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("templateText");
    }

    @Test
    void blankTemplateText_throwsIllegalArgument() {
        assertThatThrownBy(() -> new PromptVariant(VARIANT_ID, NAME, "  ", DESC, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("templateText");
    }

    @Test
    void nullCreatedAt_throwsIllegalArgument() {
        assertThatThrownBy(() -> new PromptVariant(VARIANT_ID, NAME, TEMPLATE, DESC, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("createdAt");
    }
}
