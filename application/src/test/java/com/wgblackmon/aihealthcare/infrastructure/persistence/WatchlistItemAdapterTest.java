package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.WatchlistItem;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItemType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link WatchlistItemAdapter} using in-memory H2.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
@DataJpaTest
@Import(WatchlistItemAdapter.class)
class WatchlistItemAdapterTest {

    @Autowired
    private WatchlistItemAdapter adapter;

    @Test
    void saveAndFindByUser() {
        Instant now = Instant.now();
        WatchlistItem item = new WatchlistItem("w1", "user@test.com",
                WatchlistItemType.KEYWORD, "FDA", "FDA", now);

        adapter.save(item);
        List<WatchlistItem> result = adapter.findByUser("user@test.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).value()).isEqualTo("FDA");
        assertThat(result.get(0).itemType()).isEqualTo(WatchlistItemType.KEYWORD);
    }

    @Test
    void findByIdReturnsItem() {
        Instant now = Instant.now();
        WatchlistItem item = new WatchlistItem("w1", "user@test.com",
                WatchlistItemType.COMPANY, "tempus-ai", "Tempus AI", now);

        adapter.save(item);
        Optional<WatchlistItem> result = adapter.findById("w1");

        assertThat(result).isPresent();
        assertThat(result.get().label()).isEqualTo("Tempus AI");
    }

    @Test
    void deleteRemovesItem() {
        Instant now = Instant.now();
        WatchlistItem item = new WatchlistItem("w1", "user@test.com",
                WatchlistItemType.KEYWORD, "FDA", "FDA", now);

        adapter.save(item);
        adapter.delete("w1", "user@test.com");

        assertThat(adapter.findByUser("user@test.com")).isEmpty();
    }

    @Test
    void findAllReturnsAllItems() {
        Instant now = Instant.now();
        adapter.save(new WatchlistItem("w1", "user1@test.com",
                WatchlistItemType.KEYWORD, "FDA", "FDA", now));
        adapter.save(new WatchlistItem("w2", "user2@test.com",
                WatchlistItemType.COMPANY, "aidoc", "Aidoc", now));

        List<WatchlistItem> result = adapter.findAll();
        assertThat(result).hasSize(2);
    }

    @Test
    void findByUserReturnsEmptyForUnknownUser() {
        List<WatchlistItem> result = adapter.findByUser("nobody@test.com");
        assertThat(result).isEmpty();
    }
}
