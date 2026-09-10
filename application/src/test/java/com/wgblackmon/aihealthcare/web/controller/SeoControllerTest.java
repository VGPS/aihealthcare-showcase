package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import com.wgblackmon.aihealthcare.infrastructure.persistence.HealthcareAiCompanyEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.HealthcareAiCompanyRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.StateLawEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.StateLawRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for {@link SeoController} (robots.txt + sitemap.xml).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-10
 * @updated 2026-09-10
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
        when(companyRepository.findAll()).thenReturn(List.of());
        when(stateLawRepository.findAll()).thenReturn(List.of());
        when(wikiPageRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"));
    }

    @Test
    void sitemapXml_containsStaticPages() throws Exception {
        when(companyRepository.findAll()).thenReturn(List.of());
        when(stateLawRepository.findAll()).thenReturn(List.of());
        when(wikiPageRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/about")))
                .andExpect(content().string(containsString("/pricing")))
                .andExpect(content().string(containsString("/directory")))
                .andExpect(content().string(containsString("/legislation")))
                .andExpect(content().string(containsString("/wiki")));
    }

    @Test
    void sitemapXml_includesCompanyUrls() throws Exception {
        HealthcareAiCompanyEntity company = new HealthcareAiCompanyEntity();
        company.setSlug("ada-health");
        when(companyRepository.findAll()).thenReturn(List.of(company));
        when(stateLawRepository.findAll()).thenReturn(List.of());
        when(wikiPageRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/directory/ada-health")));
    }

    @Test
    void sitemapXml_includesLegislationAndWikiUrls() throws Exception {
        when(companyRepository.findAll()).thenReturn(List.of());

        StateLawEntity law = new StateLawEntity();
        law.setId("ca-ab-3030");
        when(stateLawRepository.findAll()).thenReturn(List.of(law));

        WikiPageEntity wiki = new WikiPageEntity();
        wiki.setSlug("fda-ai-guidance");
        when(wikiPageRepository.findAll()).thenReturn(List.of(wiki));

        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/legislation/ca-ab-3030")))
                .andExpect(content().string(containsString("/wiki/fda-ai-guidance")));
    }
}
