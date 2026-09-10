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
 * and {@code noIndex} into every Thymeleaf model so the head fragment can render
 * favicon, meta description, canonical URL, Open Graph tags, and a conditional
 * noindex directive without each controller setting them individually.
 *
 * <p>Controllers may override {@code pageDescription} with a page-specific value;
 * the head fragment falls back to {@code defaultDescription} when absent.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-10
 * @updated 2026-09-10
 */
@Slf4j
@ControllerAdvice
public class SeoModelAdvice {

    private static final String DEFAULT_DESCRIPTION =
            "AI Healthcare Intelligence — daily market analysis, regulatory tracking, "
            + "company directory, and state legislation registry for AI in healthcare.";

    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/", "/about", "/pricing", "/press", "/login", "/register",
            "/directory", "/wiki", "/legislation", "/developer", "/privacy",
            "/choose-path", "/forgot-password", "/reset-password", "/error"
    );

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
        String result = baseUrl + uri;
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
        boolean isPublic = isPublicPath(uri);
        boolean result = !isPublic;
        log.debug("noIndex() | uri={}, return={}", uri, result);
        return result;
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
