package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.AnalystNote;
import com.wgblackmon.aihealthcare.domain.model.NoteTargetType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link AnalystNoteAdapter} using in-memory H2.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@DataJpaTest
@Import(AnalystNoteAdapter.class)
class AnalystNoteAdapterTest {

    @Autowired
    private AnalystNoteAdapter adapter;

    private static final Instant NOW = Instant.now();

    @Test
    void saveAndFindByUser() {
        AnalystNote note = new AnalystNote("n1", "user@test.com",
                NoteTargetType.COMPANY, "tempus-ai", "Tempus AI",
                "Great genomics company", NOW, NOW);

        adapter.save(note);
        List<AnalystNote> result = adapter.findByUser("user@test.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).content()).isEqualTo("Great genomics company");
        assertThat(result.get(0).targetType()).isEqualTo(NoteTargetType.COMPANY);
    }

    @Test
    void findByUserAndTarget() {
        adapter.save(new AnalystNote("n1", "user@test.com",
                NoteTargetType.COMPANY, "tempus-ai", "Tempus AI",
                "Company note", NOW, NOW));
        adapter.save(new AnalystNote("n2", "user@test.com",
                NoteTargetType.WIKI_PAGE, "fda-clearance", "FDA Clearance",
                "Wiki note", NOW, NOW));

        List<AnalystNote> companyNotes = adapter.findByUserAndTarget(
                "user@test.com", NoteTargetType.COMPANY, "tempus-ai");
        List<AnalystNote> wikiNotes = adapter.findByUserAndTarget(
                "user@test.com", NoteTargetType.WIKI_PAGE, "fda-clearance");

        assertThat(companyNotes).hasSize(1);
        assertThat(companyNotes.get(0).content()).isEqualTo("Company note");
        assertThat(wikiNotes).hasSize(1);
        assertThat(wikiNotes.get(0).content()).isEqualTo("Wiki note");
    }

    @Test
    void findByIdReturnsNote() {
        adapter.save(new AnalystNote("n1", "user@test.com",
                NoteTargetType.ARTICLE, "art-123", "Some Article",
                "Article analysis", NOW, NOW));

        Optional<AnalystNote> result = adapter.findById("n1");

        assertThat(result).isPresent();
        assertThat(result.get().targetLabel()).isEqualTo("Some Article");
    }

    @Test
    void deleteRemovesNote() {
        adapter.save(new AnalystNote("n1", "user@test.com",
                NoteTargetType.COMPANY, "tempus-ai", "Tempus AI",
                "To be deleted", NOW, NOW));

        adapter.delete("n1", "user@test.com");

        assertThat(adapter.findByUser("user@test.com")).isEmpty();
    }

    @Test
    void findByUserReturnsEmptyForUnknownUser() {
        List<AnalystNote> result = adapter.findByUser("nobody@test.com");
        assertThat(result).isEmpty();
    }
}
