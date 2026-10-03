package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.CompanyOutreach;
import com.wgblackmon.aihealthcare.domain.model.OutreachPurpose;
import com.wgblackmon.aihealthcare.domain.model.OutreachStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link CompanyOutreachAdapter} using an in-memory database.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-03
 * @updated 2026-10-03
 */
@DataJpaTest
@Import(CompanyOutreachAdapter.class)
class CompanyOutreachAdapterTest {

    @Autowired
    private CompanyOutreachAdapter adapter;

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

    @Test
    void saveAndFindById() {
        CompanyOutreach o = new CompanyOutreach(null, "microsoft",
                OutreachPurpose.EMPLOYMENT, OutreachStatus.NOT_STARTED,
                null, "Notes here", NOW, NOW);

        CompanyOutreach saved = adapter.save(o);
        Optional<CompanyOutreach> found = adapter.findById(saved.id());

        assertThat(found).isPresent();
        assertThat(found.get().slug()).isEqualTo("microsoft");
        assertThat(found.get().purpose()).isEqualTo(OutreachPurpose.EMPLOYMENT);
        assertThat(found.get().status()).isEqualTo(OutreachStatus.NOT_STARTED);
        assertThat(found.get().notes()).isEqualTo("Notes here");
    }

    @Test
    void findBySlugAndPurpose_returnsPresentWhenExists() {
        CompanyOutreach o = new CompanyOutreach(null, "acme",
                OutreachPurpose.SUBSCRIPTION, OutreachStatus.IN_PROGRESS,
                null, null, NOW, NOW);
        adapter.save(o);

        Optional<CompanyOutreach> result = adapter.findBySlugAndPurpose("acme", OutreachPurpose.SUBSCRIPTION);
        assertThat(result).isPresent();
        assertThat(result.get().purpose()).isEqualTo(OutreachPurpose.SUBSCRIPTION);
    }

    @Test
    void findBySlug_returnsMultipleRows() {
        adapter.save(new CompanyOutreach(null, "bigco", OutreachPurpose.EMPLOYMENT,
                OutreachStatus.NOT_STARTED, null, null, NOW, NOW));
        adapter.save(new CompanyOutreach(null, "bigco", OutreachPurpose.SUBSCRIPTION,
                OutreachStatus.NOT_STARTED, null, null, NOW, NOW));
        adapter.save(new CompanyOutreach(null, "other", OutreachPurpose.EMPLOYMENT,
                OutreachStatus.NOT_STARTED, null, null, NOW, NOW));

        List<CompanyOutreach> result = adapter.findBySlug("bigco");
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> r.slug().equals("bigco"));
    }

    @Test
    void deleteById_removesRecord() {
        CompanyOutreach saved = adapter.save(new CompanyOutreach(null, "del-me",
                OutreachPurpose.EMPLOYMENT, OutreachStatus.NOT_STARTED,
                null, null, NOW, NOW));

        adapter.deleteById(saved.id());
        assertThat(adapter.findById(saved.id())).isEmpty();
    }

    @Test
    void findAll_returnsAllRows() {
        adapter.save(new CompanyOutreach(null, "co-a", OutreachPurpose.EMPLOYMENT,
                OutreachStatus.NOT_STARTED, null, null, NOW, NOW));
        adapter.save(new CompanyOutreach(null, "co-b", OutreachPurpose.SUBSCRIPTION,
                OutreachStatus.IN_PROGRESS, null, null, NOW, NOW));

        assertThat(adapter.findAll()).hasSizeGreaterThanOrEqualTo(2);
    }
}
