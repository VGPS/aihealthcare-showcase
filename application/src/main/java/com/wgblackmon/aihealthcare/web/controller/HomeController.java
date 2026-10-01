package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.infrastructure.persistence.HealthcareAiCompanyEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.HealthcareAiCompanyRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.StateLawEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.StateLawRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageIndexView;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Thymeleaf controller for the public homepage at {@code GET /}.
 *
 * <p>Authenticated users are redirected to the dashboard. Unauthenticated
 * visitors see the platform marketing homepage, including a "Latest
 * Intelligence" section (T9 internal linking) linking to the 5 newest wiki
 * pages, companies, state laws, and insight articles — spreading crawl budget
 * across the deep public sections instead of relying on the sitemap alone.
 *
 * @author  Bill Blackmon
 * @since   2026-08-15
 * @updated 2026-09-30 — T9 SEO: "Latest Intelligence" internal-linking section
 */
@Slf4j
@Controller
public class HomeController {

    private static final int LATEST_LIMIT = 5;

    private final WikiPageRepository wikiPageRepository;
    private final HealthcareAiCompanyRepository companyRepository;
    private final StateLawRepository stateLawRepository;
    private final InsightsController insightsController;

    public HomeController(WikiPageRepository wikiPageRepository,
                           HealthcareAiCompanyRepository companyRepository,
                           StateLawRepository stateLawRepository,
                           InsightsController insightsController) {
        log.debug("HomeController() | wikiPageRepository={}, companyRepository={}, stateLawRepository={}, insightsController={}",
                wikiPageRepository.getClass().getSimpleName(),
                companyRepository.getClass().getSimpleName(),
                stateLawRepository.getClass().getSimpleName(),
                insightsController.getClass().getSimpleName());
        this.wikiPageRepository = wikiPageRepository;
        this.companyRepository = companyRepository;
        this.stateLawRepository = stateLawRepository;
        this.insightsController = insightsController;
    }

    @GetMapping("/")
    public String home(Authentication auth, Model model) {
        log.debug("home() | auth={}", auth != null ? auth.getName() : "anonymous");
        if (auth != null && auth.isAuthenticated()) {
            log.debug("home() | return=redirect:/dashboard");
            return "redirect:/dashboard";
        }
        model.addAttribute("pageDescription",
                "AI Healthcare Intelligence — daily market analysis, regulatory tracking, company directory, and state legislation registry for AI in healthcare.");

        List<WikiPageIndexView> latestWikiPages = wikiPageRepository.findAllBy(
                PageRequest.of(0, LATEST_LIMIT, Sort.by("updatedAt").descending()));
        List<HealthcareAiCompanyEntity> recentCompanies = companyRepository.findTop5ByOrderByDiscoveredAtDesc();
        List<StateLawEntity> newestLaws = stateLawRepository.findTop5ByOrderByCreatedAtDesc();
        List<InsightsController.InsightEntry> latestInsights = insightsController.loadInsights();
        if (latestInsights.size() > LATEST_LIMIT) {
            latestInsights = latestInsights.subList(0, LATEST_LIMIT);
        }

        model.addAttribute("latestWikiPages", latestWikiPages);
        model.addAttribute("recentCompanies", recentCompanies);
        model.addAttribute("newestLaws", newestLaws);
        model.addAttribute("latestInsights", latestInsights);

        log.debug("home() | return=home");
        return "home";
    }
}
