package com.wgblackmon.aihealthcare.infrastructure.ingestion.startup;

import com.wgblackmon.aihealthcare.domain.model.Company;
import com.wgblackmon.aihealthcare.domain.model.CompanyTags;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyScrapingPort;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Infrastructure adapter that scrapes AI-healthcare startup companies
 * from Y Combinator and TopStartups.io directories, then appends a
 * hand-curated set of anchor incumbents.
 *
 * <p>Implements {@link CompanyScrapingPort} so the domain service can
 * invoke scraping without coupling to jsoup or HTTP details.
 *
 * <p>CSS selectors are best-effort and may need adjustment if the target
 * sites change their DOM structure. All selectors are centralised as
 * constants at the top of this class for easy maintenance.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-07
 * @updated 2026-06-07
 */
@Slf4j
@Component
public class StartupDirectoryHarvester implements CompanyScrapingPort {

    // ---- YC URLs and selectors ----
    private static final String YC_HEALTH_TECH_URL =
            "https://www.ycombinator.com/companies/industry/health-tech";
    private static final String YC_HEALTHCARE_SERVICES_URL =
            "https://www.ycombinator.com/companies/industry/healthcare-services";

    private static final String YC_COMPANY_SELECTOR = "a[href^=/companies/]";
    private static final String YC_NAME_SELECTOR = "span.leading-tight";
    private static final String YC_DESC_SELECTOR = "span.leading-snug";

    // ---- TopStartups URLs and selectors ----
    private static final String TOP_STARTUPS_HEALTHCARE_URL =
            "https://topstartups.io/?industries=Healthcare";
    private static final String TOP_STARTUPS_AI_URL =
            "https://topstartups.io/?industries=Artificial+Intelligence";

    private static final String TS_COMPANY_SELECTOR = "div.startup-card, div.company-card, tr[data-href], a[href*=/company/]";
    private static final String TS_NAME_SELECTOR = "h3, h4, .company-name, .startup-name, td:first-child";
    private static final String TS_DESC_SELECTOR = "p, .description, .startup-description, td:nth-child(2)";

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final String USER_AGENT = "AIHealthcare-Monitor/1.0";

    @Override
    public List<Company> scrapeAll() {
        log.debug("scrapeAll() | starting startup directory harvest");

        List<Company> allCompanies = new ArrayList<>();

        // YC directories
        allCompanies.addAll(scrapeYC(YC_HEALTH_TECH_URL, "YC Health Tech"));
        allCompanies.addAll(scrapeYC(YC_HEALTHCARE_SERVICES_URL, "YC Healthcare Services"));

        // TopStartups directories
        allCompanies.addAll(scrapeTopStartups(TOP_STARTUPS_HEALTHCARE_URL, "TopStartups Healthcare"));
        allCompanies.addAll(scrapeTopStartups(TOP_STARTUPS_AI_URL, "TopStartups AI"));

        // Anchor incumbents
        allCompanies.addAll(getAnchorCompanies());

        log.debug("scrapeAll() | return=List[{}]", allCompanies.size());
        return allCompanies;
    }

    /**
     * Scrapes companies from a Y Combinator industry directory page.
     */
    List<Company> scrapeYC(String url, String sourceLabel) {
        log.debug("scrapeYC() | url={}, sourceLabel={}", url, sourceLabel);
        List<Company> companies = new ArrayList<>();

        try {
            Document doc = fetchPage(url);
            Elements cards = doc.select(YC_COMPANY_SELECTOR);
            log.info("scrapeYC() | found {} company cards from {}", cards.size(), sourceLabel);

            for (Element card : cards) {
                String href = card.attr("href");
                // Skip non-company links (e.g. /companies or /companies/industry/...)
                if (href.equals("/companies") || href.contains("/industry/")) {
                    continue;
                }

                String name = extractText(card, YC_NAME_SELECTOR);
                String description = extractText(card, YC_DESC_SELECTOR);

                if (name.isBlank()) {
                    continue;
                }

                String profileUrl = "https://www.ycombinator.com" + href;

                companies.add(new Company(
                        name, sourceLabel, profileUrl, null, description,
                        CompanyTags.none(), false, false
                ));
            }
        } catch (Exception e) {
            log.warn("scrapeYC() | failed to scrape {}: {}", sourceLabel, e.getMessage());
        }

        log.debug("scrapeYC() | return=List[{}]", companies.size());
        return companies;
    }

