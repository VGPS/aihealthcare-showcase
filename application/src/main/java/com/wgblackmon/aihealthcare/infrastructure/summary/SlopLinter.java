package com.wgblackmon.aihealthcare.infrastructure.summary;

import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * Deterministic, zero-LLM text linter that checks a markdown summary against the
 * AI Healthcare house style guide.  Runs on every summary produced by the anti-slop
 * pipeline, on existing summaries during the backfill baseline scan, and on
 * LLM-compiled wiki pages via {@link #lintWiki(String)}.
 *
 * <p>Nine rules are evaluated.  Each rule either BLOCKs (the summary must not be
 * shown to users without an edit pass) or WARNs (quality is degraded but the summary
 * is displayable).  A summary passes when its score is at or above the configured
 * threshold and it has zero BLOCK findings.
 *
 * <p>{@link #lint(String, java.util.Set)} runs all 9 rules including citation checks.
 * {@link #lintWiki(String)} runs 7 rules, skipping uncited-claim (4) and bad-citation
 * (5) because wiki pages use prose attribution rather than {@code [S#]} tags.
 *
 * <p>Thresholds are configured via {@code aihealthcare.summary.lint.*} in
 * {@code application.yml}.  Banned phrases are defined in {@link #DEFAULT_BANNED_PHRASES}
 * and can be extended in {@code AppConfig.slopLinter()}.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-09-29
 * @updated 2026-09-29
 */
public class SlopLinter {

    public enum Severity { BLOCK, WARN }

    public record Finding(String rule, Severity severity, String detail) {}

    public record LintResult(int score, List<Finding> findings) {
        public boolean passes() {
            return findings.stream().noneMatch(f -> f.severity() == Severity.BLOCK)
                    && score >= 80;
        }
        public boolean hasBlocks() {
            return findings.stream().anyMatch(f -> f.severity() == Severity.BLOCK);
        }
        public String statusLabel() {
            if (passes()) return "PASS";
            if (hasBlocks()) return "BLOCKED";
            return "WARN";
        }
    }

    /** Default banned-phrase list. Extend in {@code AppConfig.slopLinter()} if needed. */
    public static final List<String> DEFAULT_BANNED_PHRASES = List.of(
            "delve", "delving", "tapestry", "navigate the complexities",
            "in today's rapidly evolving", "ever-evolving", "game-changer", "game-changing",
            "revolutionize", "revolutionizing", "transformative", "paradigm shift",
            "unlock the potential", "unleash", "harness the power",
            "it's important to note", "it is worth noting", "it's worth mentioning",
            "notably", "importantly", "in conclusion", "in summary", "overall",
            "ultimately", "at the end of the day",
            "plays a crucial role", "plays a pivotal role", "crucial", "pivotal",
            "vital", "robust", "seamless", "seamlessly",
            "cutting-edge", "state-of-the-art", "leverage", "synergy", "holistic",
            "multifaceted", "a testament to", "stands as", "serves as a",
            "not only", "moreover", "furthermore", "additionally",
            "embark", "foster"
    );

    private static final Pattern CITATION      = Pattern.compile("\\[S\\d+(?:,\\s*S\\d+)*]");
    private static final Pattern BOLD          = Pattern.compile("\\*\\*[^*]+\\*\\*");
    private static final Pattern BULLET        = Pattern.compile("(?m)^\\s*([-*•]|\\d+\\.)\\s+");
    private static final Pattern FILLER_OPENER = Pattern.compile(
            "(?m)(^|[.!?]\\s+)(Moreover|Furthermore|Additionally|Notably|Importantly|Overall|Ultimately),",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRIPLET       = Pattern.compile(
            "[\\w ]+,\\s[\\w ]+,\\sand\\s[\\w ]+");
    private static final Pattern EM_DASH       = Pattern.compile("—");
    private static final Pattern DIGIT         = Pattern.compile("\\d");
    private static final Pattern MID_UPPER     = Pattern.compile("(?<=[\\s,(])[A-Z][a-z]{2,}");
    private static final Set<String> STOP_CAPS = Set.of(
            "The", "A", "An", "In", "On", "At", "By", "For", "To", "And", "But", "Or",
            "With", "From", "This", "That", "These", "Those", "Its", "Their", "Our");

    private final List<Pattern> bannedPatterns;
    private final int passScore;
    private final int maxBold;

    public SlopLinter(List<String> bannedPhrases, int passScore, int maxBold) {
        this.bannedPatterns = bannedPhrases.stream()
                .map(p -> Pattern.compile("\\b" + Pattern.quote(p.trim()) + "\\b",
                        Pattern.CASE_INSENSITIVE))
                .collect(Collectors.toList());
        this.passScore = passScore;
        this.maxBold   = maxBold;
    }

    /** Convenience constructor using spec defaults (pass-score=80, max-bold=1). */
    public SlopLinter(List<String> bannedPhrases) {
        this(bannedPhrases, 80, 1);
    }

    /** Runs all 9 rules. Use for LLM-generated summaries that carry {@code [S#]} citations. */
    public LintResult lint(String md, Set<String> validSourceIds) {
        return lintRules(md, validSourceIds, true);
    }

    /**
     * Runs 7 rules — skips uncited-claim (4) and bad-citation (5).
     * Use for wiki page markdown where attribution is prose-style, not {@code [S#]} tags.
     */
    public LintResult lintWiki(String md) {
        return lintRules(md, Set.of(), false);
    }

    private LintResult lintRules(String md, Set<String> validSourceIds, boolean checkCitations) {
        List<Finding> out  = new ArrayList<>();
        int           score = 100;

        // Rule 1 — banned phrases (BLOCK, -10 each)
        for (Pattern p : bannedPatterns) {
            Matcher m = p.matcher(md);
            while (m.find()) {
                out.add(new Finding("banned-phrase", Severity.BLOCK, m.group()));
                score -= 10;
            }
        }

        // Rule 2 — too much bold (WARN, -5 per extra span)
        long boldCount = BOLD.matcher(md).results().count();
        if (boldCount > maxBold) {
            out.add(new Finding("too-much-bold", Severity.WARN, boldCount + " bold spans"));
            score -= 5 * (int)(boldCount - maxBold);
        }

        // Rule 3 — bullet-heavy: more bullets than half the sentence count (WARN, -10)
        List<String> sentences = splitSentences(md);
        long bulletCount = BULLET.matcher(md).results().count();
        if (bulletCount >= 4 && sentences.size() > 0 && bulletCount > sentences.size() / 2.0) {
            out.add(new Finding("bullet-heavy", Severity.WARN, bulletCount + " bullets"));
            score -= 10;
        }

        if (checkCitations) {
            // Rule 4 — uncited factual sentences (BLOCK, -8 each)
            long uncited = sentences.stream()
                    .filter(s -> looksFactual(s) && !CITATION.matcher(s).find())
                    .count();
            if (uncited > 0) {
                out.add(new Finding("uncited-claim", Severity.BLOCK,
                        uncited + " factual sentence(s) without [S#]"));
                score -= 8 * (int) uncited;
            }

            // Rule 5 — bad citation IDs that don't match provided sources (BLOCK, -15 each)
            Matcher citeMatcher = Pattern.compile("S(\\d+)").matcher(md);
            while (citeMatcher.find()) {
                String cited = "S" + citeMatcher.group(1);
                if (!validSourceIds.isEmpty() && !validSourceIds.contains(cited)) {
                    out.add(new Finding("bad-citation", Severity.BLOCK, cited + " is not a known source"));
                    score -= 15;
                }
            }
        }

        // Rule 6 — filler openers (WARN, -3 each)
        long openers = FILLER_OPENER.matcher(md).results().count();
        if (openers > 0) {
            out.add(new Finding("filler-opener", Severity.WARN, openers + " filler sentence opener(s)"));
            score -= 3 * (int) openers;
        }

        // Rule 7 — reflexive triplets (WARN, -3 each over 1)
        long triplets = TRIPLET.matcher(md).results().count();
        if (triplets > 1) {
            out.add(new Finding("reflexive-triplet", Severity.WARN, triplets + " comma triplets"));
            score -= 3 * (int)(triplets - 1);
        }

        // Rule 8 — em-dash heavy (WARN, -2 each over 2)
        long dashes = EM_DASH.matcher(md).results().count();
        if (dashes > 2) {
            out.add(new Finding("em-dash-heavy", Severity.WARN, dashes + " em-dashes"));
            score -= 2 * (int)(dashes - 2);
        }

        // Rule 9 — restating conclusion (WARN, -10)
        if (restatesAtEnd(sentences)) {
            out.add(new Finding("restating-conclusion", Severity.WARN, "last paragraph restates the opening"));
            score -= 10;
        }

        return new LintResult(Math.max(score, 0), Collections.unmodifiableList(out));
    }

    /**
     * Returns true when the sentence contains a number OR a mid-sentence proper noun,
     * indicating it likely makes a verifiable factual claim that should carry a citation.
     * Conservative by design — false negatives are less harmful than false positives.
     */
    static boolean looksFactual(String s) {
        if (s == null || s.trim().length() < 15) return false;
        if (DIGIT.matcher(s).find()) return true;
        Matcher m = MID_UPPER.matcher(s);
        while (m.find()) {
            if (!STOP_CAPS.contains(m.group())) return true;
        }
        return false;
    }

    /**
     * Returns true when the last sentence echoes the first — either by starting with a
     * summary-phrase trigger word, or by having > 60% token overlap with the opening.
     */
    static boolean restatesAtEnd(List<String> sentences) {
        if (sentences.size() < 3) return false;
        String last = sentences.get(sentences.size() - 1).toLowerCase();
        for (String trigger : List.of("in conclusion", "in summary", "overall",
                "ultimately", "to summarize", "in closing")) {
            if (last.contains(trigger)) return true;
        }
        Set<String> firstTokens = tokenize(sentences.get(0));
        Set<String> lastTokens  = tokenize(last);
        firstTokens.removeAll(Set.of("the", "a", "an", "in", "on", "at", "by", "for",
                "to", "and", "but", "or", "is", "are", "was", "were", "of", "with",
                "its", "their", "this", "that", "has", "have", "been"));
        if (firstTokens.isEmpty()) return false;
        long overlap = lastTokens.stream().filter(firstTokens::contains).count();
        return (double) overlap / firstTokens.size() > 0.6;
    }

    /**
     * Splits a markdown string into individual sentences, skipping heading and bullet lines.
     */
    static List<String> splitSentences(String md) {
        if (md == null || md.isBlank()) return List.of();
        String flat = Arrays.stream(md.split("\n"))
                .filter(line -> {
                    String t = line.trim();
                    return !t.startsWith("#")
                            && !t.matches("^[-*•].*")
                            && !t.matches("^\\d+\\..*")
                            && !t.isBlank();
                })
                .collect(Collectors.joining(" "));
        return Arrays.stream(flat.split("(?<=[.!?])\\s+(?=[A-Z\"])"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static Set<String> tokenize(String text) {
        return new HashSet<>(Arrays.asList(text.toLowerCase().split("[^a-z]+")));
    }
}
