package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.notification;

import com.wgblackmon.aihealthcare.domain.marketanalysis.AffectedCompany;
import com.wgblackmon.aihealthcare.domain.marketanalysis.FactClassification;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ImpactAssessment;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ImpactDimension;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ImpactDirection;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigest;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestEntry;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketImpactRank;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketNewsItem;
import com.wgblackmon.aihealthcare.domain.marketanalysis.NewsCategory;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SesMarketDigestNotifier}.
 *
 * <p>Verifies email construction, subject formatting, graceful error handling,
 * and delegation to {@link JavaMailSender}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@ExtendWith(MockitoExtension.class)
class SesMarketDigestNotifierTest {

    @Mock
    private JavaMailSender mailSender;

    private SesMarketDigestNotifier notifier;

    private static final LocalDate DATE = LocalDate.of(2026, 8, 19);
    private static final String FROM    = "newsletter@bigskylabs.ai";
    private static final String TO      = "alerts@bigskylabs.ai";

    @BeforeEach
    void setUp() {
        notifier = new SesMarketDigestNotifier(mailSender, FROM, TO);
    }

    @Test
    void notify_sendsEmailViaMailSender() {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        MarketDigest digest = digestWithOneEntry();

        notifier.notify(digest);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void notify_withEmptyDigest_stillSendsEmail() {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        notifier.notify(MarketDigest.empty(DATE));

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void notify_whenMailSenderThrows_doesNotPropagate() {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("SMTP connection refused"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> notifier.notify(digestWithOneEntry()))
                .doesNotThrowAnyException();
    }

    @Test
    void buildSubject_containsDateAndLabel() {
        String subject = notifier.buildSubject(MarketDigest.empty(DATE));

        assertThat(subject).contains("AI Healthcare Market Alert");
        assertThat(subject).contains("August 19, 2026");
    }

    @Test
    void buildHtml_containsHeadlineAndCategory() {
        MarketDigest digest = digestWithOneEntry();

        String html = notifier.buildHtml(digest);

        assertThat(html).contains("Earnings alert headline");
        assertThat(html).contains("Earnings");
    }

    @Test
    void buildPlainText_containsHeadlineAndRank() {
        MarketDigest digest = digestWithOneEntry();

        String text = notifier.buildPlainText(digest);

        assertThat(text).contains("Earnings alert headline");
        assertThat(text).contains("Rank 1/5");
    }

    @Test
    void notify_whenCreateMimeMessageThrows_doesNotPropagate() {
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("session error"));

        assertThatCode(() -> notifier.notify(digestWithOneEntry()))
                .doesNotThrowAnyException();

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private MarketDigest digestWithOneEntry() {
        MarketNewsItem item = new MarketNewsItem(
                "Earnings alert headline",
                "Company beat estimates by 12%.",
                List.of("https://example.com/earnings"),
                Instant.parse("2026-08-19T10:00:00Z"),
                NewsCategory.EARNINGS,
                null
        );
        MarketDigestEntry entry = new MarketDigestEntry(
                item,
                List.of(new ImpactAssessment(
                        ImpactDimension.REVENUE, ImpactDirection.POSITIVE, "Revenue up")),
                FactClassification.CONFIRMED,
                new MarketImpactRank(1),
                List.of(new AffectedCompany("HealthCorp", "HLTH", "earnings subject", null))
        );
        return new MarketDigest(DATE, List.of(entry), Instant.now());
    }
}
