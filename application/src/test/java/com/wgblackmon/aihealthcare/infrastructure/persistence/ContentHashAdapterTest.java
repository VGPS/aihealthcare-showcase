package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.port.outbound.ContentHashPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA slice tests for {@link ContentHashAdapter}.
 *
 * <p>Verifies hash storage, retrieval, and upsert behaviour using
 * the {@code page_content_hashes} table on an in-memory H2 database.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-19
 * @updated 2026-04-19
 */
@DataJpaTest
class ContentHashAdapterTest {

    @Autowired
    private PageContentHashRepository repository;

    private ContentHashPort adapter;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        adapter = new ContentHashAdapter(repository);
    }

    @Test
    void getHash_unknownUrl_returnsNull() {
        String result = adapter.getHash("https://example.com/unknown");

        assertThat(result).isNull();
    }

    @Test
    void saveHash_thenGetHash_returnsStoredHash() {
        adapter.saveHash("https://example.com/page", "abc123def456");

        String result = adapter.getHash("https://example.com/page");

        assertThat(result).isEqualTo("abc123def456");
    }

    @Test
    void saveHash_twice_overwritesPreviousHash() {
        adapter.saveHash("https://example.com/page", "hash-v1");
        adapter.saveHash("https://example.com/page", "hash-v2");

        String result = adapter.getHash("https://example.com/page");

        assertThat(result).isEqualTo("hash-v2");
    }

    @Test
    void saveHash_multipleUrls_trackedIndependently() {
        adapter.saveHash("https://example.com/page-a", "hash-a");
        adapter.saveHash("https://example.com/page-b", "hash-b");

        assertThat(adapter.getHash("https://example.com/page-a")).isEqualTo("hash-a");
        assertThat(adapter.getHash("https://example.com/page-b")).isEqualTo("hash-b");
    }
}
