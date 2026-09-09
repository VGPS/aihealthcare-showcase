package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.model.CannedPrompt;
import com.wgblackmon.aihealthcare.domain.model.DataParameter;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link CannedPromptAdapter} — round-trip persistence,
 * JSON parameter serialisation, feed-id filtering, and delete.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@DataJpaTest
class CannedPromptAdapterTest {

    @Autowired
    private EnterpriseDataPromptRepository repository;

    private CannedPromptAdapter adapter;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        adapter = new CannedPromptAdapter(repository, new ObjectMapper());
    }

    private CannedPrompt prompt(String id, String feedId, List<DataParameter> params) {
        return new CannedPrompt(id, "Label " + id, "Desc", feedId,
                "Find {{keyword}} articles", params, SubscriptionTier.SUBSCRIBER, true);
    }

    @Test
    void save_roundTrip_withEmptyParameters() {
        CannedPrompt saved = adapter.save(prompt("p1", "articles", List.of()));

        Optional<CannedPrompt> found = adapter.findById("p1");
        assertThat(found).isPresent();
        assertThat(found.get().promptId()).isEqualTo("p1");
        assertThat(found.get().label()).isEqualTo("Label p1");
        assertThat(found.get().feedId()).isEqualTo("articles");
        assertThat(found.get().minTier()).isEqualTo(SubscriptionTier.SUBSCRIBER);
        assertThat(found.get().parameters()).isEmpty();
    }

    @Test
    void save_roundTrip_withJsonParameters() {
        DataParameter param = new DataParameter(
                "keyword", "Topic keyword", "TEXT", true, null, List.of());
        CannedPrompt saved = adapter.save(prompt("p2", "articles", List.of(param)));

        Optional<CannedPrompt> found = adapter.findById("p2");
        assertThat(found).isPresent();
        assertThat(found.get().parameters()).hasSize(1);

        DataParameter roundTripped = found.get().parameters().get(0);
        assertThat(roundTripped.name()).isEqualTo("keyword");
        assertThat(roundTripped.label()).isEqualTo("Topic keyword");
        assertThat(roundTripped.type()).isEqualTo("TEXT");
        assertThat(roundTripped.required()).isTrue();
    }

    @Test
    void save_roundTrip_enumParameterWithAllowedValues() {
        DataParameter param = new DataParameter(
                "state", "State code", "ENUM", true, null, List.of("CA", "NY", "TX"));
        adapter.save(prompt("p3", "legislation", List.of(param)));

        CannedPrompt found = adapter.findById("p3").orElseThrow();
        DataParameter roundTripped = found.parameters().get(0);
        assertThat(roundTripped.type()).isEqualTo("ENUM");
        assertThat(roundTripped.allowedValues()).containsExactly("CA", "NY", "TX");
    }

    @Test
    void findAllActive_returnsOnlyActivePrompts() {
        adapter.save(prompt("p1", "articles", List.of()));
        adapter.save(new CannedPrompt("p2", "Inactive", "Desc", "articles",
                "template", List.of(), SubscriptionTier.SUBSCRIBER, false));

        List<CannedPrompt> active = adapter.findAllActive();
        assertThat(active).hasSize(1);
        assertThat(active.get(0).promptId()).isEqualTo("p1");
    }

    @Test
    void findByFeedId_filtersByFeedAndActive() {
        adapter.save(prompt("p1", "articles", List.of()));
        adapter.save(prompt("p2", "legislation", List.of()));
        adapter.save(prompt("p3", "articles", List.of()));

        List<CannedPrompt> articlePrompts = adapter.findByFeedId("articles");
        assertThat(articlePrompts).hasSize(2);
        assertThat(articlePrompts).extracting(CannedPrompt::feedId)
                .containsOnly("articles");
    }

    @Test
    void delete_removesPrompt() {
        adapter.save(prompt("p1", "articles", List.of()));
        assertThat(adapter.findById("p1")).isPresent();

        adapter.delete("p1");
        assertThat(adapter.findById("p1")).isEmpty();
    }

    @Test
    void findById_notFound_returnsEmpty() {
        assertThat(adapter.findById("nonexistent")).isEmpty();
    }
}
