package com.wgblackmon.aihealthcare.domain.marketanalysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PeerGroupTagger}.
 *
 * <p>Verifies keyword-to-peer-group mapping for all five named groups,
 * the {@link PeerGroup#OTHER} fallback, case-insensitive matching,
 * and the already-tagged pass-through behaviour.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
class PeerGroupTaggerTest {

    private PeerGroupTagger tagger;

    @BeforeEach
    void setUp() {
        tagger = new PeerGroupTagger();
    }

    // ─── tag() — keyword matching per group ──────────────────────────────────

    @Test
    void tag_doximity_returnsAiScribeDocumentation() {
        assertThat(tagger.tag("Doximity Inc.", "DOCS"))
                .isEqualTo(PeerGroup.AI_SCRIBE_DOCUMENTATION);
    }

    @Test
    void tag_scribeKeyword_returnsAiScribeDocumentation() {
        assertThat(tagger.tag("Ambient Scribe Platform Co.", null))
                .isEqualTo(PeerGroup.AI_SCRIBE_DOCUMENTATION);
    }

    @Test
    void tag_aidoc_returnsDiagnosticImagingAi() {
        assertThat(tagger.tag("Aidoc Medical", null))
                .isEqualTo(PeerGroup.DIAGNOSTIC_IMAGING_AI);
    }

    @Test
    void tag_radiologyKeyword_returnsDiagnosticImagingAi() {
        assertThat(tagger.tag("AI Radiology Solutions", null))
                .isEqualTo(PeerGroup.DIAGNOSTIC_IMAGING_AI);
    }

    @Test
    void tag_recursion_returnsDrugDiscoveryAi() {
        assertThat(tagger.tag("Recursion Pharmaceuticals", "RXRX"))
                .isEqualTo(PeerGroup.DRUG_DISCOVERY_AI);
    }

    @Test
    void tag_digitalTherapeuticKeyword_returnsDigitalTherapeutics() {
        assertThat(tagger.tag("Digital Therapeutic Platform", null))
                .isEqualTo(PeerGroup.DIGITAL_THERAPEUTICS);
    }

    @Test
    void tag_populationHealth_returnsValueBasedCare() {
        assertThat(tagger.tag("Population Health Management Inc.", null))
                .isEqualTo(PeerGroup.VALUE_BASED_CARE_PLATFORM);
    }

    @Test
    void tag_unknownCompany_returnsOther() {
        assertThat(tagger.tag("Generic Biotech Corp.", "GBC"))
                .isEqualTo(PeerGroup.OTHER);
    }

    @Test
    void tag_caseInsensitive_matches() {
        assertThat(tagger.tag("DOXIMITY CORPORATION", "DOCS"))
                .isEqualTo(PeerGroup.AI_SCRIBE_DOCUMENTATION);
    }

    // ─── tagCompany() — already-tagged pass-through ───────────────────────────

    @Test
    void tagCompany_alreadyTaggedNonOther_passesThrough() {
        AffectedCompany c = new AffectedCompany(
                "Unknown Corp", null, "deal counterparty", PeerGroup.DRUG_DISCOVERY_AI);

        AffectedCompany result = tagger.tagCompany(c);

        assertThat(result).isSameAs(c);
        assertThat(result.peerGroup()).isEqualTo(PeerGroup.DRUG_DISCOVERY_AI);
    }

    @Test
    void tagCompany_nullPeerGroup_setsTaggedGroup() {
        AffectedCompany c = new AffectedCompany("Aidoc", null, "earnings subject", null);

        AffectedCompany result = tagger.tagCompany(c);

        assertThat(result.peerGroup()).isEqualTo(PeerGroup.DIAGNOSTIC_IMAGING_AI);
        assertThat(result.name()).isEqualTo("Aidoc");
        assertThat(result.role()).isEqualTo("earnings subject");
    }

    // ─── tagEntry() ──────────────────────────────────────────────────────────

    @Test
    void tagEntry_retagsAllCompanies() {
        AffectedCompany c1 = new AffectedCompany("Doximity", "DOCS", "earnings subject", null);
        AffectedCompany c2 = new AffectedCompany("Unknown Corp", null, "peer", null);

        MarketDigestEntry entry = entryWithCompanies(c1, c2);
        MarketDigestEntry tagged = tagger.tagEntry(entry);

        assertThat(tagged.affectedCompanies()).hasSize(2);
        assertThat(tagged.affectedCompanies().get(0).peerGroup())
                .isEqualTo(PeerGroup.AI_SCRIBE_DOCUMENTATION);
        assertThat(tagged.affectedCompanies().get(1).peerGroup())
                .isEqualTo(PeerGroup.OTHER);
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private static MarketDigestEntry entryWithCompanies(AffectedCompany... companies) {
        java.util.List<AffectedCompany> list = new java.util.ArrayList<>();
        for (AffectedCompany c : companies) {
            list.add(c);
        }
        MarketNewsItem item = new MarketNewsItem(
                "Headline", "Summary.", java.util.List.of(),
                java.time.Instant.now(), NewsCategory.EARNINGS, null);
        return new MarketDigestEntry(
                item, java.util.List.of(), FactClassification.CONFIRMED,
                new MarketImpactRank(2), list);
    }
}
