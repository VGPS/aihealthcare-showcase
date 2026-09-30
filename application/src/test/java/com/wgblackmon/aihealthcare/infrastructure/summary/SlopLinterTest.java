package com.wgblackmon.aihealthcare.infrastructure.summary;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SlopLinter}.
 *
 * <p>Each rule has a positive case (text that should trigger it) and a negative case
 * (clean text that should not).  The house-style GOOD sample must score >= 90;
 * the BAD sample must produce at least one BLOCK finding.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-29
 * @updated 2026-09-29
 */
class SlopLinterTest {

    private static final List<String> BANNED = List.of(
            "delve", "tapestry", "navigate the complexities",
            "in today's rapidly evolving", "ever-evolving",
            "game-changer", "game-changing", "revolutionize", "revolutionizing",
            "transformative", "paradigm shift", "unlock the potential",
            "it's important to note", "it is worth noting",
            "in conclusion", "in summary", "overall", "ultimately",
            "plays a crucial role", "plays a pivotal role", "crucial", "pivotal",
            "vital", "robust", "seamless", "seamlessly",
            "cutting-edge", "state-of-the-art", "leverage", "synergy",
            "holistic", "multifaceted", "a testament to", "stands as", "serves as a",
            "moreover", "furthermore", "additionally",
            "embark", "foster"
    );

    private static final String GOOD_SAMPLE =
            "Mayo Clinic cut prior-authorization turnaround from 5.2 to 1.9 days across " +
            "3 service lines after deploying an AI triage tool, according to its Q2 operations " +
            "report [S2]. The vendor, Cohere Health, has not published error rates [S2]. " +
            "Buyers should ask for denial-overturn data before signing.";

    private static final String BAD_SAMPLE =
            "In today's rapidly evolving healthcare landscape, AI is playing a pivotal role " +
            "in transforming prior authorization. Notably, leading institutions are leveraging " +
            "cutting-edge solutions to unlock efficiency, reduce burden, and improve outcomes. " +
            "Overall, the future looks promising.";

    private SlopLinter linter;

    @BeforeEach
    void setUp() {
        linter = new SlopLinter(BANNED);
    }

    // -------------------------------------------------------------------------
    // Rule 1 — banned phrases
    // -------------------------------------------------------------------------

    @Test
    void bannedPhrase_detected_asBlock() {
        String text = "This paradigm shift in healthcare AI will revolutionize care delivery.";
        SlopLinter.LintResult result = linter.lint(text, Set.of());
        assertThat(result.findings()).anyMatch(f ->
                f.rule().equals("banned-phrase") && f.severity() == SlopLinter.Severity.BLOCK);
    }

    @Test
    void cleanText_noBannedPhrases() {
        String text = "Nuance cut transcription time by 40% across 12 hospital sites in Q1 [S1].";
        SlopLinter.LintResult result = linter.lint(text, Set.of("S1"));
        assertThat(result.findings()).noneMatch(f -> f.rule().equals("banned-phrase"));
    }

    // -------------------------------------------------------------------------
    // Rule 2 — too much bold
    // -------------------------------------------------------------------------

    @Test
    void twoBoldSpans_triggersWarn() {
        String text = "**Key finding:** revenue up 20%. **Second bold:** costs down. No citations here unfortunately.";
        SlopLinter.LintResult result = linter.lint(text, Set.of());
        assertThat(result.findings()).anyMatch(f ->
                f.rule().equals("too-much-bold") && f.severity() == SlopLinter.Severity.WARN);
    }

    @Test
    void oneBoldSpan_noFinding() {
        String text = "**Revenue rose 20% [S1].** Costs fell 5% [S2].";
        SlopLinter.LintResult result = linter.lint(text, Set.of("S1", "S2"));
        assertThat(result.findings()).noneMatch(f -> f.rule().equals("too-much-bold"));
    }

    // -------------------------------------------------------------------------
    // Rule 3 — bullet-heavy
    // -------------------------------------------------------------------------

