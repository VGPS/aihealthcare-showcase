package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.Company;
import com.wgblackmon.aihealthcare.domain.model.CompanyDiscoveryResult;
import com.wgblackmon.aihealthcare.domain.model.CompanyEvent;
import com.wgblackmon.aihealthcare.domain.model.CompanyProfile;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.inbound.DiscoverCompaniesUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyEventPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyProfilePort;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;
import com.wgblackmon.aihealthcare.domain.service.CompanyProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thymeleaf controller serving company intelligence profile pages.
 *
 * <p>{@code GET /companies} renders the company index (all profiles sorted
 * by article count). {@code GET /companies/{slug}} renders the detail page
 * with event timeline.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
@Slf4j
@Controller
public class CompanyProfileController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy")
                    .withZone(ZoneId.of("America/New_York"));

    private final CompanyProfilePort companyProfilePort;
    private final CompanyEventPort companyEventPort;
    private final DiscoverCompaniesUseCase discoverCompaniesUseCase;
    private final CompanyProfileService companyProfileService;
    private final NewsArticleRepository newsArticleRepository;

    public CompanyProfileController(CompanyProfilePort companyProfilePort,
                                    CompanyEventPort companyEventPort,
                                    DiscoverCompaniesUseCase discoverCompaniesUseCase,
                                    CompanyProfileService companyProfileService,
                                    NewsArticleRepository newsArticleRepository) {
        log.debug("CompanyProfileController() | companyProfilePort={}, companyEventPort={}, discoverCompaniesUseCase={}, companyProfileService={}, newsArticleRepository={}",
                  companyProfilePort, companyEventPort, discoverCompaniesUseCase, companyProfileService, newsArticleRepository);
        this.companyProfilePort = companyProfilePort;
        this.companyEventPort = companyEventPort;
        this.discoverCompaniesUseCase = discoverCompaniesUseCase;
        this.companyProfileService = companyProfileService;
        this.newsArticleRepository = newsArticleRepository;
    }

    /**
     * Renders the company index page — all company profiles ordered by article count.
     *
     * @param model Thymeleaf model
     * @return the "company-index" view name
     */
    @GetMapping("/companies")
    public String index(Model model) {
        log.debug("index()");

        List<CompanyProfile> profiles = companyProfilePort.findAll();

        // Format timestamps server-side and compute real article counts
        Map<String, String> discoveredDates = new HashMap<>();
        Map<String, String> updatedDates = new HashMap<>();
        Map<String, Integer> realArticleCounts = new HashMap<>();
        for (CompanyProfile profile : profiles) {
            discoveredDates.put(profile.slug(), DISPLAY_FMT.format(profile.firstDiscoveredAt()));
            updatedDates.put(profile.slug(), DISPLAY_FMT.format(profile.lastUpdatedAt()));
            int count = newsArticleRepository.findRealArticlesByCompanyName(profile.name()).size();
            realArticleCounts.put(profile.slug(), count);
        }

        model.addAttribute("profiles", profiles);
        model.addAttribute("discoveredDates", discoveredDates);
        model.addAttribute("updatedDates", updatedDates);
        model.addAttribute("realArticleCounts", realArticleCounts);
        model.addAttribute("profileCount", profiles.size());

        log.debug("index() | return=company-index, profileCount={}", profiles.size());
        return "company-index";
    }

    /**
     * Runs the company discovery pipeline and creates/updates profiles from results.
     *
     * @param redirectAttributes flash attributes for the redirect
     * @return redirect to the company index
     */
    @PostMapping("/companies/discover")
    public String runDiscovery(RedirectAttributes redirectAttributes) {
        log.debug("runDiscovery()");

        try {
            CompanyDiscoveryResult result = discoverCompaniesUseCase.discover();
            int created = 0;
            int updated = 0;

            for (Company company : result.companies()) {
                String slug = companyProfileService.toSlug(company.name());
                Optional<CompanyProfile> existing = companyProfilePort.findBySlug(slug);

                // Build a synthetic article list from the company's URL
                List<NewsArticle> relatedArticles = new ArrayList<>();
                String articleId = "company-" + slug;
                NewsArticle syntheticArticle = new NewsArticle(
                        articleId, company.name(),
                        URI.create(company.url() != null ? company.url() : "https://example.com"),
                        company.description(), "New AI Healthcare Companies",
                        null, null, company.source(), "INDUSTRY", 0.5, Instant.now());
                relatedArticles.add(syntheticArticle);

                CompanyProfile profile = companyProfileService.upsertFromDiscovery(
                        company, relatedArticles, existing.orElse(null));
                companyProfilePort.save(profile);

                // Detect events from the synthetic article
                List<CompanyEvent> events = companyProfileService.detectEvents(profile, relatedArticles);
                for (CompanyEvent event : events) {
                    companyEventPort.save(event);
                }

                if (existing.isPresent()) {
                    updated++;
                } else {
                    created++;
                }
            }

            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("Discovery complete: %d companies found (%d new, %d updated)",
                            result.companies().size(), created, updated));
            log.info("runDiscovery() | created={}, updated={}, total={}", created, updated, result.companies().size());
        } catch (Exception e) {
            log.error("runDiscovery() | discovery pipeline failed", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Discovery pipeline failed: " + e.getMessage());
        }

        log.debug("runDiscovery() | return=redirect:/companies");
        return "redirect:/companies";
    }

    /**
     * Renders the company detail page with event timeline.
     *
     * @param slug  the company slug
     * @param model Thymeleaf model
     * @return the "company-detail" view name, or redirect to index if not found
     */
    @GetMapping("/companies/{slug}")
    public String detail(@PathVariable String slug, Model model) {
        log.debug("detail() | slug={}", slug);

        Optional<CompanyProfile> profileOpt = companyProfilePort.findBySlug(slug);
        if (profileOpt.isEmpty()) {
            log.debug("detail() | return=redirect:/companies (not found)");
            return "redirect:/companies";
        }

        CompanyProfile profile = profileOpt.get();
        List<CompanyEvent> events = companyEventPort.findByCompanySlug(slug);

        // Format event dates server-side
        Map<String, String> eventDates = new HashMap<>();
        for (CompanyEvent event : events) {
            if (event.occurredAt() != null) {
                eventDates.put(event.eventId(), DISPLAY_FMT.format(event.occurredAt()));
            }
        }

        // Fetch real news articles mentioning this company (excludes synthetic discovery entries)
        List<NewsArticleEntity> entities = newsArticleRepository.findRealArticlesByCompanyName(profile.name());
        List<NewsArticle> linkedArticles = new ArrayList<>();
        for (NewsArticleEntity e : entities) {
            String cleanBody = e.getBodyText();
            if (cleanBody != null) {
                cleanBody = cleanBody.replaceAll("<[^>]+>", " ")
                        .replace("&nbsp;", " ")
                        .replaceAll("&[a-zA-Z]+;", " ")
                        .replaceAll("\\s+", " ")
                        .trim();
            }
            linkedArticles.add(new NewsArticle(
                    e.getArticleId(), e.getTitle(),
                    URI.create(e.getUrl()), cleanBody, e.getTopic(),
                    e.getAuthor(), e.getTopicId(), e.getSourceName(),
                    e.getSourceTier(), e.getSourceWeight(),
                    e.getPublishedAt()));
        }

        // Format article dates server-side
        Map<String, String> articleDates = new HashMap<>();
        for (NewsArticle article : linkedArticles) {
            if (article.publishedAt() != null) {
                articleDates.put(article.articleId(), DISPLAY_FMT.format(article.publishedAt()));
            }
        }

        model.addAttribute("profile", profile);
        model.addAttribute("events", events);
        model.addAttribute("eventDates", eventDates);
        model.addAttribute("linkedArticles", linkedArticles);
        model.addAttribute("articleDates", articleDates);
        model.addAttribute("discoveredAt", DISPLAY_FMT.format(profile.firstDiscoveredAt()));
        model.addAttribute("updatedAt", DISPLAY_FMT.format(profile.lastUpdatedAt()));

        log.debug("detail() | return=company-detail, eventCount={}, articleCount={}", events.size(), linkedArticles.size());
        return "company-detail";
    }
}
