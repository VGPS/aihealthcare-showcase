package com.wgblackmon.aihealthcare.web.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Set;

/**
 * Global model attribute provider for SEO meta tags in the shared head fragment.
 *
 * <p>Injects {@code baseUrl}, {@code canonicalUrl}, {@code defaultDescription},
 * {@code noIndex}, and {@code robotsDirective} into every Thymeleaf model so the
 * head fragment can render favicon, meta description, canonical URL, Open Graph
 * tags, and a conditional robots directive without each controller setting them
 * individually.
 *
 * <p>Controllers may override {@code pageDescription} with a page-specific value;
 * the head fragment falls back to {@code defaultDescription} when absent.
 *
 * <p>Search/filter query parameters ({@link #FILTER_PARAMS}) on an otherwise
 * public list page (e.g. {@code /wiki?query=liability}) strip the canonical URL
 * back to the unfiltered list and emit {@code noindex, follow} so Google doesn't
 * index an unbounded number of filter-combination URLs as duplicate content, while
 * still crawling the links on the page. Plain pagination ({@code ?page=N} with no
 * filter params) keeps a self-referencing canonical and stays indexable.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-09-10
 * @updated 2026-09-30 — T3 SEO: canonical/noindex handling for filtered + paginated list pages
 */
@Slf4j
@ControllerAdvice
public class SeoModelAdvice {

    private static final String DEFAULT_DESCRIPTION =
            "AI Healthcare Intelligence — daily market analysis, regulatory tracking, "
            + "company directory, and state legislation registry for AI in healthcare.";

    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/", "/about", "/pricing", "/press", "/login", "/register",
            "/directory", "/wiki", "/legislation", "/trends", "/developer", "/privacy",
            "/tour", "/choose-path", "/forgot-password", "/reset-password", "/error",
            "/insights"
    );

    /** Query params that identify a filtered (not merely paginated) list view across all list pages. */
    private static final Set<String> FILTER_PARAMS = Set.of(
            "query", "pageType", "sector", "sort", "state", "category", "status", "q"
    );

    private static final String PAGE_PARAM = "page";

    private final String baseUrl;

    public SeoModelAdvice(@Value("${aihealthcare.base-url}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        log.debug("SeoModelAdvice() | baseUrl={}", this.baseUrl);
    }

    @ModelAttribute("baseUrl")
    public String baseUrl() {
        return baseUrl;
    }

    @ModelAttribute("canonicalUrl")
    public String canonicalUrl(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String result;
        if (hasFilterParam(request)) {
            result = baseUrl + uri;
        } else {
            String page = request.getParameter(PAGE_PARAM);
            result = (page != null && !page.isBlank() && !"0".equals(page) && !"1".equals(page))
                    ? baseUrl + uri + "?page=" + page
                    : baseUrl + uri;
        }
        log.debug("canonicalUrl() | uri={}, return={}", uri, result);
        return result;
    }

    @ModelAttribute("defaultDescription")
    public String defaultDescription() {
        return DEFAULT_DESCRIPTION;
    }

    @ModelAttribute("noIndex")
    public boolean noIndex(HttpServletRequest request) {
        String uri = request.getRequestURI();
        boolean result = robotsDirective(request) != null;
        log.debug("noIndex() | uri={}, return={}", uri, result);
        return result;
    }

    /**
     * {@code noindex, nofollow} for pages requiring authentication (don't crawl links
     * into the app), {@code noindex, follow} for filtered views of a public list page
     * (don't index the filter combination, but still crawl its links), or {@code null}
     * when the page should be indexed normally.
     */
    @ModelAttribute("robotsDirective")
    public String robotsDirective(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String result;
        if (!isPublicPath(uri)) {
            result = "noindex, nofollow";
        } else if (hasFilterParam(request)) {
            result = "noindex, follow";
        } else {
            result = null;
        }
        log.debug("robotsDirective() | uri={}, return={}", uri, result);
        return result;
    }

    private boolean hasFilterParam(HttpServletRequest request) {
        for (String name : FILTER_PARAMS) {
            String value = request.getParameter(name);
            if (value != null && !value.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private boolean isPublicPath(String uri) {
        for (String prefix : PUBLIC_PREFIXES) {
            if ("/".equals(prefix)) {
                if ("/".equals(uri)) return true;
            } else if (uri.equals(prefix) || uri.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }
}
