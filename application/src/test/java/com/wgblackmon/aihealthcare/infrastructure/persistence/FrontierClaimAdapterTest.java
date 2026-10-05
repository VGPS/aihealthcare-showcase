package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.ClaimType;
import com.wgblackmon.aihealthcare.domain.model.ClaimVerdict;
import com.wgblackmon.aihealthcare.domain.model.FrontierClaim;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA round-trip tests for {@link FrontierClaimAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-05
 * @updated 2026-10-05
 */
@DataJpaTest
@Import(FrontierClaimAdapter.class)
class FrontierClaimAdapterTest {

    @Autowired
    private FrontierClaimAdapter adapter;

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    @Test
    void saveAndFindById_roundTrips() {
        FrontierClaim claim = buildClaim("OpenAI", "GPT-5 is best.",
                ClaimType.CAPABILITY_CLAIM, ClaimVerdict.MARKETING_HYPE);
        adapter.save(claim);

        Optional<FrontierClaim> found = adapter.findById(claim.claimId());
        assertThat(found).isPresent();
        assertThat(found.get().company()).isEqualTo("OpenAI");
        assertThat(found.get().claimType()).isEqualTo(ClaimType.CAPABILITY_CLAIM);
        assertThat(found.get().verdict()).isEqualTo(ClaimVerdict.MARKETING_HYPE);
    }

    @Test
    void saveAll_andFindAll_returnsAll() {
        FrontierClaim c1 = buildClaim("OpenAI", "GPT-5 is best.", ClaimType.CAPABILITY_CLAIM, ClaimVerdict.MARKETING_HYPE);
        FrontierClaim c2 = buildClaim("Anthropic", "Claude is safe.", ClaimType.SAFETY_CLAIM, ClaimVerdict.ALLEGED_UNVERIFIED);
        adapter.saveAll(List.of(c1, c2));

        List<FrontierClaim> all = adapter.findAll();
        assertThat(all).hasSize(2);
    }

    @Test
    void findByCompany_ignoresCase() {
        adapter.save(buildClaim("OpenAI", "GPT-5 is best.", ClaimType.CAPABILITY_CLAIM, ClaimVerdict.MARKETING_HYPE));
        adapter.save(buildClaim("Anthropic", "Claude is perfect.", ClaimType.CAPABILITY_CLAIM, ClaimVerdict.MARKETING_HYPE));

        List<FrontierClaim> result = adapter.findByCompany("openai");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).company()).isEqualTo("OpenAI");
    }

    @Test
    void nullableFields_persistAndRetrieveAsNull() {
        FrontierClaim claim = new FrontierClaim(
                UUID.randomUUID().toString(), "Meta", "Llama leads.",
                null, null, null,
                ClaimType.BENCHMARK_CLAIM, ClaimVerdict.ALLEGED_UNVERIFIED,
                null, null, NOW, null);
        adapter.save(claim);

        Optional<FrontierClaim> found = adapter.findById(claim.claimId());
        assertThat(found).isPresent();
        assertThat(found.get().claimDate()).isNull();
        assertThat(found.get().evidenceNotes()).isNull();
        assertThat(found.get().sourceUrl()).isNull();
        assertThat(found.get().lastReviewedAt()).isNull();
    }

    @Test
    void findByVerdict_returnsMatchingClaims() {
        adapter.save(buildClaim("OpenAI", "Claim A.", ClaimType.CAPABILITY_CLAIM, ClaimVerdict.MARKETING_HYPE));
        adapter.save(buildClaim("Google", "Claim B.", ClaimType.BENCHMARK_CLAIM, ClaimVerdict.EVIDENCE_BACKED));

        List<FrontierClaim> hyped = adapter.findByVerdict("MARKETING_HYPE");
        assertThat(hyped).hasSize(1);
        assertThat(hyped.get(0).company()).isEqualTo("OpenAI");
    }

    @Test
    void findById_missingId_returnsEmpty() {
        Optional<FrontierClaim> result = adapter.findById("nonexistent-id");
        assertThat(result).isEmpty();
    }

    private FrontierClaim buildClaim(String company, String text,
                                      ClaimType type, ClaimVerdict verdict) {
        return new FrontierClaim(
                UUID.randomUUID().toString(), company, text,
                LocalDate.of(2026, 10, 1), "https://example.com", "Example",
                type, verdict,
                "Evidence rationale.", "art-1", NOW, null);
    }
}
