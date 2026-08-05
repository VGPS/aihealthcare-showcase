package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link CompanyRelationship} domain record.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
class CompanyRelationshipTest {

    private static final Instant NOW = Instant.now();

    @Test
    void validRecord_createsSuccessfully() {
        CompanyRelationship rel = new CompanyRelationship("r1", "Google", "DeepMind",
                CompanyRelationshipType.ACQUISITION, "a1", "Google acquires DeepMind", 0.9, NOW);
        assertThat(rel.sourceCompany()).isEqualTo("Google");
        assertThat(rel.targetCompany()).isEqualTo("DeepMind");
        assertThat(rel.relationshipType()).isEqualTo(CompanyRelationshipType.ACQUISITION);
    }

    @Test
    void blankRelationshipId_throws() {
        assertThatThrownBy(() -> new CompanyRelationship("", "A", "B",
                CompanyRelationshipType.PARTNERSHIP, "a1", "Sum", 0.5, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("relationshipId");
    }

    @Test
    void blankSourceCompany_throws() {
        assertThatThrownBy(() -> new CompanyRelationship("r1", " ", "B",
                CompanyRelationshipType.PARTNERSHIP, "a1", "Sum", 0.5, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceCompany");
    }

    @Test
    void blankTargetCompany_throws() {
        assertThatThrownBy(() -> new CompanyRelationship("r1", "A", "",
                CompanyRelationshipType.PARTNERSHIP, "a1", "Sum", 0.5, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetCompany");
    }

    @Test
    void nullRelationshipType_throws() {
        assertThatThrownBy(() -> new CompanyRelationship("r1", "A", "B",
                null, "a1", "Sum", 0.5, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("relationshipType");
    }

    @Test
    void confidenceOutOfRange_throws() {
        assertThatThrownBy(() -> new CompanyRelationship("r1", "A", "B",
                CompanyRelationshipType.INVESTMENT, "a1", "Sum", 1.5, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidence");
    }

    @Test
    void nullDetectedAt_throws() {
        assertThatThrownBy(() -> new CompanyRelationship("r1", "A", "B",
                CompanyRelationshipType.INTEGRATION, "a1", "Sum", 0.5, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("detectedAt");
    }

    @Test
    void allRelationshipTypes_haveValues() {
        assertThat(CompanyRelationshipType.values()).containsExactly(
                CompanyRelationshipType.PARTNERSHIP,
                CompanyRelationshipType.ACQUISITION,
                CompanyRelationshipType.INVESTMENT,
                CompanyRelationshipType.COMPETITOR,
                CompanyRelationshipType.SUPPLIER,
                CompanyRelationshipType.INTEGRATION
        );
    }
}
