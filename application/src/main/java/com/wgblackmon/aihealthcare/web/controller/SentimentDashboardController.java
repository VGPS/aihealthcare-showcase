package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AnalystNote;
import com.wgblackmon.aihealthcare.domain.model.ArticleSentiment;
import com.wgblackmon.aihealthcare.domain.model.CompanySentiment;
import com.wgblackmon.aihealthcare.domain.model.NoteTargetType;
import com.wgblackmon.aihealthcare.domain.model.SentimentLabel;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.inbound.AnalyzeCompanySentimentUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.AnalystNotePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.security.Principal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thymeleaf controller that renders the company sentiment and risk dashboard.
 *
 * <p>Serves {@code GET /dashboard/risk} showing all tracked companies with
 * their sentiment scores, risk summaries, and distribution breakdowns.
 * Tier gating: FREE users see the top 5 companies; SUBSCRIBER/DEMO/ADMIN
 * see all.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-12
 */
@Slf4j
@Controller
public class SentimentDashboardController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a z")
                    .withZone(ZoneId.of("America/New_York"));

    private static final DateTimeFormatter NOTE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
                    .withZone(ZoneId.of("America/New_York"));

    private static final int FREE_COMPANY_LIMIT = 5;

    private final AnalyzeCompanySentimentUseCase sentimentUseCase;
    private final SubscriberPort subscriberPort;
    private final NewsArticleRepository articleRepository;
    private final AnalystNotePort analystNotePort;

    public SentimentDashboardController(AnalyzeCompanySentimentUseCase sentimentUseCase,
                                        SubscriberPort subscriberPort,
                                        NewsArticleRepository articleRepository,
                                        AnalystNotePort analystNotePort) {
        log.debug("SentimentDashboardController() | sentimentUseCase={}, subscriberPort={}, articleRepository={}, analystNotePort={}",
                  sentimentUseCase, subscriberPort, articleRepository, analystNotePort);
        this.sentimentUseCase = sentimentUseCase;
        this.subscriberPort = subscriberPort;
        this.articleRepository = articleRepository;
        this.analystNotePort = analystNotePort;
    }

    /**
     * Renders the risk dashboard overview showing all company sentiments.
     */
    @GetMapping("/dashboard/risk")
    public String riskDashboard(Principal principal, Model model) {
        log.debug("riskDashboard() | principal={}", principal != null ? principal.getName() : "anonymous");

        List<CompanySentiment> allSentiments = sentimentUseCase.getAll();
        boolean fullAccess = hasFullAccess(principal);

        List<CompanySentiment> sentiments;
        if (fullAccess) {
            sentiments = allSentiments;
        } else {
            sentiments = limitList(allSentiments, FREE_COMPANY_LIMIT);
        }

        // Build chart data
        List<String> chartLabels = new ArrayList<>();
        List<Double> chartScores = new ArrayList<>();
        List<String> chartColors = new ArrayList<>();
        for (CompanySentiment s : sentiments) {
            chartLabels.add(s.companyName());
            chartScores.add(s.sentimentScore());
            chartColors.add(colorForSentiment(s.overallSentiment()));
        }

        // Build formatted dates map
        List<String> formattedDates = new ArrayList<>();
        for (CompanySentiment s : sentiments) {
            formattedDates.add(DISPLAY_FMT.format(s.analyzedAt()));
        }

        // Summary counts
        int positiveCompanies = 0;
        int negativeCompanies = 0;
        int mixedCompanies = 0;
        int neutralCompanies = 0;
        for (CompanySentiment s : allSentiments) {
            switch (s.overallSentiment()) {
                case POSITIVE: positiveCompanies++; break;
                case NEGATIVE: negativeCompanies++; break;
                case MIXED:    mixedCompanies++;    break;
                case NEUTRAL:  neutralCompanies++;  break;
            }
        }

        // Build article metadata maps for inline expansion
        List<String> allArticleIds = new ArrayList<>();
        for (CompanySentiment s : sentiments) {
            for (ArticleSentiment a : s.articleSentiments()) {
                allArticleIds.add(a.articleId());
            }
        }
        Map<String, String> articleUrls = new HashMap<>();
        Map<String, String> articleSources = new HashMap<>();
        Map<String, String> articleDates = new HashMap<>();
        if (!allArticleIds.isEmpty()) {
            List<NewsArticleEntity> entities = articleRepository.findByArticleIdIn(allArticleIds);
            for (NewsArticleEntity entity : entities) {
                if (entity.getUrl() != null) {
                    articleUrls.put(entity.getArticleId(), entity.getUrl());
                }
                if (entity.getSourceName() != null) {
                    articleSources.put(entity.getArticleId(), entity.getSourceName());
                }
                if (entity.getPublishedAt() != null) {
                    articleDates.put(entity.getArticleId(), DISPLAY_FMT.format(entity.getPublishedAt()));
                }
            }
        }

        model.addAttribute("sentiments", sentiments);
        model.addAttribute("totalCompanies", allSentiments.size());
        model.addAttribute("positiveCompanies", positiveCompanies);
        model.addAttribute("negativeCompanies", negativeCompanies);
        model.addAttribute("mixedCompanies", mixedCompanies);
        model.addAttribute("neutralCompanies", neutralCompanies);
        model.addAttribute("chartLabels", chartLabels);
        model.addAttribute("chartScores", chartScores);
        model.addAttribute("chartColors", chartColors);
        model.addAttribute("formattedDates", formattedDates);
        model.addAttribute("articleUrls", articleUrls);
        model.addAttribute("articleSources", articleSources);
        model.addAttribute("articleDates", articleDates);
        model.addAttribute("fullAccess", fullAccess);
        model.addAttribute("hasSentiments", !sentiments.isEmpty());
        model.addAttribute("activePage", "risk");

        log.debug("riskDashboard() | return=risk-dashboard ({} companies)", sentiments.size());
        return "risk-dashboard";
    }

    /**
     * Renders the detail page for a single company's sentiment breakdown.
     */
    @GetMapping("/dashboard/risk/{slug}")
    public String companyRiskDetail(@PathVariable String slug,
                                     @RequestParam(defaultValue = "sentiment") String sort,
                                     Principal principal, Model model) {
        log.debug("companyRiskDetail() | slug={}, sort={}, principal={}", slug, sort,
                  principal != null ? principal.getName() : "anonymous");

        Optional<CompanySentiment> opt = sentimentUseCase.getBySlug(slug);
        if (opt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Company sentiment not found: " + slug);
        }

        CompanySentiment sentiment = opt.get();

        // Doughnut chart data for article distribution
        List<Integer> distributionData = new ArrayList<>();
        distributionData.add(sentiment.positiveCount());
        distributionData.add(sentiment.negativeCount());
        distributionData.add(sentiment.mixedCount());
        distributionData.add(sentiment.neutralCount());

        // Look up article metadata (URL, sourceName, publishedAt) from DB
        List<String> articleIds = new ArrayList<>();
        for (ArticleSentiment a : sentiment.articleSentiments()) {
            articleIds.add(a.articleId());
        }

        Map<String, String> articleUrls = new HashMap<>();
        Map<String, String> articleSources = new HashMap<>();
        Map<String, String> articleDates = new HashMap<>();

        if (!articleIds.isEmpty()) {
            List<NewsArticleEntity> entities = articleRepository.findByArticleIdIn(articleIds);
            for (NewsArticleEntity entity : entities) {
                if (entity.getUrl() != null) {
                    articleUrls.put(entity.getArticleId(), entity.getUrl());
                }
                if (entity.getSourceName() != null) {
                    articleSources.put(entity.getArticleId(), entity.getSourceName());
                }
                if (entity.getPublishedAt() != null) {
                    articleDates.put(entity.getArticleId(), DISPLAY_FMT.format(entity.getPublishedAt()));
                }
            }
        }

        // Sort articles
        List<ArticleSentiment> sortedArticles = new ArrayList<>(sentiment.articleSentiments());
        switch (sort) {
            case "title_asc":
                sortedArticles.sort(Comparator.comparing(ArticleSentiment::title, String.CASE_INSENSITIVE_ORDER));
                break;
            case "title_desc":
                sortedArticles.sort(Comparator.comparing(ArticleSentiment::title, String.CASE_INSENSITIVE_ORDER).reversed());
                break;
            case "confidence_desc":
                sortedArticles.sort(Comparator.comparingDouble(ArticleSentiment::confidence).reversed());
                break;
            case "confidence_asc":
                sortedArticles.sort(Comparator.comparingDouble(ArticleSentiment::confidence));
                break;
            case "sentiment_asc":
                sortedArticles.sort(Comparator.comparing(a -> a.sentiment().name()));
                break;
            case "sentiment_desc":
                sortedArticles.sort(Comparator.comparing((ArticleSentiment a) -> a.sentiment().name()).reversed());
                break;
            default:
                break;
        }

        model.addAttribute("sentiment", sentiment);
        model.addAttribute("sortedArticles", sortedArticles);
        model.addAttribute("analyzedAt", DISPLAY_FMT.format(sentiment.analyzedAt()));
        model.addAttribute("distributionData", distributionData);
        model.addAttribute("articleUrls", articleUrls);
        model.addAttribute("articleSources", articleSources);
        model.addAttribute("articleDates", articleDates);
        model.addAttribute("sort", sort);
        model.addAttribute("activePage", "risk");

        // Load analyst notes for this company
        List<AnalystNote> analystNotes = new ArrayList<>();
        Map<String, String> analystNoteDates = new HashMap<>();
        if (principal != null) {
            analystNotes = analystNotePort.findByUserAndTarget(
                    principal.getName(), NoteTargetType.COMPANY, slug);
            for (AnalystNote note : analystNotes) {
                Instant noteTime = note.updatedAt() != null ? note.updatedAt() : note.createdAt();
                analystNoteDates.put(note.noteId(), NOTE_FMT.format(noteTime));
            }
        }
        model.addAttribute("analystNotes", analystNotes);
        model.addAttribute("analystNoteDates", analystNoteDates);
        model.addAttribute("returnUrl", "/dashboard/risk/" + slug);

        log.debug("companyRiskDetail() | return=risk-detail for {} ({} articles, {} notes)", slug, sortedArticles.size(), analystNotes.size());
        return "risk-detail";
    }

    private boolean hasFullAccess(Principal principal) {
        if (principal == null) {
            return false;
        }
        if (isAdmin(principal)) {
            return true;
        }
        Optional<Subscriber> subscriber = subscriberPort.findByEmail(principal.getName());
        if (subscriber.isPresent()) {
            SubscriptionTier tier = subscriber.get().tier();
            return tier == SubscriptionTier.SUBSCRIBER || tier == SubscriptionTier.DEMO
                    || tier == SubscriptionTier.ENTERPRISE;
        }
        return false;
    }

    private boolean isAdmin(Principal principal) {
        if (principal instanceof Authentication auth) {
            for (GrantedAuthority authority : auth.getAuthorities()) {
                if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<CompanySentiment> limitList(List<CompanySentiment> list, int limit) {
        if (list.size() <= limit) {
            return list;
        }
        return new ArrayList<>(list.subList(0, limit));
    }

    private String colorForSentiment(SentimentLabel label) {
        switch (label) {
            case POSITIVE: return "#16a34a";
            case NEGATIVE: return "#dc2626";
            case MIXED:    return "#f59e0b";
            case NEUTRAL:  return "#6b7280";
            default:       return "#6b7280";
        }
    }
}
