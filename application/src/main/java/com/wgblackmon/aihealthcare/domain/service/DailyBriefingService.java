package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.AnalystNote;
import com.wgblackmon.aihealthcare.domain.model.CompanySentiment;
import com.wgblackmon.aihealthcare.domain.model.DailyBriefingData;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRunStatus;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItem;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItemType;
import com.wgblackmon.aihealthcare.domain.model.WatchlistMatch;
import com.wgblackmon.aihealthcare.domain.port.outbound.AnalystNotePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanySentimentPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterDeliveryPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistMatchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistPort;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates the personalized daily briefing pipeline for all eligible
 * subscribers (SUBSCRIBER and DEMO tiers).
 *
 * <p>For each subscriber, assembles recent watchlist matches, sentiment
 * data for watched companies, and recently modified analyst notes into
 * a {@link DailyBriefingData} record, renders it via
 * {@link DailyBriefingRenderer}, and delivers the personalized email
 * via {@link NewsletterDeliveryPort}.
 *
 * <p>Pure domain service with no Spring annotations. Wired as a
 * {@code @Bean} in {@code AppConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Slf4j
public class DailyBriefingService {

    private final SubscriberPort subscriberPort;
    private final WatchlistPort watchlistPort;
    private final WatchlistMatchPort watchlistMatchPort;
    private final CompanySentimentPort companySentimentPort;
    private final AnalystNotePort analystNotePort;
    private final NewsletterDeliveryPort newsletterDeliveryPort;
    private final DailyBriefingRenderer renderer;
    private final int lookbackHours;

    public DailyBriefingService(SubscriberPort subscriberPort,
                                 WatchlistPort watchlistPort,
                                 WatchlistMatchPort watchlistMatchPort,
                                 CompanySentimentPort companySentimentPort,
                                 AnalystNotePort analystNotePort,
                                 NewsletterDeliveryPort newsletterDeliveryPort,
                                 DailyBriefingRenderer renderer,
                                 int lookbackHours) {
        log.debug("DailyBriefingService() | lookbackHours={}", lookbackHours);
        this.subscriberPort = subscriberPort;
        this.watchlistPort = watchlistPort;
        this.watchlistMatchPort = watchlistMatchPort;
        this.companySentimentPort = companySentimentPort;
        this.analystNotePort = analystNotePort;
        this.newsletterDeliveryPort = newsletterDeliveryPort;
        this.renderer = renderer;
        this.lookbackHours = lookbackHours;
    }

    /**
     * Sends personalized daily briefing emails to all eligible subscribers.
     */
    public void sendDailyBriefings() {
        log.debug("sendDailyBriefings() | starting");

        List<Subscriber> subscribers = new ArrayList<>();
        List<Subscriber> subTier = subscriberPort.findAllActiveByTier(SubscriptionTier.SUBSCRIBER);
        for (Subscriber s : subTier) {
            subscribers.add(s);
        }
        List<Subscriber> demoTier = subscriberPort.findAllActiveByTier(SubscriptionTier.DEMO);
        for (Subscriber s : demoTier) {
            subscribers.add(s);
        }

        if (subscribers.isEmpty()) {
            log.info("sendDailyBriefings() | no eligible subscribers, skipping");
            log.debug("sendDailyBriefings() | return=void");
            return;
        }

        Instant since = Instant.now().minus(Duration.ofHours(lookbackHours));
        LocalDate today = LocalDate.now();

        int sentCount = 0;
        int skippedCount = 0;
        int errorCount = 0;

        for (Subscriber subscriber : subscribers) {
            try {
                boolean sent = sendBriefingForSubscriber(subscriber, since, today);
                if (sent) {
                    sentCount++;
                } else {
                    skippedCount++;
                }
            } catch (Exception ex) {
                errorCount++;
                log.error("sendDailyBriefings() | failed for subscriber={}: {}",
                        subscriber.email(), ex.getMessage(), ex);
            }
        }

        log.info("sendDailyBriefings() | complete: sent={}, skipped={}, errors={}",
                sentCount, skippedCount, errorCount);
        log.debug("sendDailyBriefings() | return=void");
    }

    private boolean sendBriefingForSubscriber(Subscriber subscriber, Instant since, LocalDate today) {
        log.debug("sendBriefingForSubscriber() | email={}", subscriber.email());

        String email = subscriber.email();

        // Load watchlist items
        List<WatchlistItem> watchlistItems = watchlistPort.findByUser(email);

        // Load recent notes
        List<AnalystNote> recentNotes = analystNotePort.findByUserSince(email, since);

        // Skip if no watchlist items AND no notes
        if (watchlistItems.isEmpty() && recentNotes.isEmpty()) {
            log.debug("sendBriefingForSubscriber() | return=false (no items and no notes)");
            return false;
        }

        // Load recent watchlist matches
        List<WatchlistMatch> recentMatches = watchlistMatchPort.findByUserSince(email, since);

        // Load sentiment for watched COMPANY items
        List<CompanySentiment> sentiments = new ArrayList<>();
        for (WatchlistItem item : watchlistItems) {
            if (item.itemType() == WatchlistItemType.COMPANY) {
                Optional<CompanySentiment> sentiment = companySentimentPort.findBySlug(item.value());
                if (sentiment.isPresent()) {
                    sentiments.add(sentiment.get());
                }
            }
        }

        // Assemble briefing data
        DailyBriefingData data = new DailyBriefingData(
                subscriber.name(),
                email,
                today,
                recentMatches,
                sentiments,
                recentNotes
        );

        // Render
        String html = renderer.renderHtml(data);
        String plainText = renderer.renderPlainText(data);

        // Build synthetic NewsletterRun
        String runId = "briefing-" + email.hashCode() + "-" + Instant.now().toEpochMilli();
        NewsletterRun run = new NewsletterRun(
                runId,
                "Your Daily AI Healthcare Briefing",
                today,
                html,
                plainText,
                NewsletterRunStatus.SENT,
                Instant.now()
        );

        // Deliver to this single subscriber
        newsletterDeliveryPort.deliver(run, List.of(subscriber));

        log.debug("sendBriefingForSubscriber() | return=true (delivered)");
        return true;
    }
}
