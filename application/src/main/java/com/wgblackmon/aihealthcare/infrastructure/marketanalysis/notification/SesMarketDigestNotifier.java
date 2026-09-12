package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.notification;

import com.wgblackmon.aihealthcare.domain.marketanalysis.AffectedCompany;
import com.wgblackmon.aihealthcare.domain.marketanalysis.FactClassification;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigest;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestEntry;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDigestNotifier;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.TickerWatchlistRepository;
import com.wgblackmon.aihealthcare.web.controller.DisplayFormats;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SMTP-backed notifier implementing {@link MarketDigestNotifier}.
 *
 * <p>Sends a formatted HTML market alert email whenever the daily digest pipeline
 * produces at least one qualifying entry. In development the sender is pointed at
 * MailHog (localhost:1025, no auth) via {@code application-dev.yml}; in production
 * it routes through Amazon SES via {@code application-prod.yml} — no code changes
 * required between environments.
 *
 * <p>The notify address is read from {@code aihealthcare.market-analysis.notify-address}.
 * The from address reuses {@code aihealthcare.newsletter.from-address} so both the
 * newsletter and market alerts share the same verified SES sender identity.
 *
 * <p>Exceptions are logged at ERROR level but never rethrown — the pipeline caller
 * ({@link com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestService})
 * treats notification as best-effort and wraps this call in its own try-catch.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-09-12
 */
@Slf4j
@Component
public class SesMarketDigestNotifier implements MarketDigestNotifier {

    private static final DateTimeFormatter DATE_FMT = DisplayFormats.LONG_DATE;

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String notifyAddress;
    private final TickerWatchlistRepository watchlistRepository;

    public SesMarketDigestNotifier(
            JavaMailSender mailSender,
            @Value("${aihealthcare.newsletter.from-address}") String fromAddress,
            @Value("${aihealthcare.market-analysis.notify-address}") String notifyAddress,
            @Autowired(required = false) TickerWatchlistRepository watchlistRepository) {
        log.debug("SesMarketDigestNotifier() | fromAddress={}, notifyAddress={}, watchlistPresent={}",
                fromAddress, notifyAddress, watchlistRepository != null);
        this.mailSender            = mailSender;
        this.fromAddress           = fromAddress;
        this.notifyAddress         = notifyAddress;
        this.watchlistRepository   = watchlistRepository;
        log.debug("SesMarketDigestNotifier() | return=void");
    }

    @Override
    public void notify(MarketDigest digest) {
        log.debug("notify() | date={}, entries={}", digest.date(), digest.entries().size());

        sendDigestToRecipient(digest, notifyAddress);

        if (watchlistRepository != null) {
            sendFilteredDigestsToWatchlistSubscribers(digest);
        }

        log.debug("notify() | return=void");
    }

