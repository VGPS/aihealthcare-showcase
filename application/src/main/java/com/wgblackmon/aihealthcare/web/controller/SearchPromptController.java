package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.SearchPromptConfig;
import com.wgblackmon.aihealthcare.domain.port.outbound.SearchPromptPort;
import com.wgblackmon.aihealthcare.web.dto.SearchPromptResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * REST controller exposing read access to search engine prompt configurations.
 *
 * <p>Provides two endpoints:
 * <ul>
 *   <li>{@code GET /api/v1/search-prompts} — returns all stored search prompt configs.</li>
 *   <li>{@code GET /api/v1/search-prompts/{engine}} — returns the config for a specific
 *       engine; responds with {@code 404} if not found.</li>
 * </ul>
 *
 * <p>The engine path variable is uppercased before lookup so that
 * {@code /api/v1/search-prompts/perplexity} and
 * {@code /api/v1/search-prompts/PERPLEXITY} both resolve correctly.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-28
 * @updated 2026-04-28
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/search-prompts")
public class SearchPromptController {

    private final SearchPromptPort searchPromptPort;

    public SearchPromptController(SearchPromptPort searchPromptPort) {
        log.debug("SearchPromptController() | searchPromptPort={}",
                  searchPromptPort.getClass().getSimpleName());
        this.searchPromptPort = searchPromptPort;
        log.debug("SearchPromptController() | return=void");
    }

    /**
     * Returns all search prompt configurations.
     *
     * @return {@code 200 OK} with a list of all configs (may be empty).
     */
    @GetMapping
    public ResponseEntity<List<SearchPromptResponse>> listAll() {
        log.debug("listAll()");
        List<SearchPromptConfig> configs = searchPromptPort.findAll();
        List<SearchPromptResponse> result = new ArrayList<>();
        for (SearchPromptConfig config : configs) {
            result.add(toResponse(config));
        }
        log.debug("listAll() | return={} items", result.size());
        return ResponseEntity.ok(result);
    }

    /**
     * Returns the search prompt configuration for the given engine.
     *
     * @param engine Engine identifier (case-insensitive, e.g. {@code "google"}).
     * @return {@code 200 OK} with the config, or {@code 404} if not found.
     */
    @GetMapping("/{engine}")
    public ResponseEntity<SearchPromptResponse> getByEngine(@PathVariable String engine) {
        log.debug("getByEngine() | engine={}", engine);
        Optional<SearchPromptConfig> config = searchPromptPort.findByEngine(engine.toUpperCase());
        if (config.isEmpty()) {
            log.debug("getByEngine() | return=404");
            return ResponseEntity.notFound().build();
        }
        SearchPromptResponse result = toResponse(config.get());
        log.debug("getByEngine() | return={}", result.engine());
        return ResponseEntity.ok(result);
    }

    // -------------------------------------------------------------------------
    // Mapping
    // -------------------------------------------------------------------------

    private SearchPromptResponse toResponse(SearchPromptConfig config) {
        log.debug("toResponse() | engine={}", config.engine());
        SearchPromptResponse result = new SearchPromptResponse(
                config.engine(),
                config.name(),
                config.templateText(),
                config.description(),
                config.active()
        );
        log.debug("toResponse() | return={}", result.engine());
        return result;
    }
}
