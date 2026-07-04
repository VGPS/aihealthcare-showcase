package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.SearchPromptConfig;
import com.wgblackmon.aihealthcare.domain.port.outbound.SearchPromptPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link SearchPromptController}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-28
 * @updated 2026-04-28
 */
@Import(SecurityConfig.class)
@WithMockUser
@WebMvcTest(SearchPromptController.class)
class SearchPromptControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private SearchPromptPort searchPromptPort;

    private static final SearchPromptConfig GOOGLE = new SearchPromptConfig(
            "GOOGLE", "Google Broad Discovery",
            "Search for {topic} content.", "Discovery query.", true);

    private static final SearchPromptConfig PERPLEXITY = new SearchPromptConfig(
            "PERPLEXITY", "Perplexity Deep Research",
            "Find substantive content on {topic}.", "Deep research query.", true);

    @Test
    void listAll_returnsAllConfigs() throws Exception {
        when(searchPromptPort.findAll()).thenReturn(List.of(GOOGLE, PERPLEXITY));

        mockMvc.perform(get("/api/v1/search-prompts"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(2))
               .andExpect(jsonPath("$[0].engine").value("GOOGLE"))
               .andExpect(jsonPath("$[1].engine").value("PERPLEXITY"));
    }

    @Test
    void listAll_empty_returns200WithEmptyArray() throws Exception {
        when(searchPromptPort.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/search-prompts"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getByEngine_found_returnsConfig() throws Exception {
        when(searchPromptPort.findByEngine(eq("GOOGLE"))).thenReturn(Optional.of(GOOGLE));

        mockMvc.perform(get("/api/v1/search-prompts/google"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.engine").value("GOOGLE"))
               .andExpect(jsonPath("$.name").value("Google Broad Discovery"))
               .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void getByEngine_notFound_returns404() throws Exception {
        when(searchPromptPort.findByEngine(eq("UNKNOWN"))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/search-prompts/unknown"))
               .andExpect(status().isNotFound());
    }

    @Test
    void getByEngine_templateTextIncluded() throws Exception {
        when(searchPromptPort.findByEngine(eq("PERPLEXITY"))).thenReturn(Optional.of(PERPLEXITY));

        mockMvc.perform(get("/api/v1/search-prompts/perplexity"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.templateText").value("Find substantive content on {topic}."))
               .andExpect(jsonPath("$.description").value("Deep research query."));
    }
}
