package com.wgblackmon.aihealthcare.web.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SeoModelAdvice}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-10
 * @updated 2026-09-10
 */
class SeoModelAdviceTest {

    private SeoModelAdvice advice;

    @BeforeEach
    void setUp() {
        advice = new SeoModelAdvice("https://app.bigskylabs.ai");
    }

    @Test
    void canonicalUrl_buildsCorrectly() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/directory/ada-health");

        String result = advice.canonicalUrl(request);

        assertThat(result).isEqualTo("https://app.bigskylabs.ai/directory/ada-health");
    }

    @Test
    void defaultDescription_isNonNull() {
        assertThat(advice.defaultDescription()).isNotNull().isNotEmpty();
    }

    @Test
    void noIndex_falseForPublicPaths() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/directory/ada-health");

        assertThat(advice.noIndex(request)).isFalse();
    }

    @Test
    void noIndex_trueForAuthPaths() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/dashboard/news");

        assertThat(advice.noIndex(request)).isTrue();
    }
}
