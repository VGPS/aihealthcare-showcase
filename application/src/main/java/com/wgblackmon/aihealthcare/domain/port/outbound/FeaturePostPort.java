package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.FeaturePost;

import java.util.List;

/**
 * Driven (outbound) port supplying the library of LinkedIn feature posts.
 *
 * The domain does not care where the posts come from. The current adapter
 * reads a YAML file from the classpath; a future one could read a database
 * table without any change to the rotation service, which is the point of
 * defining the boundary here.
 *
 * Implementations are expected to have already substituted the library's meta
 * tokens ({{trial_url}} and friends) before returning posts, so that callers
 * receive publishable text. Author-supplied {{PLACEHOLDER}} tokens are left
 * intact for {@link FeaturePost#unresolvedTokens()} to report.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-05
 * @updated 2026-09-05
 */
public interface FeaturePostPort {

    /**
     * Returns every post in the library.
     *
     * @return all feature posts, never null; empty when no library is present
     */
    List<FeaturePost> findAll();

    /**
     * Returns the number of weeks in one full rotation cycle, derived from the
     * highest week number present in the library.
     *
     * @return the cycle length in weeks; 0 when the library is empty
     */
    int cycleWeeks();
}
