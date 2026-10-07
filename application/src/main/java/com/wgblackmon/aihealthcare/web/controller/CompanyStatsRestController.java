package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.port.inbound.BrowseCompaniesUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public REST endpoint exposing live aggregate statistics about the AI healthcare
 * company directory. Requires no authentication — intended for use in marketing copy,
 * social post generation scripts, and external integrations that need a live company
 * count without scraping the HTML directory page.
 *
 * <p>The count reflects all companies currently persisted in the directory, updated
 * continuously as the company discovery pipeline runs. Because the number grows over
 * time it should always be fetched live rather than hardcoded in templates or copy.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-07
 * @updated 2026-10-07
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/companies")
public class CompanyStatsRestController {

    private final BrowseCompaniesUseCase browseCompaniesUseCase;

    public CompanyStatsRestController(BrowseCompaniesUseCase browseCompaniesUseCase) {
        this.browseCompaniesUseCase = browseCompaniesUseCase;
    }

    /**
     * Returns the live count of AI healthcare companies in the directory.
     *
     * <p>Response: {@code {"count": 627}}
     * <p>No authentication required — safe to call from scripts, social post
     * generators, and public integrations.
     */
    @GetMapping("/count")
    public Map<String, Integer> count() {
        log.debug("count()");
        int count = browseCompaniesUseCase.listCompanies().size();
        Map<String, Integer> result = Map.of("count", count);
        log.debug("count() | return={}", result);
        return result;
    }
}
