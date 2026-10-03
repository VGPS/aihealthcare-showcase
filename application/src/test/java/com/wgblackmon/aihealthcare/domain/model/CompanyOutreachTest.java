package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CompanyOutreach}, {@link CompanyContact},
 * and the four associated enums.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-03
 * @updated 2026-10-03
 */
class CompanyOutreachTest {

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

    // ── CompanyOutreach validation ──────────────────────────────────────────

    @Test
    void companyOutreach_validRecord_creates() {
        CompanyOutreach o = new CompanyOutreach(null, "microsoft", OutreachPurpose.EMPLOYMENT,
                OutreachStatus.NOT_STARTED, null, "Initial contact", NOW, NOW);
        assertThat(o.slug()).isEqualTo("microsoft");
        assertThat(o.purpose()).isEqualTo(OutreachPurpose.EMPLOYMENT);
        assertThat(o.status()).isEqualTo(OutreachStatus.NOT_STARTED);
        assertThat(o.contactedAt()).isNull();
    }

    @Test
    void companyOutreach_nullSlug_throws() {
        assertThatThrownBy(() ->
                new CompanyOutreach(null, null, OutreachPurpose.SUBSCRIPTION,
                        OutreachStatus.NOT_STARTED, null, null, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slug");
    }

    @Test
    void companyOutreach_blankSlug_throws() {
        assertThatThrownBy(() ->
                new CompanyOutreach(null, "  ", OutreachPurpose.SUBSCRIPTION,
                        OutreachStatus.NOT_STARTED, null, null, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slug");
    }

    @Test
    void companyOutreach_nullPurpose_throws() {
        assertThatThrownBy(() ->
                new CompanyOutreach(null, "acme", null,
                        OutreachStatus.NOT_STARTED, null, null, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("purpose");
    }

    @Test
    void companyOutreach_nullStatus_throws() {
        assertThatThrownBy(() ->
                new CompanyOutreach(null, "acme", OutreachPurpose.EMPLOYMENT,
                        null, null, null, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");
    }

    // ── CompanyContact validation ───────────────────────────────────────────

    @Test
    void companyContact_validRecord_creates() {
        CompanyContact c = new CompanyContact(null, "microsoft", "Jane Doe", "CTO",
                "jane@example.com", "https://linkedin.com/in/janedoe",
                ContactSource.LINKEDIN, ContactStatus.IDENTIFIED, "Met at HIMSS", NOW, NOW);
        assertThat(c.fullName()).isEqualTo("Jane Doe");
        assertThat(c.status()).isEqualTo(ContactStatus.IDENTIFIED);
        assertThat(c.source()).isEqualTo(ContactSource.LINKEDIN);
    }

    @Test
    void companyContact_nullFullName_throws() {
        assertThatThrownBy(() ->
                new CompanyContact(null, "acme", null, null, null, null,
                        ContactSource.WEBSITE, ContactStatus.IDENTIFIED, null, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fullName");
    }

    @Test
    void companyContact_optionalFieldsAllNull_ok() {
        CompanyContact c = new CompanyContact(null, "acme", "John Smith", null,
                null, null, ContactSource.OTHER, ContactStatus.IDENTIFIED, null, NOW, NOW);
        assertThat(c.email()).isNull();
        assertThat(c.linkedinUrl()).isNull();
        assertThat(c.notes()).isNull();
    }

    // ── Enum values ─────────────────────────────────────────────────────────

    @Test
    void outreachPurpose_hasExpectedValues() {
        assertThat(OutreachPurpose.values()).containsExactlyInAnyOrder(
                OutreachPurpose.EMPLOYMENT, OutreachPurpose.SUBSCRIPTION);
    }

    @Test
    void outreachStatus_hasExpectedValues() {
        assertThat(OutreachStatus.values()).containsExactlyInAnyOrder(
                OutreachStatus.NOT_STARTED, OutreachStatus.IN_PROGRESS,
                OutreachStatus.RESPONDED, OutreachStatus.DECLINED, OutreachStatus.CLOSED);
    }

    @Test
    void contactStatus_hasExpectedValues() {
        assertThat(ContactStatus.values()).containsExactlyInAnyOrder(
                ContactStatus.IDENTIFIED, ContactStatus.REACHED_OUT,
                ContactStatus.RESPONDED, ContactStatus.MEETING_SCHEDULED, ContactStatus.DECLINED);
    }

    @Test
    void contactSource_hasExpectedValues() {
        assertThat(ContactSource.values()).containsExactlyInAnyOrder(
                ContactSource.LINKEDIN, ContactSource.REFERRAL,
                ContactSource.WEBSITE, ContactSource.OTHER);
    }
}
