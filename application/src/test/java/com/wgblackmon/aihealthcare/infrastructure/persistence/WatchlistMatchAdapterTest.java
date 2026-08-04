package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.WatchlistItem;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItemType;
import com.wgblackmon.aihealthcare.domain.model.WatchlistMatch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link WatchlistMatchAdapter} using in-memory H2.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
@DataJpaTest
@Import({WatchlistMatchAdapter.class, WatchlistItemAdapter.class})
class WatchlistMatchAdapterTest {

    @Autowired
    private WatchlistMatchAdapter matchAdapter;

    @Autowired
    private WatchlistItemAdapter itemAdapter;

    @Test
    void saveAndFindByUser() {
        Instant now = Instant.now();
        WatchlistItem item = new WatchlistItem("w1", "user@test.com",
                WatchlistItemType.KEYWORD, "FDA", "FDA", now);
        itemAdapter.save(item);

        WatchlistMatch match = new WatchlistMatch("m1", "w1", "a1", now, "FDA cleared...");
        matchAdapter.save(match);

        List<WatchlistMatch> result = matchAdapter.findByUser("user@test.com", 10);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).articleId()).isEqualTo("a1");
        assertThat(result.get(0).snippet()).isEqualTo("FDA cleared...");
    }

    @Test
    void existsByItemAndArticle() {
        Instant now = Instant.now();
        WatchlistItem item = new WatchlistItem("w1", "user@test.com",
                WatchlistItemType.KEYWORD, "FDA", "FDA", now);
        itemAdapter.save(item);

        WatchlistMatch match = new WatchlistMatch("m1", "w1", "a1", now, null);
        matchAdapter.save(match);

        assertThat(matchAdapter.existsByItemAndArticle("w1", "a1")).isTrue();
        assertThat(matchAdapter.existsByItemAndArticle("w1", "a999")).isFalse();
    }

    @Test
    void saveAllPersistsMultipleMatches() {
        Instant now = Instant.now();
        WatchlistItem item = new WatchlistItem("w1", "user@test.com",
                WatchlistItemType.KEYWORD, "FDA", "FDA", now);
        itemAdapter.save(item);

        List<WatchlistMatch> matches = List.of(
                new WatchlistMatch("m1", "w1", "a1", now, "snippet1"),
                new WatchlistMatch("m2", "w1", "a2", now, "snippet2")
        );
        matchAdapter.saveAll(matches);

        List<WatchlistMatch> result = matchAdapter.findByItem("w1", 10);
        assertThat(result).hasSize(2);
    }

    @Test
    void findByUserReturnsEmptyWhenNoItems() {
        List<WatchlistMatch> result = matchAdapter.findByUser("nobody@test.com", 10);
        assertThat(result).isEmpty();
    }

    @Test
    void findByUserSince_returnsOnlyRecentMatches() {
        Instant old = Instant.parse("2026-07-01T00:00:00Z");
        Instant recent = Instant.parse("2026-08-04T12:00:00Z");
        Instant cutoff = Instant.parse("2026-08-01T00:00:00Z");

        WatchlistItem item = new WatchlistItem("w1", "user@test.com",
                WatchlistItemType.KEYWORD, "FDA", "FDA", old);
        itemAdapter.save(item);

        matchAdapter.save(new WatchlistMatch("m1", "w1", "a1", old, "old match"));
        matchAdapter.save(new WatchlistMatch("m2", "w1", "a2", recent, "recent match"));

        List<WatchlistMatch> result = matchAdapter.findByUserSince("user@test.com", cutoff);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).matchId()).isEqualTo("m2");
    }
}