    @Test
    void bulletHeavy_whenFourOrMoreBulletsExceedHalfSentences() {
        // 4 bullets, 1 prose sentence → bullets dominate
        String text = "Summary:\n- Point one costs $10M [S1]\n- Point two saves 30% [S2]\n" +
                      "- Point three adds 5 sites [S3]\n- Point four cuts errors [S3]\nDone.";
        SlopLinter.LintResult result = linter.lint(text, Set.of("S1", "S2", "S3"));
        assertThat(result.findings()).anyMatch(f -> f.rule().equals("bullet-heavy"));
    }

    @Test
    void twoBullets_noFinding() {
        // 2 bullets is a legitimate list; should not trigger the rule
        String text = "Revenue grew 15% [S1]. Two vendors contributed:\n- Vendor A [S1]\n- Vendor B [S2]\nCosts remain flat [S2].";
        SlopLinter.LintResult result = linter.lint(text, Set.of("S1", "S2"));
        assertThat(result.findings()).noneMatch(f -> f.rule().equals("bullet-heavy"));
    }

    // -------------------------------------------------------------------------
    // Rule 4 — uncited factual sentence
    // -------------------------------------------------------------------------

    @Test
    void uncitedFactualSentence_isBlocked() {
        String text = "Revenue grew 25% in Q3. Operating costs fell by $4M.";
        SlopLinter.LintResult result = linter.lint(text, Set.of());
        assertThat(result.findings()).anyMatch(f ->
                f.rule().equals("uncited-claim") && f.severity() == SlopLinter.Severity.BLOCK);
    }

    @Test
    void citedFactualSentence_noUncitedFinding() {
        String text = "Revenue grew 25% in Q3 [S1]. Operating costs fell by $4M [S1].";
        SlopLinter.LintResult result = linter.lint(text, Set.of("S1"));
        assertThat(result.findings()).noneMatch(f -> f.rule().equals("uncited-claim"));
    }

    @Test
    void pureFramingText_notFlaggedAsUncited() {
        String text = "This report covers AI adoption trends in healthcare.";
        SlopLinter.LintResult result = linter.lint(text, Set.of());
        assertThat(result.findings()).noneMatch(f -> f.rule().equals("uncited-claim"));
    }

    // -------------------------------------------------------------------------
    // Rule 5 — bad citation IDs
    // -------------------------------------------------------------------------

    @Test
    void unknownSourceId_isBlocked() {
        String text = "Revenue grew 25% in Q3 [S99].";
        SlopLinter.LintResult result = linter.lint(text, Set.of("S1", "S2"));
        assertThat(result.findings()).anyMatch(f ->
                f.rule().equals("bad-citation") && f.severity() == SlopLinter.Severity.BLOCK);
    }

    @Test
    void validSourceId_noFinding() {
        String text = "Revenue grew 25% in Q3 [S1].";
        SlopLinter.LintResult result = linter.lint(text, Set.of("S1"));
        assertThat(result.findings()).noneMatch(f -> f.rule().equals("bad-citation"));
    }

    // -------------------------------------------------------------------------
    // Rule 6 — filler openers
    // -------------------------------------------------------------------------

    @Test
    void fillerOpener_detected() {
        String text = "Revenue rose 10% [S1]. Moreover, costs fell [S1].";
        SlopLinter.LintResult result = linter.lint(text, Set.of("S1"));
        assertThat(result.findings()).anyMatch(f -> f.rule().equals("filler-opener"));
    }

    @Test
    void noFillerOpener_noFinding() {
        String text = "Revenue rose 10% [S1]. Costs fell 5% simultaneously [S1].";
        SlopLinter.LintResult result = linter.lint(text, Set.of("S1"));
        assertThat(result.findings()).noneMatch(f -> f.rule().equals("filler-opener"));
    }

    // -------------------------------------------------------------------------
    // Rule 7 — reflexive triplets
    // -------------------------------------------------------------------------

    @Test
    void multipleTriplets_detected() {
        String text = "The system cuts cost, time, and errors [S1]. It improves speed, quality, and safety [S1].";
        SlopLinter.LintResult result = linter.lint(text, Set.of("S1"));
        assertThat(result.findings()).anyMatch(f -> f.rule().equals("reflexive-triplet"));
    }

    // -------------------------------------------------------------------------
    // Rule 9 — restating conclusion
    // -------------------------------------------------------------------------

