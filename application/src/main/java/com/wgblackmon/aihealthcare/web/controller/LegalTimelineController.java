package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.inbound.MonitorRegulatoryEventsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.web.MetaDescriptionFetcher;
import com.wgblackmon.aihealthcare.infrastructure.persistence.RegulatoryEventEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.RegulatoryEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thymeleaf controller that renders the legal and regulatory timeline page.
 *
 * <p>Serves {@code GET /dashboard/legal} by merging three existing data sources
 * into a single chronological feed:
 * <ul>
 *   <li>"AI Healthcare Legal" articles → Litigation category</li>
 *   <li>"AI Healthcare Government Policy" articles → Policy category</li>
 *   <li>Regulatory events (FDA/CMS) → Regulation category</li>
 * </ul>
 *
 * <p>Supports category filtering (litigation/regulation/policy) and
 * a days-back window (30/90/180/365/0=all). Tier-gated: FREE users
 * are capped at 30 days; SUBSCRIBER/DEMO/ADMIN get unrestricted access.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-29
 * @updated 2026-07-29
 */
@Slf4j
@Controller
public class LegalTimelineController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy")
                    .withZone(ZoneId.of("America/New_York"));

    private static final int FREE_MAX_DAYS = 30;
    private static final int DEFAULT_EVENT_LIMIT = 200;

    private static final String TOPIC_LEGAL = "AI Healthcare Legal";
    private static final String TOPIC_POLICY = "AI Healthcare Government Policy";

    private static final Duration META_FETCH_TIMEOUT = Duration.ofSeconds(8);

    private final ArticleIngestionPort articleIngestionPort;
    private final MonitorRegulatoryEventsUseCase regulatoryUseCase;
    private final RegulatoryEventRepository regulatoryEventRepository;
    private final SubscriberPort subscriberPort;
    private final MetaDescriptionFetcher metaDescriptionFetcher;

    public LegalTimelineController(ArticleIngestionPort articleIngestionPort,
                                    MonitorRegulatoryEventsUseCase regulatoryUseCase,
                                    RegulatoryEventRepository regulatoryEventRepository,
                                    SubscriberPort subscriberPort,
                                    MetaDescriptionFetcher metaDescriptionFetcher) {
        log.debug("LegalTimelineController() | articleIngestionPort={}, regulatoryUseCase={}, " +
                  "regulatoryEventRepository={}, subscriberPort={}, metaDescriptionFetcher={}",
                  articleIngestionPort, regulatoryUseCase, regulatoryEventRepository,
                  subscriberPort, metaDescriptionFetcher);
        this.articleIngestionPort = articleIngestionPort;
        this.regulatoryUseCase = regulatoryUseCase;
        this.regulatoryEventRepository = regulatoryEventRepository;
        this.subscriberPort = subscriberPort;
        this.metaDescriptionFetcher = metaDescriptionFetcher;
    }

    /**
     * Renders the legal and regulatory timeline page.
     *
     * @param filter    optional category filter: "litigation", "regulation", "policy", or null for all
     * @param days      lookback window in days (default 90; 0 = all time)
     * @param principal the authenticated user, or null for anonymous
     * @param model     Thymeleaf model
     * @return the "legal-timeline" view name
     */
    @GetMapping("/dashboard/legal")
    public String legalTimeline(@RequestParam(required = false) String filter,
                                 @RequestParam(defaultValue = "90") int days,
                                 Principal principal,
                                 Model model) {
        log.debug("legalTimeline() | filter={}, days={}, principal={}", filter, days,
                  principal != null ? principal.getName() : "anonymous");

        SubscriptionTier tier = resolveTier(principal);
        boolean fullAccess = tier == SubscriptionTier.SUBSCRIBER
                || tier == SubscriptionTier.DEMO
                || isAdmin(principal);

        // Clamp FREE users to 30 days max
        if (!fullAccess && (days == 0 || days > FREE_MAX_DAYS)) {
            days = FREE_MAX_DAYS;
        }

        List<TimelineEntry> combined = new ArrayList<>();

        // Load articles from both topics
        List<NewsArticle> legalArticles = List.of();
        List<NewsArticle> policyArticles = List.of();

        if (filter == null || "litigation".equalsIgnoreCase(filter)) {
            legalArticles = articleIngestionPort.fetchByTopicWithArchiveLimit(TOPIC_LEGAL, days);
        }
        if (filter == null || "policy".equalsIgnoreCase(filter)) {
            policyArticles = articleIngestionPort.fetchByTopicWithArchiveLimit(TOPIC_POLICY, days);
        }

        // Prefetch meta descriptions for articles with empty/duplicate body text
        List<String> urlsToFetch = new ArrayList<>();
        for (NewsArticle article : legalArticles) {
            if (article.url() != null && needsMetaDescription(article)) {
                urlsToFetch.add(article.url().toString());
            }
        }
        for (NewsArticle article : policyArticles) {
            if (article.url() != null && needsMetaDescription(article)) {
                urlsToFetch.add(article.url().toString());
            }
        }
        if (!urlsToFetch.isEmpty()) {
            metaDescriptionFetcher.prefetch(urlsToFetch, META_FETCH_TIMEOUT);
        }

        // Build timeline entries
        for (NewsArticle article : legalArticles) {
            combined.add(toEntry(article, "Litigation", "bg-red-100 text-red-800"));
        }
        for (NewsArticle article : policyArticles) {
            combined.add(toEntry(article, "Policy", "bg-amber-100 text-amber-800"));
        }

        // Load regulatory events
        if (filter == null || "regulation".equalsIgnoreCase(filter)) {
            List<RegulatoryEventEntity> regEntities;
            if (days > 0) {
                Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
                regEntities = regulatoryEventRepository.findByDiscoveredAtAfterOrderByDiscoveredAtDesc(cutoff);
            } else {
                regEntities = regulatoryEventRepository.findAllByOrderByDiscoveredAtDesc();
            }
            for (RegulatoryEventEntity entity : regEntities) {
                combined.add(toEntry(entity));
            }
        }

        // Deduplicate: same title on same day keeps only the latest timestamp
        combined = deduplicateTimelineByTitleAndDay(combined);

        // Sort newest first
        combined.sort(Comparator.comparing(TimelineEntry::sortInstant).reversed());

        // Category counts for summary badges
        int litigationCount = 0;
        int regulationCount = 0;
        int policyCount = 0;
        for (TimelineEntry entry : combined) {
            if ("Litigation".equals(entry.category())) {
                litigationCount++;
            } else if ("Regulation".equals(entry.category())) {
                regulationCount++;
            } else if ("Policy".equals(entry.category())) {
                policyCount++;
            }
        }

        model.addAttribute("entries", combined);
        model.addAttribute("entryCount", combined.size());
        model.addAttribute("litigationCount", litigationCount);
        model.addAttribute("regulationCount", regulationCount);
        model.addAttribute("policyCount", policyCount);
        model.addAttribute("filter", filter);
        model.addAttribute("days", days);
        model.addAttribute("fullAccess", fullAccess);

        log.debug("legalTimeline() | return=legal-timeline, entryCount={}", combined.size());
        return "legal-timeline";
    }

    private TimelineEntry toEntry(NewsArticle article, String category, String badgeClass) {
        Instant sortInstant = article.publishedAt() != null ? article.publishedAt() : Instant.EPOCH;
        String formattedDate = sortInstant.equals(Instant.EPOCH) ? "" : DISPLAY_FMT.format(sortInstant);
        String url = article.url() != null ? article.url().toString() : null;

        String rawTitle = article.title();
        String displayTitle = rawTitle;
        String publisher = null;

        // Google News titles use "Headline - Publisher" format
        int dashIdx = rawTitle.lastIndexOf(" - ");
        if (dashIdx > 0 && dashIdx < rawTitle.length() - 3) {
            displayTitle = rawTitle.substring(0, dashIdx).trim();
            publisher = rawTitle.substring(dashIdx + 3).trim();
        }

        // Build snippet: suppress if body text just repeats the title
        String snippet = buildSnippet(article.bodyText(), rawTitle, 200);

        // Fall back to cached meta description from the publisher's page
        if (snippet.isEmpty() && url != null) {
            String metaDesc = metaDescriptionFetcher.getCached(url);
            if (metaDesc != null && !metaDesc.isBlank()) {
                snippet = buildSnippet(metaDesc, rawTitle, 200);
            }
        }

        return new TimelineEntry(
                article.articleId(), sortInstant, formattedDate, displayTitle,
                url, category, badgeClass, publisher, snippet);
    }

    private TimelineEntry toEntry(RegulatoryEventEntity entity) {
        Instant sortInstant = entity.getPublishedAt() != null
                ? entity.getPublishedAt() : entity.getDiscoveredAt();
        String formattedDate = DISPLAY_FMT.format(sortInstant);
        String sourceLabel = entity.getRegulatoryBody() != null ? entity.getRegulatoryBody() : "Regulatory";
        String snippet = buildSnippet(entity.getSummary(), 200);

        return new TimelineEntry(
                entity.getEventId(), sortInstant, formattedDate, entity.getTitle(),
                entity.getSourceUrl(), "Regulation", "bg-blue-100 text-blue-800",
                sourceLabel, snippet);
    }

    private boolean needsMetaDescription(NewsArticle article) {
        String body = article.bodyText();
        if (body == null || body.isBlank()) {
            return true;
        }
        String stripped = body.replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (stripped.isBlank()) {
            return true;
        }
        // Body just repeats the title
        String normalizedBody = stripped.toLowerCase().replaceAll("[^a-z0-9]", "");
        String normalizedTitle = article.title().toLowerCase().replaceAll("[^a-z0-9]", "");
        return normalizedBody.equals(normalizedTitle)
                || normalizedTitle.contains(normalizedBody)
                || normalizedBody.contains(normalizedTitle);
    }

    private String buildSnippet(String text, String title, int maxLength) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String stripped = text.replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
        if (stripped.isBlank()) {
            return "";
        }
        // Suppress snippet if it just repeats the title
        if (title != null) {
            String normalizedSnippet = stripped.toLowerCase().replaceAll("[^a-z0-9]", "");
            String normalizedTitle = title.toLowerCase().replaceAll("[^a-z0-9]", "");
            if (normalizedSnippet.equals(normalizedTitle) || normalizedTitle.contains(normalizedSnippet)
                    || normalizedSnippet.contains(normalizedTitle)) {
                return "";
            }
        }
        if (stripped.length() <= maxLength) {
            return stripped;
        }
        return stripped.substring(0, maxLength) + "...";
    }

    private String buildSnippet(String text, int maxLength) {
        return buildSnippet(text, null, maxLength);
    }

    private SubscriptionTier resolveTier(Principal principal) {
        log.debug("resolveTier() | principal={}", principal != null ? principal.getName() : "null");
        if (principal == null) {
            log.debug("resolveTier() | return={}", SubscriptionTier.FREE);
            return SubscriptionTier.FREE;
        }
        if (isAdmin(principal)) {
            log.debug("resolveTier() | ADMIN role detected, return={}", SubscriptionTier.SUBSCRIBER);
            return SubscriptionTier.SUBSCRIBER;
        }
        Optional<Subscriber> subscriber = subscriberPort.findByEmail(principal.getName());
        SubscriptionTier result = subscriber.map(Subscriber::tier).orElse(SubscriptionTier.FREE);
        log.debug("resolveTier() | return={}", result);
        return result;
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

    /**
     * Presentation record for a single timeline entry, merging articles and
     * regulatory events into a common display structure.
     */
    record TimelineEntry(
            String id,
            Instant sortInstant,
            String formattedDate,
            String title,
            String url,
            String category,
            String categoryBadgeClass,
            String sourceLabel,
            String snippet
    ) {}

    private List<TimelineEntry> deduplicateTimelineByTitleAndDay(List<TimelineEntry> entries) {
        log.debug("deduplicateTimelineByTitleAndDay() | inputSize={}", entries.size());

        Map<String, TimelineEntry> bestByTitleDay = new LinkedHashMap<>();
        List<TimelineEntry> noDate = new ArrayList<>();

        for (TimelineEntry entry : entries) {
            if (entry.title() == null || entry.title().isBlank()
                    || entry.sortInstant() == null) {
                noDate.add(entry);
                continue;
            }
            String normalizedTitle = entry.title().toLowerCase().trim();
            String dayStr = entry.sortInstant().toString().substring(0, 10);
            String dayKey = normalizedTitle + "|" + dayStr;

            TimelineEntry existing = bestByTitleDay.get(dayKey);
            if (existing == null || entry.sortInstant().isAfter(existing.sortInstant())) {
                bestByTitleDay.put(dayKey, entry);
            }
        }

        List<TimelineEntry> result = new ArrayList<>(bestByTitleDay.values());
        result.addAll(noDate);

        log.debug("deduplicateTimelineByTitleAndDay() | return size={} (removed {})",
                result.size(), entries.size() - result.size());
        return result;
    }
}
