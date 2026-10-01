package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import com.wgblackmon.aihealthcare.infrastructure.persistence.HealthcareAiCompanyEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.HealthcareAiCompanyRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.StateLawEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.StateLawRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.TrendSnapshotRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for {@link SeoController} (robots.txt + sitemap index + child sitemaps).
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-09-10
 * @updated 2026-09-30 — updated for sitemap index split; real lastmod timestamps
 */
@Import(SecurityConfig.class)
@WebMvcTest(SeoController.class)
class SeoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiKeyPort apiKeyPort;

    @MockitoBean
    private HealthcareAiCompanyRepository companyRepository;

    @MockitoBean
    private StateLawRepository stateLawRepository;

    @MockitoBean
    private WikiPageRepository wikiPageRepository;

    @MockitoBean
    private TrendSnapshotRepository trendSnapshotRepository;

    @Test
    void robotsTxt_returnsTextPlain() throws Exception {
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"));
    }

    @Test
    void robotsTxt_containsSitemapLine() throws Exception {
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sitemap:")))
                .andExpect(content().string(containsString("/sitemap.xml")));
    }

    @Test
    void robotsTxt_disallowsDashboard() throws Exception {
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Disallow: /dashboard")))
                .andExpect(content().string(containsString("Disallow: /admin")))
                .andExpect(content().string(containsString("Disallow: /api/")));
    }

    @Test
    void robotsTxt_allowsPublicPaths() throws Exception {
        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Allow: /directory")))
                .andExpect(content().string(containsString("Allow: /wiki")))
                .andExpect(content().string(containsString("Allow: /legislation")));
    }

    @Test
    void sitemapXml_returnsXml() throws Exception {
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"));
    }

    @Test
    void sitemapIndex_containsChildSitemapLinks() throws Exception {
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<sitemapindex")))
                .andExpect(content().string(containsString("sitemap-pages.xml")))
                .andExpect(content().string(containsString("sitemap-wiki.xml")))
                .andExpect(content().string(containsString("sitemap-directory.xml")))
                .andExpect(content().string(containsString("sitemap-legislation.xml")))
                .andExpect(content().string(containsString("sitemap-trends.xml")))
                .andExpect(content().string(containsString("sitemap-insights.xml")));
    }

    @Test
    void sitemapPages_containsStaticPages() throws Exception {
        mockMvc.perform(get("/sitemap-pages.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/about")))
                .andExpect(content().string(containsString("/pricing")))
                .andExpect(content().string(containsString("/directory")))
                .andExpect(content().string(containsString("/legislation")))
                .andExpect(content().string(containsString("/wiki")));
    }

    @Test
    void sitemapDirectory_includesCompanyUrls() throws Exception {
        HealthcareAiCompanyEntity company = new HealthcareAiCompanyEntity();
        company.setSlug("ada-health");
        company.setDiscoveredAt(Instant.now());
        when(companyRepository.findAll()).thenReturn(List.of(company));

        mockMvc.perform(get("/sitemap-directory.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/directory/ada-health")));
    }

    @Test
    void sitemapLegislation_includesLawUrls() throws Exception {
        StateLawEntity law = new StateLawEntity();
        law.setId("ca-ab-3030");
        law.setUpdatedAt(Instant.now());
        when(stateLawRepository.findAll()).thenReturn(List.of(law));

        mockMvc.perform(get("/sitemap-legislation.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/legislation/ca-ab-3030")));
    }

    @Test
    void sitemapWiki_includesWikiUrls() throws Exception {
        WikiPageEntity wiki = new WikiPageEntity();
        wiki.setSlug("fda-ai-guidance");
        wiki.setCreatedAt(Instant.now());
        when(wikiPageRepository.findAll()).thenReturn(List.of(wiki));

        mockMvc.perform(get("/sitemap-wiki.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/wiki/fda-ai-guidance")));
    }

    @Test
    void indexNowKeyFile_matchingKey_returnsKeyText() throws Exception {
        mockMvc.perform(get("/bd4debfb9b3b028915758e01ef215e51.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string("bd4debfb9b3b028915758e01ef215e51"));
    }

    @Test
    void indexNowKeyFile_mismatchedKey_returns404() throws Exception {
        mockMvc.perform(get("/0000000000000000000000000000000.txt"))
                .andExpect(status().isNotFound());
    }
}