    @Test
    void inConclusionPhrase_detected() {
        String text = "Revenue rose 20% in Q3 [S1]. Costs fell $4M [S1]. In conclusion, " +
                      "this shows strong growth momentum.";
        SlopLinter.LintResult result = linter.lint(text, Set.of("S1"));
        assertThat(result.findings()).anyMatch(f -> f.rule().equals("restating-conclusion"));
    }

    // -------------------------------------------------------------------------
    // House-style GOOD / BAD samples
    // -------------------------------------------------------------------------

    @Test
    void goodSample_scoresAtLeast90() {
        SlopLinter.LintResult result = linter.lint(GOOD_SAMPLE, Set.of("S2", "S5"));
        assertThat(result.score())
                .as("GOOD sample should score >= 90, got %d with findings: %s",
                        result.score(), result.findings())
                .isGreaterThanOrEqualTo(90);
    }

    @Test
    void badSample_hasAtLeastOneBlock() {
        SlopLinter.LintResult result = linter.lint(BAD_SAMPLE, Set.of());
        assertThat(result.hasBlocks())
                .as("BAD sample should have at least one BLOCK finding, got: %s", result.findings())
                .isTrue();
    }

    // -------------------------------------------------------------------------
    // Helper method tests
    // -------------------------------------------------------------------------

    @Test
    void looksFactual_trueForSentenceWithDigit() {
        assertThat(SlopLinter.looksFactual("Revenue grew 25% in Q3.")).isTrue();
    }

    @Test
    void looksFactual_trueForSentenceWithProperNoun() {
        assertThat(SlopLinter.looksFactual("Nuance deployed the solution across Epic installations.")).isTrue();
    }

    @Test
    void looksFactual_falseForShortSentence() {
        assertThat(SlopLinter.looksFactual("AI is changing care.")).isFalse();
    }

    @Test
    void looksFactual_falseForFramingSentence() {
        assertThat(SlopLinter.looksFactual("This report covers AI adoption trends.")).isFalse();
    }

    @Test
    void restatesAtEnd_trueForHighOverlap() {
        List<String> sentences = List.of(
                "Revenue grew significantly in the third quarter.",
                "Operating costs decreased across all regions.",
                "Overall, revenue grew significantly as expected this quarter."
        );
        assertThat(SlopLinter.restatesAtEnd(sentences)).isTrue();
    }

    @Test
    void restatesAtEnd_falseForDistinctConclusion() {
        List<String> sentences = List.of(
                "Revenue grew 25% in Q3.",
                "Costs fell by $4M across three divisions.",
                "Buyers should request updated error-rate data before contracting."
        );
        assertThat(SlopLinter.restatesAtEnd(sentences)).isFalse();
    }

    // -------------------------------------------------------------------------
    // lintWiki — citation rules skipped
    // -------------------------------------------------------------------------

    @Test
    void lintWiki_skipsCitationRules() {
        // Factual sentence with no [S#] and an unknown ref — both citation rules should be silent
        String text = "Revenue grew 25% in Q3. Costs fell by $4M. The FDA cleared the device [S99].";
        SlopLinter.LintResult result = linter.lintWiki(text);
        assertThat(result.findings()).noneMatch(f -> f.rule().equals("uncited-claim"));
        assertThat(result.findings()).noneMatch(f -> f.rule().equals("bad-citation"));
    }

    @Test
    void lintWiki_stillCatchesBannedPhrases() {
        String text = "This paradigm shift will revolutionize care delivery across the ecosystem.";
        SlopLinter.LintResult result = linter.lintWiki(text);
        assertThat(result.findings()).anyMatch(f ->
                f.rule().equals("banned-phrase") && f.severity() == SlopLinter.Severity.BLOCK);
    }

    @Test
    void splitSentences_skipsHeadingsAndBullets() {
        String md = "# Heading\n- Bullet one\n- Bullet two\nRegular sentence here. Another one.";
        List<String> sentences = SlopLinter.splitSentences(md);
        assertThat(sentences).hasSize(2);
        assertThat(sentences).noneMatch(s -> s.startsWith("#") || s.startsWith("-"));
    }
}
