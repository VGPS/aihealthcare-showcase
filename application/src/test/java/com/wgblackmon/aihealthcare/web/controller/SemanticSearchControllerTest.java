package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link SemanticSearchController}.
 *
 * <p>As of Slice 40, the Semantic Search page was merged into AI Search.
 * This controller now redirects {@code /research/search} to
 * {@code /research/ai-search}, preserving query parameters.
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-05-30
 * @updated 2026-06-06
 */
@Import(SecurityConfig.class)
@WebMvcTest(SemanticSearchController.class)
class SemanticSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ApiKeyPort apiKeyPort;

    // -------------------------------------------------------------------------
    // Authentication
    // -------------------------------------------------------------------------

    @Test
    void search_noAuth_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/research/search"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    // -------------------------------------------------------------------------
    // Redirect — no query
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "user@example.com")
    void search_noQuery_redirectsToAiSearch() throws Exception {
        mockMvc.perform(get("/research/search"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/research/ai-search"));
    }

    // -------------------------------------------------------------------------
    // Redirect — with query
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "user@example.com")
    void search_withQuery_redirectsWithQueryParam() throws Exception {
        mockMvc.perform(get("/research/search").param("q", "AI diagnostics"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/research/ai-search?q=AI diagnostics"));
    }

    // -------------------------------------------------------------------------
    // Redirect — with query and topK
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser(username = "user@example.com")
    void search_withQueryAndTopK_redirectsWithBothParams() throws Exception {
        mockMvc.perform(get("/research/search")
                        .param("q", "AI diagnostics")
                        .param("topK", "25"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/research/ai-search?q=AI diagnostics&topK=25"));
    }
}