    private void sendDigestToRecipient(MarketDigest digest, String recipientAddress) {
        log.debug("sendDigestToRecipient() | to={}, entries={}", recipientAddress, digest.entries().size());
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(recipientAddress);
            helper.setSubject(buildSubject(digest));
            helper.setText(buildPlainText(digest), buildHtml(digest));

            mailSender.send(message);
            log.info("sendDigestToRecipient() | alert sent to {} — {} entries for {}",
                    recipientAddress, digest.entries().size(), digest.date());
        } catch (Exception e) {
            log.error("sendDigestToRecipient() | failed to send to {}: {}", recipientAddress, e.getMessage());
        }
        log.debug("sendDigestToRecipient() | return=void");
    }

    private void sendFilteredDigestsToWatchlistSubscribers(MarketDigest digest) {
        log.debug("sendFilteredDigestsToWatchlistSubscribers() | date={}", digest.date());

        List<String> subscriberIds = watchlistRepository.findAllSubscriberIds();
        int sent = 0;

        for (String subscriberId : subscriberIds) {
            if (subscriberId.equalsIgnoreCase(notifyAddress)) {
                continue;
            }

            List<String> watchedTickers = watchlistRepository.findWatchedTickers(subscriberId);
            if (watchedTickers.isEmpty()) {
                continue;
            }

            Set<String> tickerSet = new HashSet<>(watchedTickers);
            List<MarketDigestEntry> filtered = filterByTickers(digest.entries(), tickerSet);

            if (!filtered.isEmpty()) {
                MarketDigest filteredDigest = new MarketDigest(digest.date(), filtered, digest.generatedAt());
                sendDigestToRecipient(filteredDigest, subscriberId);
                sent++;
            }
        }

        log.info("sendFilteredDigestsToWatchlistSubscribers() | sent {} subscriber-filtered alerts", sent);
        log.debug("sendFilteredDigestsToWatchlistSubscribers() | return=void");
    }

    List<MarketDigestEntry> filterByTickers(List<MarketDigestEntry> entries, Set<String> tickers) {
        log.debug("filterByTickers() | entries={}, tickers={}", entries.size(), tickers.size());

        List<MarketDigestEntry> result = new ArrayList<>();
        for (MarketDigestEntry entry : entries) {
            for (AffectedCompany company : entry.affectedCompanies()) {
                if (company.tickerSymbol() != null && tickers.contains(company.tickerSymbol().toUpperCase())) {
                    result.add(entry);
                    break;
                }
            }
        }

        log.debug("filterByTickers() | return.size={}", result.size());
        return result;
    }

    // ─── private helpers ────────────────────────────────────────────────────

    String buildSubject(MarketDigest digest) {
        return "AI Healthcare Market Alert — " + DATE_FMT.format(digest.date());
    }

    String buildHtml(MarketDigest digest) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><body style=\"font-family:Arial,sans-serif;max-width:700px;margin:0 auto;\">");
        sb.append("<h2 style=\"color:#1a3a5c;\">AI Healthcare Market Alert</h2>");
        sb.append("<p style=\"color:#555;\">").append(DATE_FMT.format(digest.date())).append("</p>");
        sb.append("<p>").append(digest.entries().size())
          .append(" market-moving development(s) detected today:</p>");
        sb.append("<hr style=\"border:1px solid #e0e0e0;\">");

        for (MarketDigestEntry entry : digest.entries()) {
            String badgeColor = categoryColor(entry.newsItem().category().name());
            String factLabel = entry.factClassification() == FactClassification.CONFIRMED
                    ? "✓ Confirmed" : "⚠ Speculative";
            String factColor = entry.factClassification() == FactClassification.CONFIRMED
                    ? "#27ae60" : "#e67e22";

            sb.append("<div style=\"margin:20px 0;padding:16px;border-left:4px solid ")
              .append(badgeColor).append(";background:#fafafa;\">");
            sb.append("<div style=\"margin-bottom:8px;\">");
            sb.append("<span style=\"background:").append(badgeColor)
              .append(";color:#fff;padding:2px 8px;border-radius:3px;font-size:12px;\">")
              .append(formatCategory(entry.newsItem().category().name()))
              .append("</span>");
            sb.append("&nbsp;<span style=\"color:").append(factColor)
              .append(";font-size:12px;\">").append(factLabel).append("</span>");
            sb.append("&nbsp;<span style=\"color:#888;font-size:12px;\">Rank ")
              .append(entry.rank().value()).append("/5</span>");
            sb.append("</div>");
            sb.append("<h3 style=\"margin:8px 0;color:#1a3a5c;\">")
              .append(htmlEscape(entry.newsItem().headline())).append("</h3>");
            sb.append("<p style=\"color:#444;margin:8px 0;\">")
              .append(htmlEscape(entry.newsItem().summary())).append("</p>");
            sb.append("</div>");
        }

        sb.append("<hr style=\"border:1px solid #e0e0e0;\">");
        sb.append("<p style=\"color:#888;font-size:12px;\">")
          .append("AI Healthcare Intelligence | bigskylabs.ai</p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    String buildPlainText(MarketDigest digest) {
        StringBuilder sb = new StringBuilder();
        sb.append("AI Healthcare Market Alert — ").append(DATE_FMT.format(digest.date())).append("\n\n");
        sb.append(digest.entries().size()).append(" market-moving development(s) detected:\n\n");

        for (MarketDigestEntry entry : digest.entries()) {
            sb.append("─────────────────────────────────\n");
            sb.append("[").append(formatCategory(entry.newsItem().category().name())).append("]");
            sb.append(" | Rank ").append(entry.rank().value()).append("/5");
            sb.append(" | ").append(entry.factClassification().name()).append("\n");
            sb.append(entry.newsItem().headline()).append("\n");
            sb.append(entry.newsItem().summary()).append("\n\n");
        }

        sb.append("─────────────────────────────────\n");
        sb.append("AI Healthcare Intelligence | bigskylabs.ai\n");
        return sb.toString();
    }

    private String categoryColor(String category) {
        switch (category) {
            case "EARNINGS":         return "#2980b9";
            case "REGULATORY":       return "#8e44ad";
            case "FUNDING":          return "#27ae60";
            case "M_AND_A":          return "#e74c3c";
            case "MAJOR_PARTNERSHIP": return "#e67e22";
            default:                 return "#7f8c8d";
        }
    }

    private String formatCategory(String category) {
        switch (category) {
            case "EARNINGS":         return "Earnings";
            case "REGULATORY":       return "Regulatory";
            case "FUNDING":          return "Funding";
            case "M_AND_A":          return "M&A";
            case "MAJOR_PARTNERSHIP": return "Partnership";
            default:                 return "Other";
        }
    }

    private String htmlEscape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
