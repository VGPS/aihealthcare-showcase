package com.wgblackmon.aihealthcare.infrastructure.ingestion;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Filters RSS feed articles by relevance to Healthcare AND Artificial Intelligence.
 *
 * <p>An article passes the filter only when its combined title+body text contains
 * at least one match for the healthcare keyword pattern AND at least one match
 * for the AI keyword pattern.  Articles that satisfy only one pattern (e.g., a
 * general AI article unrelated to healthcare, or a general healthcare article
 * unrelated to AI) are dropped.
 *
 * <p>This filter addresses a core quality problem with broad RSS sources such as
 * WHO News, MIT Technology Review, and Google News whose feeds include many items
 * outside the AI-in-healthcare scope — climate change, geopolitics, consumer AI,
 * and general public-health updates that do not mention AI at all.
 *
 * <p>Both patterns are compiled once at class-load time and are thread-safe; the
 * {@link Matcher} is created per call inside {@link #isRelevant}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-02
 * @updated 2026-05-02
 */
@Slf4j
@Component
public class ArticleRelevanceFilter {

    /**
     * Healthcare domain keywords.
     * Matches any occurrence of these terms (case-insensitive, no word-boundary
     * required so partial matches like "radiological" and "oncology" are caught).
     */
    static final Pattern HEALTHCARE_PATTERN = Pattern.compile(
            "health|hospital|patient|clinical|medical|physician|doctor|diagnos|" +
            "treatment|pharma|drug|therap|disease|nursing|nurse|radiol|" +
            "medical imaging|diagnostic imaging|clinical imaging|" +
            "surgery|oncol|pathol|pediatr|cardiol|psychiatr|telehealth|" +
            "EHR|EMR|FDA|WHO|NIH|genomic|biomedical|bioinformatics|" +
            "mental health|laboratory|specimen|clinical trial|pandemic|epidemic|" +
            "public health|precision medicine|telemedicine|care delivery|" +
            "electronic health|emergency medicine|preventive care",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Artificial Intelligence domain keywords.
     * Uses word boundaries for short acronyms (AI, LLM, NLP, GPT) to avoid
     * false positives, while broader phrases use substring matching.
     */
    static final Pattern AI_PATTERN = Pattern.compile(
            "\\bAI\\b|\\bA\\.I\\.\\b|artificial intelligence|machine learning|" +
            "deep learning|neural network|\\bLLM\\b|\\bGPT\\b|\\bNLP\\b|" +
            "\\bBERT\\b|\\bClaude\\b|chatbot|natural language processing|" +
            "computer vision|predictive model|generative AI|gen AI|" +
            "language model|transformer model|foundation model|" +
            "decision support system|predictive analytics|data science|" +
            "intelligent automation|large language model|\\bRAG\\b|" +
            "reinforcement learning|supervised learning|unsupervised learning",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Returns {@code true} when the article is relevant to AI-in-healthcare.
     *
     * <p>Relevance requires a match in BOTH the healthcare pattern AND the AI
     * pattern within the concatenation of the article's title and body text.
     *
     * @param article the article to evaluate; must not be {@code null}
     * @return {@code true} if the article matches both patterns
     */
    public boolean isRelevant(NewsArticle article) {
        log.debug("isRelevant() | articleId={}, title={}", article.articleId(), article.title());

        String searchText = buildSearchText(article);

        boolean hasHealthcare = HEALTHCARE_PATTERN.matcher(searchText).find();
        boolean hasAI = AI_PATTERN.matcher(searchText).find();
        boolean result = hasHealthcare && hasAI;

        if (!result) {
            log.debug("isRelevant() | FILTERED '{}': healthcare={}, ai={}",
                      article.title(), hasHealthcare, hasAI);
        }

        log.debug("isRelevant() | return={}", result);
        return result;
    }

    /**
     * Filters a list of articles, retaining only those relevant to AI-in-healthcare.
     *
     * @param articles the articles to filter; must not be {@code null}
     * @return a new list containing only relevant articles (never {@code null})
     */
    public List<NewsArticle> filter(List<NewsArticle> articles) {
        log.debug("filter() | articleCount={}", articles.size());

        List<NewsArticle> result = new ArrayList<>();
        int dropped = 0;

        for (NewsArticle article : articles) {
            if (isRelevant(article)) {
                result.add(article);
            } else {
                dropped++;
                log.debug("filter() | dropped: '{}'", article.title());
            }
        }

        log.info("filter() | {} articles kept, {} dropped as off-topic", result.size(), dropped);
        log.debug("filter() | return={} articles", result.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the combined search text from an article's title and body.
     *
     * @param article the article whose text fields are combined
     * @return non-null combined string (may be empty)
     */
    private String buildSearchText(NewsArticle article) {
        log.debug("buildSearchText() | articleId={}", article.articleId());

        StringBuilder sb = new StringBuilder();
        if (article.title() != null) {
            sb.append(article.title()).append(" ");
        }
        if (article.bodyText() != null) {
            sb.append(article.bodyText());
        }

        String result = sb.toString();
        log.debug("buildSearchText() | return={} chars", result.length());
        return result;
    }
}
