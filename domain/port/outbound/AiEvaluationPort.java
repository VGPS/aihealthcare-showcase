package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.EvaluationScore;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
import com.wgblackmon.aihealthcare.domain.model.NewsletterTone;

import java.util.List;

/**
 * Outbound port — score an AI-generated newsletter section against its source
 * articles using an LLM-as-judge evaluation pattern.
 *
 * <p>Implementations live in {@code infrastructure/ai} and use a dedicated
 * evaluation prompt (separate from the summarization prompt) to instruct the
 * LLM to act as a quality judge.  This port is only invoked during prompt
 * experimentation, not during production newsletter generation.
 *
 * <p>The evaluator scores on five dimensions: relevance, conciseness,
 * attribution quality, tone match, and completeness — each on a [0.0, 1.0]
 * scale.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-17
 * @updated 2026-04-17
 */
public interface AiEvaluationPort {

    /**
     * Score an AI-generated section against the original source articles.
     *
     * @param section  the AI-generated section to evaluate
     * @param articles the source articles that were used to generate the section
     * @param topic    the topic used during summarization
     * @param tone     the tone used during summarization
     * @return an {@link EvaluationScore} with scores on all five dimensions
     */
    EvaluationScore evaluate(
            NewsletterSection section,
            List<NewsArticle> articles,
            String topic,
            NewsletterTone tone
    );
}