    /**
     * Scrapes companies from a TopStartups.io filtered page.
     */
    List<Company> scrapeTopStartups(String url, String sourceLabel) {
        log.debug("scrapeTopStartups() | url={}, sourceLabel={}", url, sourceLabel);
        List<Company> companies = new ArrayList<>();

        try {
            Document doc = fetchPage(url);
            Elements cards = doc.select(TS_COMPANY_SELECTOR);
            log.info("scrapeTopStartups() | found {} company cards from {}", cards.size(), sourceLabel);

            for (Element card : cards) {
                String name = extractText(card, TS_NAME_SELECTOR);
                String description = extractText(card, TS_DESC_SELECTOR);

                if (name.isBlank()) {
                    continue;
                }

                String profileUrl = "";
                String companySite = null;

                // Try to extract URLs from the card
                Element link = card.selectFirst("a[href]");
                if (link != null) {
                    String href = link.attr("abs:href");
                    if (href.contains("topstartups.io")) {
                        profileUrl = href;
                    } else if (!href.isBlank()) {
                        companySite = href;
                    }
                }

                // Fallback: data-href attribute
                if (profileUrl.isBlank() && card.hasAttr("data-href")) {
                    profileUrl = card.attr("data-href");
                    if (!profileUrl.startsWith("http")) {
                        profileUrl = "https://topstartups.io" + profileUrl;
                    }
                }

                companies.add(new Company(
                        name, sourceLabel, profileUrl, companySite, description,
                        CompanyTags.none(), false, false
                ));
            }
        } catch (Exception e) {
            log.warn("scrapeTopStartups() | failed to scrape {}: {}", sourceLabel, e.getMessage());
        }

        log.debug("scrapeTopStartups() | return=List[{}]", companies.size());
        return companies;
    }

    /**
     * Returns the hand-curated anchor incumbent companies.
     * Edit this list to add/remove/update established players.
     */
    List<Company> getAnchorCompanies() {
        log.debug("getAnchorCompanies() | loading anchor incumbents");

        List<Company> anchors = new ArrayList<>();

        anchors.add(new Company(
                "Nuance DAX", "anchor",
                "https://www.nuance.com/healthcare/dragon-ai-clinical-solutions.html",
                "https://www.nuance.com",
                "Microsoft-owned ambient AI clinical documentation platform used by major health systems.",
                new CompanyTags(true, false, false, false, false), true, true
        ));

        anchors.add(new Company(
                "Abridge", "anchor",
                "https://www.abridge.com",
                "https://www.abridge.com",
                "AI-powered medical conversation summarization for clinical documentation.",
                new CompanyTags(true, false, false, false, false), true, true
        ));

        anchors.add(new Company(
                "Viz.ai", "anchor",
                "https://www.viz.ai",
                "https://www.viz.ai",
                "AI-powered care coordination and medical imaging analysis for stroke, PE, and aortic disease.",
                new CompanyTags(false, false, true, false, false), true, true
        ));

        anchors.add(new Company(
                "Olive AI", "anchor",
                "https://oliveai.com",
                "https://oliveai.com",
                "AI-driven healthcare operations platform automating revenue cycle and administrative workflows.",
                new CompanyTags(false, false, false, true, true), true, true
        ));

        anchors.add(new Company(
                "Regard", "anchor",
                "https://www.withregard.com",
                "https://www.withregard.com",
                "AI copilot for physicians — automated diagnosis suggestions and clinical note generation.",
                new CompanyTags(true, true, false, false, false), true, true
        ));

        anchors.add(new Company(
                "Tempus", "anchor",
                "https://www.tempus.com",
                "https://www.tempus.com",
                "AI-enabled precision medicine platform with clinical and molecular data analytics.",
                new CompanyTags(false, false, false, false, true), true, true
        ));

        anchors.add(new Company(
                "Waystar", "anchor",
                "https://www.waystar.com",
                "https://www.waystar.com",
                "AI-powered revenue cycle management for claims, denials, and prior authorization.",
                new CompanyTags(false, false, false, true, false), true, true
        ));

        anchors.add(new Company(
                "Hippocratic AI", "anchor",
                "https://www.hippocraticai.com",
                "https://www.hippocraticai.com",
                "Safety-focused generative AI agents for healthcare staffing and patient outreach.",
                new CompanyTags(false, true, false, false, false), true, true
        ));

        log.debug("getAnchorCompanies() | return=List[{}]", anchors.size());
        return anchors;
    }

    /**
     * Fetches a page via jsoup. Package-private for test overriding.
     */
    Document fetchPage(String url) throws Exception {
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(CONNECT_TIMEOUT_MS)
                .get();
    }

    private String extractText(Element parent, String selector) {
        Element el = parent.selectFirst(selector);
        return el != null ? el.text().trim() : "";
    }
}
