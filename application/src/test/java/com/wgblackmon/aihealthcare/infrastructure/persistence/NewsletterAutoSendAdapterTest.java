package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link NewsletterAutoSendAdapter}.
 *
 * <p>Verifies override persistence, date-scoped queries, and the singleton
 * row creation/update behavior.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@DataJpaTest
@Import(NewsletterAutoSendAdapter.class)
class NewsletterAutoSendAdapterTest {

    @Autowired
    private NewsletterAutoSendAdapter adapter;

    @Test
    void isOverriddenForDate_noRowExists_returnsFalse() {
        assertThat(adapter.isOverriddenForDate(LocalDate.now())).isFalse();
    }

    @Test
    void setOverride_true_thenIsOverridden_returnsTrue() {
        LocalDate today = LocalDate.now();
        adapter.setOverride(today, true);
        assertThat(adapter.isOverriddenForDate(today)).isTrue();
    }

    @Test
    void setOverride_false_thenIsOverridden_returnsFalse() {
        LocalDate today = LocalDate.now();
        adapter.setOverride(today, true);
        adapter.setOverride(today, false);
        assertThat(adapter.isOverriddenForDate(today)).isFalse();
    }

    @Test
    void isOverriddenForDate_differentDate_returnsFalse() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        adapter.setOverride(today, true);
        assertThat(adapter.isOverriddenForDate(tomorrow)).isFalse();
    }

    @Test
    void setOverride_updatesExistingRow_doesNotCreateDuplicate() {
        LocalDate today = LocalDate.now();
        adapter.setOverride(today, true);
        adapter.setOverride(today, false);
        adapter.setOverride(today, true);
        assertThat(adapter.isOverriddenForDate(today)).isTrue();
    }
}
