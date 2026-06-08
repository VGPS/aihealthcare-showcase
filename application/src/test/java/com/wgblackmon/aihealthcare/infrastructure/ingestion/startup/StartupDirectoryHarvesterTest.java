package com.wgblackmon.aihealthcare.infrastructure.ingestion.startup;

import com.wgblackmon.aihealthcare.domain.model.Company;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StartupDirectoryHarvester} scraping logic.
 * Uses spy to override {@code fetchPage()} and inject canned HTML.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-07
 * @updated 2026-06-07
 */
@ExtendWith(MockitoExtension.class)
class StartupDirectoryHarvesterTest {

    private StartupDirectoryHarvester harvester;

    @BeforeEach
    void setUp() {
        harvester = spy(new StartupDirectoryHarvester());
    }

    @Test
    void scrapeYC_parsesCompanyCards() throws Exception {
        String html = """
                <html><body>
                <a href="/companies/scribebot">
                  <span class="leading-tight">ScribeBot</span>
                  <span class="leading-snug">AI medical scribe for clinics</span>
                </a>
                <a href="/companies/healthnav">
                  <span class="leading-tight">HealthNav</span>
                  <span class="leading-snug">AI care navigation platform</span>
                </a>
                </body></html>
                """;
        Document doc = Jsoup.parse(html);
        doReturn(doc).when(harvester).fetchPage(anyString());

        List<Company> companies = harvester.scrapeYC("https://yc.com/test", "YC Health Tech");

        assertThat(companies).hasSize(2);
        assertThat(companies.get(0).name()).isEqualTo("ScribeBot");
        assertThat(companies.get(0).description()).isEqualTo("AI medical scribe for clinics");
        assertThat(companies.get(0).source()).isEqualTo("YC Health Tech");
        assertThat(companies.get(0).url()).contains("/companies/scribebot");
    }

    @Test
    void scrapeYC_skipsIndustryLinks() throws Exception {
        String html = """
                <html><body>
                <a href="/companies/industry/health-tech">
                  <span class="leading-tight">Health Tech</span>
                </a>
                <a href="/companies">
                  <span class="leading-tight">All Companies</span>
                </a>
                <a href="/companies/realco">
                  <span class="leading-tight">RealCo</span>
                  <span class="leading-snug">A real company</span>
                </a>
                </body></html>
                """;
        Document doc = Jsoup.parse(html);
        doReturn(doc).when(harvester).fetchPage(anyString());

        List<Company> companies = harvester.scrapeYC("https://yc.com/test", "YC Health Tech");

        assertThat(companies).hasSize(1);
        assertThat(companies.get(0).name()).isEqualTo("RealCo");
    }

    @Test
    void scrapeYC_fetchFails_returnsEmpty() throws Exception {
        doThrow(new RuntimeException("Connection refused")).when(harvester).fetchPage(anyString());

        List<Company> companies = harvester.scrapeYC("https://yc.com/test", "YC Health Tech");

        assertThat(companies).isEmpty();
    }

    @Test
    void scrapeTopStartups_parsesCards() throws Exception {
        String html = """
                <html><body>
                <div class="startup-card">
                  <h3>MedBot</h3>
                  <p>AI assistant for doctors</p>
                  <a href="https://medbot.com">Visit</a>
                </div>
                </body></html>
                """;
        Document doc = Jsoup.parse(html);
        doReturn(doc).when(harvester).fetchPage(anyString());

        List<Company> companies = harvester.scrapeTopStartups("https://topstartups.io/test", "TopStartups Healthcare");

        assertThat(companies).hasSize(1);
        assertThat(companies.get(0).name()).isEqualTo("MedBot");
        assertThat(companies.get(0).source()).isEqualTo("TopStartups Healthcare");
    }

    @Test
    void scrapeTopStartups_fetchFails_returnsEmpty() throws Exception {
        doThrow(new RuntimeException("Timeout")).when(harvester).fetchPage(anyString());

        List<Company> companies = harvester.scrapeTopStartups("https://topstartups.io/test", "TopStartups AI");

        assertThat(companies).isEmpty();
    }

    @Test
    void getAnchorCompanies_returns8Anchors() {
        List<Company> anchors = harvester.getAnchorCompanies();

        assertThat(anchors).hasSize(8);
        for (Company anchor : anchors) {
            assertThat(anchor.source()).isEqualTo("anchor");
            assertThat(anchor.isAI()).isTrue();
            assertThat(anchor.isHealth()).isTrue();
        }
    }

    @Test
    void scrapeAll_combinesAllSources() throws Exception {
        // Mock all fetchPage calls to return empty pages (no companies scraped)
        Document emptyDoc = Jsoup.parse("<html><body></body></html>");
        doReturn(emptyDoc).when(harvester).fetchPage(anyString());

        List<Company> companies = harvester.scrapeAll();

        // Should still have anchors even if scraping finds nothing
        assertThat(companies).hasSizeGreaterThanOrEqualTo(8);
    }

    @Test
    void scrapeYC_blankName_skipped() throws Exception {
        String html = """
                <html><body>
                <a href="/companies/blank">
                  <span class="leading-tight">  </span>
                  <span class="leading-snug">No name company</span>
                </a>
                </body></html>
                """;
        Document doc = Jsoup.parse(html);
        doReturn(doc).when(harvester).fetchPage(anyString());

        List<Company> companies = harvester.scrapeYC("https://yc.com/test", "YC Health Tech");

        assertThat(companies).isEmpty();
    }
}
