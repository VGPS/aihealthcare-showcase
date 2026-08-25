package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.DocumentRecord;
import com.wgblackmon.aihealthcare.domain.model.DocumentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link DocumentLibraryAdapter} using the test database.
 *
 * <p>Verifies CRUD operations and status transitions against the
 * {@code document_library} table managed by JPA DDL auto.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-25
 * @updated 2026-08-25
 */
@DataJpaTest
@Import(DocumentLibraryAdapter.class)
class DocumentLibraryAdapterTest {

    @Autowired
    private DocumentLibraryAdapter adapter;

    private DocumentRecord sample(String docId) {
        return new DocumentRecord(docId, "paper.pdf", "Dr Smith", "AI Healthcare Legal",
                "Jane Requester", "jane@example.com",
                Instant.now(), 0, null, DocumentStatus.UPLOADED, null);
    }

    @Test
    void saveAndFindAll() {
        adapter.save(sample("doc-1"));
        adapter.save(sample("doc-2"));

        List<DocumentRecord> result = adapter.findAll();
        assertThat(result).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void findByIdPresent() {
        adapter.save(sample("doc-xyz"));

        Optional<DocumentRecord> result = adapter.findById("doc-xyz");
        assertThat(result).isPresent();
        assertThat(result.get().filename()).isEqualTo("paper.pdf");
        assertThat(result.get().topic()).isEqualTo("AI Healthcare Legal");
        assertThat(result.get().requesterName()).isEqualTo("Jane Requester");
        assertThat(result.get().requesterEmail()).isEqualTo("jane@example.com");
        assertThat(result.get().status()).isEqualTo(DocumentStatus.UPLOADED);
    }

    @Test
    void findByIdNotFound() {
        Optional<DocumentRecord> result = adapter.findById("no-such-doc");
        assertThat(result).isEmpty();
    }

    @Test
    void updateStatusToIndexed() {
        adapter.save(sample("doc-upd"));

        adapter.updateStatus("doc-upd", DocumentStatus.INDEXED, 15, null, null);

        Optional<DocumentRecord> result = adapter.findById("doc-upd");
        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(result.get().chunkCount()).isEqualTo(15);
        assertThat(result.get().wikiPageSlug()).isNull();
    }

    @Test
    void updateStatusToWikiCompiled_setsSlug() {
        adapter.save(sample("doc-wiki"));

        adapter.updateStatus("doc-wiki", DocumentStatus.WIKI_COMPILED, 20, "ai-in-radiology", null);

        Optional<DocumentRecord> result = adapter.findById("doc-wiki");
        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(DocumentStatus.WIKI_COMPILED);
        assertThat(result.get().wikiPageSlug()).isEqualTo("ai-in-radiology");
        assertThat(result.get().chunkCount()).isEqualTo(20);
    }
}
