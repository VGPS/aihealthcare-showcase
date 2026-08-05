package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.WebhookChannel;
import com.wgblackmon.aihealthcare.domain.model.WebhookChannelType;
import com.wgblackmon.aihealthcare.domain.model.WebhookEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @DataJpaTest} integration tests for {@link WebhookChannelAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@DataJpaTest
@Import(WebhookChannelAdapter.class)
class WebhookChannelAdapterTest {

    @Autowired
    private WebhookChannelAdapter adapter;

    private WebhookChannel createChannel(String id, String owner, String name,
                                          WebhookChannelType type,
                                          Set<WebhookEventType> events) {
        return new WebhookChannel(id, owner, name, "https://hooks.example.com/" + id,
                type, true, events, Instant.now());
    }

    @Test
    @DisplayName("save and findById round-trip")
    void save_findById_roundTrip() {
        WebhookChannel channel = createChannel("ch1", "user@test.com", "Slack Alerts",
                WebhookChannelType.SLACK, Set.of(WebhookEventType.WATCHLIST_MATCH));
        adapter.save(channel);

        Optional<WebhookChannel> found = adapter.findById("ch1");
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("Slack Alerts");
        assertThat(found.get().channelType()).isEqualTo(WebhookChannelType.SLACK);
        assertThat(found.get().subscribedEvents()).contains(WebhookEventType.WATCHLIST_MATCH);
    }

    @Test
    @DisplayName("findByOwnerEmail returns only owned channels")
    void findByOwnerEmail_filtersCorrectly() {
        adapter.save(createChannel("ch1", "alice@test.com", "Ch A",
                WebhookChannelType.SLACK, Set.of(WebhookEventType.WATCHLIST_MATCH)));
        adapter.save(createChannel("ch2", "alice@test.com", "Ch B",
                WebhookChannelType.TEAMS, Set.of(WebhookEventType.REGULATORY_ALERT)));
        adapter.save(createChannel("ch3", "bob@test.com", "Ch C",
                WebhookChannelType.CUSTOM, Set.of(WebhookEventType.TREND_ALERT)));

        List<WebhookChannel> aliceChannels = adapter.findByOwnerEmail("alice@test.com");
        assertThat(aliceChannels).hasSize(2);
    }

    @Test
    @DisplayName("findActiveByEventType returns matching channels")
    void findActiveByEventType_filtersCorrectly() {
        adapter.save(createChannel("ch1", "user@test.com", "Watchlist",
                WebhookChannelType.SLACK, Set.of(WebhookEventType.WATCHLIST_MATCH)));
        adapter.save(createChannel("ch2", "user@test.com", "Regulatory",
                WebhookChannelType.TEAMS, Set.of(WebhookEventType.REGULATORY_ALERT)));
        adapter.save(createChannel("ch3", "user@test.com", "All Events",
                WebhookChannelType.CUSTOM,
                Set.of(WebhookEventType.WATCHLIST_MATCH, WebhookEventType.REGULATORY_ALERT)));

        List<WebhookChannel> watchlistChannels = adapter.findActiveByEventType(WebhookEventType.WATCHLIST_MATCH);
        assertThat(watchlistChannels).hasSize(2);

        List<WebhookChannel> regChannels = adapter.findActiveByEventType(WebhookEventType.REGULATORY_ALERT);
        assertThat(regChannels).hasSize(2);

        List<WebhookChannel> trendChannels = adapter.findActiveByEventType(WebhookEventType.TREND_ALERT);
        assertThat(trendChannels).isEmpty();
    }

    @Test
    @DisplayName("deleteById removes channel")
    void deleteById_removesChannel() {
        adapter.save(createChannel("ch1", "user@test.com", "Temp",
                WebhookChannelType.SLACK, Set.of(WebhookEventType.WATCHLIST_MATCH)));
        assertThat(adapter.findById("ch1")).isPresent();

        adapter.deleteById("ch1");
        assertThat(adapter.findById("ch1")).isEmpty();
    }

    @Test
    @DisplayName("multiple events stored as pipe-delimited and restored")
    void multipleEvents_roundTrip() {
        Set<WebhookEventType> allEvents = Set.of(
                WebhookEventType.WATCHLIST_MATCH, WebhookEventType.REGULATORY_ALERT,
                WebhookEventType.TREND_ALERT, WebhookEventType.PIPELINE_COMPLETE);
        adapter.save(createChannel("ch1", "user@test.com", "All",
                WebhookChannelType.CUSTOM, allEvents));

        Optional<WebhookChannel> found = adapter.findById("ch1");
        assertThat(found).isPresent();
        assertThat(found.get().subscribedEvents()).hasSize(4);
    }
}
