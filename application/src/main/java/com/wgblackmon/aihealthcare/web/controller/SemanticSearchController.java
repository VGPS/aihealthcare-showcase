package com.wgblackmon.aihealthcare.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Legacy redirect controller for the former Semantic Search page.
 *
 * <p>As of Slice 40, AI Search and Semantic Search were merged into a single
 * unified page at {@code /research/ai-search}.  This controller preserves the
 * old {@code /research/search} URL by issuing a 302 redirect, forwarding
 * any query parameters.  To roll back the merge, restore this controller
 * from git history (commit before Slice 40).
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-05-30
 * @updated 2026-06-06
 */
@Slf4j
@Controller
@RequestMapping("/research/search")
public class SemanticSearchController {

    /**
     * Redirects {@code GET /research/search} to {@code /research/ai-search},
     * preserving query parameters.
     *
     * @param q    optional search query (forwarded)
     * @param topK optional max results (forwarded)
     * @return redirect URL to the unified AI Search page
     */
    @GetMapping
    public String search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer topK) {
        log.debug("search() | redirecting to /research/ai-search | q={}, topK={}", q, topK);

        StringBuilder redirect = new StringBuilder("redirect:/research/ai-search");
        boolean hasParam = false;

        if (q != null && !q.isBlank()) {
            redirect.append("?q=").append(q);
            hasParam = true;
        }

        if (topK != null) {
            redirect.append(hasParam ? "&" : "?").append("topK=").append(topK);
        }

        String result = redirect.toString();
        log.debug("search() | return={}", result);
        return result;
    }
}
