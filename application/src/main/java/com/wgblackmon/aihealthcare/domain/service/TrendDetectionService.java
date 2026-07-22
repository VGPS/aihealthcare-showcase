package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.TrendDirection;
import com.wgblackmon.aihealthcare.domain.model.TrendSignal;
import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure-domain service that detects rising, fading, and newly emerged keywords
 * by comparing frequency across three rolling time windows.
 *
 * <p>The algorithm extracts bigrams and trigrams from article titles and body text,
 * applies a healthcare-domain relevance filter, counts occurrence per window,
 * computes a momentum ratio, and classifies each keyword's direction.
 *
 * <p>Unigrams are only included when they match a curated set of known
 * healthcare/AI domain terms.  All other keywords must be multi-word phrases
 * (bigrams or trigrams) to reduce noise from generic single words.
 *
 * <p>Window definitions (relative to {@code analysisTime}):
 * <ul>
 *   <li><b>Current</b> — last 30 days</li>
 *   <li><b>Previous</b> — 31 to 90 days ago</li>
 *   <li><b>Baseline</b> — 91 to 180 days ago</li>
 * </ul>
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public class TrendDetectionService {

    private static final int CURRENT_WINDOW_DAYS = 30;
    private static final int PREVIOUS_WINDOW_DAYS = 60; // 31–90
    private static final int BASELINE_WINDOW_DAYS = 90; // 91–180

    private static final double RISING_THRESHOLD = 1.5;
    private static final double FADING_THRESHOLD = 0.67;

    private final int minOccurrences;
    private final int risingLimit;
    private final int fadingLimit;

    private static final Set<String> STOP_WORDS = buildStopWords();
    private static final Set<String> DOMAIN_UNIGRAMS = buildDomainUnigrams();

    public TrendDetectionService(int minOccurrences, int risingLimit, int fadingLimit) {
        this.minOccurrences = minOccurrences;
        this.risingLimit = risingLimit;
        this.fadingLimit = fadingLimit;
    }

    /**
     * Analyzes the given articles and produces a trend snapshot.
     *
     * @param articles   all articles within the 180-day lookback window
     * @param analysisTime the reference time for window calculations
     * @return a snapshot containing rising, fading, and new keyword signals
     */
    public TrendSnapshot detectTrends(List<NewsArticle> articles, Instant analysisTime) {
        if (articles == null || articles.isEmpty()) {
            return new TrendSnapshot(analysisTime, CURRENT_WINDOW_DAYS,
                    List.of(), List.of(), List.of(), 0);
        }

        Instant currentStart = analysisTime.minus(CURRENT_WINDOW_DAYS, ChronoUnit.DAYS);
        Instant previousStart = analysisTime.minus(CURRENT_WINDOW_DAYS + PREVIOUS_WINDOW_DAYS, ChronoUnit.DAYS);
        Instant baselineStart = analysisTime.minus(
                CURRENT_WINDOW_DAYS + PREVIOUS_WINDOW_DAYS + BASELINE_WINDOW_DAYS, ChronoUnit.DAYS);

        // Count keywords per window
        Map<String, Long> currentCounts = new HashMap<>();
        Map<String, Long> previousCounts = new HashMap<>();
        Map<String, Long> baselineCounts = new HashMap<>();
        Map<String, Instant> firstSeen = new HashMap<>();

        for (NewsArticle article : articles) {
            Instant articleTime = article.publishedAt();
            if (articleTime == null) {
                continue; // skip articles with no timestamp
            }

            Set<String> keywords = extractKeywords(article);

            for (String keyword : keywords) {
                // Track first seen
                Instant existing = firstSeen.get(keyword);
                if (existing == null || articleTime.isBefore(existing)) {
                    firstSeen.put(keyword, articleTime);
                }

                // Assign to window
                if (!articleTime.isBefore(currentStart)) {
                    currentCounts.merge(keyword, 1L, Long::sum);
                } else if (!articleTime.isBefore(previousStart)) {
                    previousCounts.merge(keyword, 1L, Long::sum);
                } else if (!articleTime.isBefore(baselineStart)) {
                    baselineCounts.merge(keyword, 1L, Long::sum);
                }
            }
        }

        // Collect all keywords that meet the minimum occurrence threshold in any window
        Set<String> allKeywords = new HashSet<>();
        for (Map.Entry<String, Long> entry : currentCounts.entrySet()) {
            if (entry.getValue() >= minOccurrences) {
                allKeywords.add(entry.getKey());
            }
        }
        for (Map.Entry<String, Long> entry : previousCounts.entrySet()) {
            if (entry.getValue() >= minOccurrences) {
                allKeywords.add(entry.getKey());
            }
        }
        for (Map.Entry<String, Long> entry : baselineCounts.entrySet()) {
            if (entry.getValue() >= minOccurrences) {
                allKeywords.add(entry.getKey());
            }
        }

        // Build signals
        List<TrendSignal> risingSignals = new ArrayList<>();
        List<TrendSignal> fadingSignals = new ArrayList<>();
        List<TrendSignal> newSignals = new ArrayList<>();

        for (String keyword : allKeywords) {
            long current = currentCounts.getOrDefault(keyword, 0L);
            long previous = previousCounts.getOrDefault(keyword, 0L);
            long baseline = baselineCounts.getOrDefault(keyword, 0L);

            double momentum = computeMomentum(current, previous);
            TrendDirection direction = classifyDirection(current, previous, baseline, momentum);

            TrendSignal signal = new TrendSignal(
                    keyword, current, previous, baseline,
                    momentum, direction, firstSeen.get(keyword));

            if (direction == TrendDirection.NEW) {
                newSignals.add(signal);
            } else if (direction == TrendDirection.RISING) {
                risingSignals.add(signal);
            } else if (direction == TrendDirection.FADING) {
                fadingSignals.add(signal);
            }
            // STABLE signals are not surfaced
        }

        // Sort rising by momentum descending, limit
        sortByMomentumDescending(risingSignals);
        if (risingSignals.size() > risingLimit) {
            risingSignals = new ArrayList<>(risingSignals.subList(0, risingLimit));
        }

        // Sort fading by momentum ascending, limit
        sortByMomentumAscending(fadingSignals);
        if (fadingSignals.size() > fadingLimit) {
            fadingSignals = new ArrayList<>(fadingSignals.subList(0, fadingLimit));
        }

        // Sort new by current count descending, limit to risingLimit
        sortByCurrentDescending(newSignals);
        if (newSignals.size() > risingLimit) {
            newSignals = new ArrayList<>(newSignals.subList(0, risingLimit));
        }

        return new TrendSnapshot(analysisTime, CURRENT_WINDOW_DAYS,
                risingSignals, fadingSignals, newSignals, allKeywords.size());
    }

    /**
     * Extracts domain-relevant keywords from an article's title and body text.
     *
     * <p>Unigrams are only kept if they match a curated healthcare/AI domain
     * vocabulary.  Bigrams and trigrams are kept when at least one token is a
     * non-stopword with healthcare or AI relevance (validated by the
     * domain-relevance filter).
     */
    Set<String> extractKeywords(NewsArticle article) {
        Set<String> keywords = new HashSet<>();

        String title = article.title() != null ? article.title() : "";
        String body = article.bodyText() != null ? article.bodyText() : "";

        extractFromText(title, keywords);
        extractFromText(body, keywords);

        return keywords;
    }

    private void extractFromText(String text, Set<String> keywords) {
        if (text == null || text.isBlank()) {
            return;
        }

        String cleaned = cleanText(text);
        String[] tokens = cleaned.split(" ");

        // Filter tokens: remove empty, short, and stop words
        List<String> validTokens = new ArrayList<>();
        for (String token : tokens) {
            if (token.length() >= 3 && !STOP_WORDS.contains(token)) {
                validTokens.add(token);
            }
        }

        // Unigrams — only domain-recognized terms
        for (String token : validTokens) {
            if (DOMAIN_UNIGRAMS.contains(token)) {
                keywords.add(token);
            }
        }

        // Bigrams — at least one token must be domain-relevant
        for (int i = 0; i < validTokens.size() - 1; i++) {
            String a = validTokens.get(i);
            String b = validTokens.get(i + 1);
            if (isDomainRelevantPhrase(a, b)) {
                keywords.add(a + " " + b);
            }
        }

        // Trigrams — at least one token must be domain-relevant
        for (int i = 0; i < validTokens.size() - 2; i++) {
            String a = validTokens.get(i);
            String b = validTokens.get(i + 1);
            String c = validTokens.get(i + 2);
            if (isDomainRelevantPhrase(a, b, c)) {
                keywords.add(a + " " + b + " " + c);
            }
        }
    }

    /**
     * Cleans raw text: decodes HTML entities, strips HTML tags, removes
     * non-alphanumeric characters, collapses whitespace, and lowercases.
     */
    String cleanText(String text) {
        // Decode common HTML entities BEFORE stripping tags
        String decoded = text;
        decoded = decoded.replace("&nbsp;", " ");
        decoded = decoded.replace("&#160;", " ");
        decoded = decoded.replace("&amp;", "&");
        decoded = decoded.replace("&#38;", "&");
        decoded = decoded.replace("&lt;", "<");
        decoded = decoded.replace("&gt;", ">");
        decoded = decoded.replace("&quot;", "\"");
        decoded = decoded.replace("&#39;", "'");
        decoded = decoded.replace("&apos;", "'");
        decoded = decoded.replace("&mdash;", " ");
        decoded = decoded.replace("&ndash;", " ");
        decoded = decoded.replace("&hellip;", " ");
        decoded = decoded.replace("&rsquo;", "'");
        decoded = decoded.replace("&lsquo;", "'");
        decoded = decoded.replace("&rdquo;", "\"");
        decoded = decoded.replace("&ldquo;", "\"");
        decoded = decoded.replace("&bull;", " ");
        decoded = decoded.replace("&trade;", "");
        decoded = decoded.replace("&reg;", "");
        decoded = decoded.replace("&copy;", "");

        // Strip any remaining numeric HTML entities (&#NNN;)
        decoded = decoded.replaceAll("&#\\d+;", " ");
        // Strip any remaining named HTML entities (&word;)
        decoded = decoded.replaceAll("&[a-zA-Z]+;", " ");

        // Strip HTML tags
        decoded = decoded.replaceAll("<[^>]+>", " ");

        // Lowercase and keep only letters, digits, spaces
        decoded = decoded.toLowerCase();
        decoded = decoded.replaceAll("[^a-z0-9\\s]", " ");
        decoded = decoded.replaceAll("\\s+", " ");
        decoded = decoded.trim();

        return decoded;
    }

    /**
     * Returns true if a bigram contains at least one domain-relevant token.
     * A token is domain-relevant if it appears in the domain unigrams set
     * or matches a healthcare/AI pattern.
     */
    private boolean isDomainRelevantPhrase(String a, String b) {
        return isDomainToken(a) || isDomainToken(b);
    }

    /**
     * Returns true if a trigram contains at least one domain-relevant token.
     */
    private boolean isDomainRelevantPhrase(String a, String b, String c) {
        return isDomainToken(a) || isDomainToken(b) || isDomainToken(c);
    }

    /**
     * Returns true if the token is recognized as a healthcare/AI domain term.
     */
    private boolean isDomainToken(String token) {
        return DOMAIN_UNIGRAMS.contains(token);
    }

    /**
     * Computes momentum as the ratio of per-day frequency in the current window
     * to per-day frequency in the previous window.
     */
    double computeMomentum(long current, long previous) {
        double currentRate = (double) current / CURRENT_WINDOW_DAYS;
        double previousRate = (double) previous / PREVIOUS_WINDOW_DAYS;

        if (previousRate == 0.0) {
            return current > 0 ? Double.MAX_VALUE : 0.0;
        }
        return currentRate / previousRate;
    }

    TrendDirection classifyDirection(long current, long previous, long baseline,
                                     double momentum) {
        // NEW: appears in current window but has zero baseline
        if (current > 0 && baseline == 0 && previous == 0) {
            return TrendDirection.NEW;
        }

        if (momentum > RISING_THRESHOLD) {
            return TrendDirection.RISING;
        }
        if (momentum < FADING_THRESHOLD) {
            return TrendDirection.FADING;
        }
        return TrendDirection.STABLE;
    }

    private void sortByMomentumDescending(List<TrendSignal> signals) {
        int n = signals.size();
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - 1 - i; j++) {
                if (signals.get(j).momentum() < signals.get(j + 1).momentum()) {
                    TrendSignal tmp = signals.get(j);
                    signals.set(j, signals.get(j + 1));
                    signals.set(j + 1, tmp);
                }
            }
        }
    }

    private void sortByMomentumAscending(List<TrendSignal> signals) {
        int n = signals.size();
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - 1 - i; j++) {
                if (signals.get(j).momentum() > signals.get(j + 1).momentum()) {
                    TrendSignal tmp = signals.get(j);
                    signals.set(j, signals.get(j + 1));
                    signals.set(j + 1, tmp);
                }
            }
        }
    }

    private void sortByCurrentDescending(List<TrendSignal> signals) {
        int n = signals.size();
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - 1 - i; j++) {
                if (signals.get(j).current30d() < signals.get(j + 1).current30d()) {
                    TrendSignal tmp = signals.get(j);
                    signals.set(j, signals.get(j + 1));
                    signals.set(j + 1, tmp);
                }
            }
        }
    }

    private static Set<String> buildStopWords() {
        Set<String> words = new HashSet<>();

        // Common English stopwords
        String[] common = {
            "the", "and", "for", "are", "but", "not", "you", "all", "can", "has",
            "her", "was", "one", "our", "out", "his", "had", "hot", "how", "its",
            "may", "new", "now", "old", "see", "way", "who", "did", "get", "let",
            "say", "she", "too", "use", "will", "with", "this", "that", "from",
            "they", "been", "have", "many", "some", "them", "than", "each", "make",
            "like", "long", "look", "more", "most", "only", "over", "such", "take",
            "also", "into", "just", "very", "when", "what", "your", "about", "would",
            "there", "their", "which", "could", "other", "after", "being", "those",
            "these", "where", "every", "under", "still", "should", "while", "first",
            "through", "between", "before", "during", "because", "against", "without",
            "within", "along", "following", "across", "behind", "beyond", "toward",
            "does", "done", "doing", "much", "well", "here", "then", "both",
            "same", "another", "know", "even", "back", "given", "going", "using",
            "really", "already", "since", "right", "might", "think", "said", "says",
            "come", "came", "want", "keep", "start", "began", "begin", "began",
            "need", "needs", "needed", "able", "whether", "often", "always", "never",
            "next", "last", "left", "around", "down", "upon", "until", "though",
            "however", "therefore", "thus", "hence", "meanwhile", "moreover",
            "furthermore", "although", "instead", "rather", "perhaps", "likely",
            "currently", "recently", "according", "nearly", "simply", "actually",
            "especially", "particularly", "specifically", "essentially", "certainly"
        };
        for (String w : common) {
            words.add(w);
        }

        // Generic verbs/adjectives that aren't domain-specific
        String[] genericVerbs = {
            "build", "built", "building", "create", "created", "creating",
            "improve", "improved", "improving", "provide", "provides", "provided",
            "include", "includes", "included", "including",
            "show", "showed", "shows", "shown", "showing",
            "report", "reported", "reports", "reporting",
            "offer", "offered", "offers", "offering",
            "enable", "enabled", "enables", "enabling",
            "develop", "developed", "developing", "development",
            "design", "designed", "designing",
            "launch", "launched", "launching",
            "announce", "announced", "announcing", "announcement",
            "appear", "appeared", "appears", "appearing",
            "share", "shared", "sharing", "shares",
            "present", "presented", "presenting",
            "publish", "published", "publishing",
            "learn", "learned", "learning",
            "grow", "grew", "growing", "growth",
            "change", "changed", "changing", "changes",
            "move", "moved", "moving",
            "lead", "led", "leading", "leaders",
            "find", "found", "finding", "findings",
            "give", "gave", "giving",
            "tell", "told", "telling",
            "become", "became", "becoming",
            "remain", "remained", "remaining",
            "continue", "continued", "continuing",
            "suggest", "suggested", "suggesting",
            "expect", "expected", "expecting",
            "address", "addressed", "addressing",
            "focus", "focused", "focusing",
            "require", "required", "requiring",
            "manage", "managed", "managing",
            "receive", "received", "receiving",
            "drive", "driven", "driving",
            "achieve", "achieved", "achieving",
            "consider", "considered", "considering",
            "identify", "identified", "identifying",
            "ensure", "ensuring",
            "reduce", "reduced", "reducing",
            "increase", "increased", "increasing",
            "allow", "allowed", "allowing",
            "support", "supported", "supporting",
            "deliver", "delivered", "delivering",
            "implement", "implemented", "implementing",
            "integrate", "integrated", "integrating",
            "enhance", "enhanced", "enhancing",
            "leverage", "leveraged", "leveraging",
            "optimize", "optimized", "optimizing",
            "transform", "transformed", "transforming",
            "utilize", "utilized", "utilizing",
            "expand", "expanded", "expanding",
            "apply", "applied", "applying"
        };
        for (String w : genericVerbs) {
            words.add(w);
        }

        // Generic nouns that appear everywhere
        String[] genericNouns = {
            "access", "area", "areas", "aspect", "base", "benefit", "benefits",
            "case", "cases", "center", "challenge", "challenges", "company",
            "companies", "country", "countries", "effort", "efforts", "example",
            "examples", "experience", "experiences", "fact", "factor", "factors",
            "feature", "features", "field", "form", "forms", "future",
            "goal", "goals", "group", "groups", "hand", "head", "home",
            "idea", "ideas", "interest", "kind", "level", "levels",
            "line", "list", "lot", "matter", "member", "members",
            "million", "millions", "billion", "billions",
            "moment", "month", "months", "number", "numbers",
            "order", "part", "parts", "percent", "period",
            "place", "plan", "plans", "point", "points", "position",
            "power", "practice", "problem", "problems", "process",
            "product", "products", "program", "programs", "project", "projects",
            "question", "questions", "range", "rate", "rates", "reason",
            "record", "records", "region", "report", "reports", "result",
            "results", "role", "room", "rule", "rules", "sector",
            "series", "set", "side", "sign", "size", "sort", "source", "sources",
            "space", "stage", "standard", "standards", "state", "states",
            "step", "steps", "strategy", "structure",
            "term", "terms", "test", "thing", "things", "time", "times",
            "today", "total", "turn", "type", "types", "unit", "units",
            "value", "values", "view", "version", "week", "weeks",
            "word", "words", "work", "works", "world", "year", "years",
            "way", "ways", "end", "day", "days", "ago", "top", "big",
            "real", "full", "high", "low", "best", "key", "major", "main",
            "large", "small", "great", "early", "late", "whole", "different",
            "important", "significant", "available", "possible", "certain",
            "clear", "current", "entire", "former", "general", "global",
            "individual", "initial", "latest", "local", "multiple",
            "overall", "past", "previous", "primary", "public", "single",
            "social", "special", "specific", "strong", "various", "wide",
            "together", "across"
        };
        for (String w : genericNouns) {
            words.add(w);
        }

        // Healthcare/article boilerplate — generic article structure words
        String[] boilerplate = {
            "article", "study", "studies", "research", "researchers",
            "read", "click", "view", "subscribe", "newsletter",
            "www", "http", "https", "com", "org", "html", "php", "asp",
            "press", "release", "published", "journal",
            "abstract", "methods", "conclusion", "conclusions",
            "introduction", "background", "objective", "objectives",
            "purpose", "review", "reviews", "analysis", "approach",
            "content", "page", "site", "post", "update", "updates",
            "latest", "recent", "related", "resources", "link", "links",
            "image", "images", "photo", "video", "caption",
            "copyright", "rights", "reserved", "contact", "privacy",
            "policy", "terms", "conditions", "cookies", "advertisement",
            "sponsored", "disclaimer", "footer", "header", "navigation",
            "menu", "sidebar", "login", "register", "sign", "account",
            "email", "address", "phone", "location", "map",
            "facebook", "twitter", "linkedin", "instagram", "youtube",
            "follow", "like", "comment", "comments", "reply",
            "trending", "popular", "featured", "breaking",
            "editor", "author", "contributor", "staff", "team",
            "opinion", "editorial", "column", "blog"
        };
        for (String w : boilerplate) {
            words.add(w);
        }

        // HTML artifact residue
        String[] htmlArtifacts = {
            "nbsp", "amp", "quot", "apos", "mdash", "ndash", "hellip",
            "rsquo", "lsquo", "rdquo", "ldquo", "bull", "trade",
            "reg", "copy", "shy", "ensp", "emsp", "thinsp",
            "div", "span", "class", "style", "href", "src", "alt",
            "img", "table", "thead", "tbody", "tfoot",
            "width", "height", "border", "padding", "margin",
            "font", "color", "background", "display", "none",
            "block", "inline", "flex", "grid", "hidden", "visible",
            "javascript", "script", "css", "var", "function"
        };
        for (String w : htmlArtifacts) {
            words.add(w);
        }

        // Numeric-looking tokens
        String[] numeric = {
            "2020", "2021", "2022", "2023", "2024", "2025", "2026", "2027",
            "2028", "2029", "2030", "000", "100", "500"
        };
        for (String w : numeric) {
            words.add(w);
        }

        return words;
    }

    /**
     * Curated set of domain-specific unigrams that are meaningful on their own
     * in the healthcare AI context.  Only unigrams in this set are kept;
     * all other single words are filtered out and can only appear as part
     * of bigrams or trigrams.
     */
    private static Set<String> buildDomainUnigrams() {
        Set<String> terms = new HashSet<>();

        // AI and ML core terms
        String[] aiTerms = {
            "ai", "llm", "llms", "gpt", "gpt4", "gpt5", "chatgpt",
            "claude", "gemini", "copilot", "openai", "anthropic", "deepmind",
            "perplexity", "mistral", "llama", "bedrock", "sagemaker",
            "transformer", "transformers", "diffusion", "multimodal",
            "rag", "finetuning", "embeddings", "vectordb", "langchain",
            "agentic", "autonomous", "generative", "reinforcement",
            "nlp", "ner", "ocr", "cnn", "rnn", "lstm", "bert", "gpt3",
            "nvidia", "gpu", "tpu", "inference", "pretraining"
        };
        for (String t : aiTerms) {
            terms.add(t);
        }

        // Healthcare and medical terms
        String[] healthcareTerms = {
            "fda", "ehr", "ehr", "emr", "hipaa", "cms", "nih", "cdc",
            "pharma", "pharmaceutical", "biopharma", "biotech", "biotechnology",
            "genomics", "genomic", "proteomics", "metabolomics",
            "oncology", "cardiology", "radiology", "pathology", "dermatology",
            "neurology", "psychiatry", "ophthalmology", "orthopedics",
            "immunology", "endocrinology", "gastroenterology", "pulmonology",
            "nephrology", "hematology", "urology", "rheumatology",
            "telehealth", "telemedicine", "mhealth", "ehealth",
            "biomarker", "biomarkers", "epigenetic", "epigenetics",
            "therapeutic", "therapeutics", "biosimilar", "biosimilars",
            "immunotherapy", "chemotherapy", "radiotherapy",
            "wearable", "wearables", "biosensor", "biosensors",
            "interoperability", "fhir", "hl7", "dicom",
            "sepsis", "diabetes", "alzheimers", "parkinsons", "cancer",
            "stroke", "copd", "asthma", "hypertension", "obesity",
            "pandemic", "epidemic", "mrna", "vaccine", "vaccines",
            "surgical", "robotic", "laparoscopic", "endoscopic",
            "diagnostic", "diagnostics", "screening", "prognosis",
            "precision", "personalized", "regenerative",
            "nanomedicine", "nanotechnology",
            "cybersecurity", "ransomware", "phishing",
            "reimbursement", "payer", "payers", "insurer", "insurers",
            "medicaid", "medicare", "tricare", "uninsured",
            "clinician", "clinicians", "physician", "physicians",
            "nurse", "nurses", "pharmacist", "pharmacists",
            "ambulatory", "inpatient", "outpatient", "icu",
            "burnout", "staffing", "workforce",
            "epic", "cerner", "meditech", "allscripts", "athenahealth",
            "tempus", "flatiron", "veracyte", "guardant"
        };
        for (String t : healthcareTerms) {
            terms.add(t);
        }

        // Regulatory and compliance terms
        String[] regulatoryTerms = {
            "premarket", "clearance", "approval", "recall",
            "compliance", "audit", "accreditation",
            "samd", "ivd", "510k", "pma", "udi",
            "gdpr", "ccpa", "baa", "hitech",
            "dea", "oig", "ocr"
        };
        for (String t : regulatoryTerms) {
            terms.add(t);
        }

        // Business/market terms specific to healthcare AI
        String[] businessTerms = {
            "acquisition", "merger", "ipo", "spac",
            "valuation", "fundraising", "startup", "startups",
            "unicorn", "decacorn",
            "partnership", "collaboration", "consortium",
            "pilot", "deployment", "rollout", "adoption",
            "scalability", "interop"
        };
        for (String t : businessTerms) {
            terms.add(t);
        }

        return terms;
    }
}
