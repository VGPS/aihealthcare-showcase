package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.CompanyContact;
import com.wgblackmon.aihealthcare.domain.model.ContactSource;
import com.wgblackmon.aihealthcare.domain.model.ContactStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link CompanyContactAdapter} using an in-memory database.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-03
 * @updated 2026-10-03
 */
@DataJpaTest
@Import(CompanyContactAdapter.class)
class CompanyContactAdapterTest {

    @Autowired
    private CompanyContactAdapter adapter;

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

    private CompanyContact contact(String slug, String name, ContactSource source) {
        return new CompanyContact(null, slug, name, "Engineer", "jane@example.com",
                "https://linkedin.com/in/jane", source, ContactStatus.IDENTIFIED, null, NOW, NOW);
    }

    @Test
    void saveAndFindById() {
        CompanyContact saved = adapter.save(contact("microsoft", "Jane Doe", ContactSource.LINKEDIN));
        Optional<CompanyContact> found = adapter.findById(saved.id());

        assertThat(found).isPresent();
        assertThat(found.get().fullName()).isEqualTo("Jane Doe");
        assertThat(found.get().source()).isEqualTo(ContactSource.LINKEDIN);
        assertThat(found.get().status()).isEqualTo(ContactStatus.IDENTIFIED);
    }

    @Test
    void findBySlug_returnsContactsForSlug() {
        adapter.save(contact("acme", "Alice", ContactSource.LINKEDIN));
        adapter.save(contact("acme", "Bob", ContactSource.REFERRAL));
        adapter.save(contact("other", "Charlie", ContactSource.WEBSITE));

        List<CompanyContact> result = adapter.findBySlug("acme");
        assertThat(result).hasSize(2);
        assertThat(result).extracting(CompanyContact::fullName).containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void deleteById_removesRecord() {
        CompanyContact saved = adapter.save(contact("del-co", "To Delete", ContactSource.OTHER));
        adapter.deleteById(saved.id());
        assertThat(adapter.findById(saved.id())).isEmpty();
    }

    @Test
    void save_updatesExistingRecord() {
        CompanyContact original = adapter.save(contact("bigco", "Sam", ContactSource.LINKEDIN));
        CompanyContact updated = new CompanyContact(
                original.id(), original.slug(), original.fullName(), original.jobTitle(),
                "new@email.com", original.linkedinUrl(), original.source(),
                ContactStatus.REACHED_OUT, original.notes(), original.createdAt(), Instant.now());

        CompanyContact result = adapter.save(updated);
        assertThat(result.email()).isEqualTo("new@email.com");
        assertThat(result.status()).isEqualTo(ContactStatus.REACHED_OUT);
    }
}
