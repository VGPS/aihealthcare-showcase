package com.wgblackmon.aihealthcare.infrastructure.delivery;

import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRunStatus;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmailDeliveryAdapter}.
 *
 * <p>Verifies send-count behaviour and per-recipient error isolation using a
 * mocked {@link JavaMailSender}.  No real SMTP connection is made; MailHog
 * integration is validated manually during local development.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-13
 * @updated 2026-04-13
 */
@ExtendWith(MockitoExtension.class)
class EmailDeliveryAdapterTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailDeliveryAdapter adapter;

    private static final String FROM = "newsletter@aihealthcare.local";

    private static final NewsletterRun RUN = new NewsletterRun(
            "run-001",
            "AI in Healthcare Weekly",
            LocalDate.of(2026, 4, 13),
            "<html><body>HTML content</body></html>",
            "Plain text content",
            NewsletterRunStatus.DRAFT,
            Instant.parse("2026-04-13T08:00:00Z")
    );

    private static Subscriber subscriber(String email) {
        return new Subscriber(email, "Test User", true,
                              Instant.parse("2026-04-13T10:00:00Z"), null, null, null, null);
    }

    private static final String BASE_URL = "https://app.bigskylabs.ai";

    @BeforeEach
    void setUp() {
        adapter = new EmailDeliveryAdapter(mailSender, FROM, BASE_URL);
    }

    // -------------------------------------------------------------------------
    // deliver() — send count
    // -------------------------------------------------------------------------

    @Test
    void deliver_sendsOneMessagePerRecipient() {
        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);

        adapter.deliver(RUN, List.of(subscriber("a@example.com"), subscriber("b@example.com")));

        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void deliver_singleRecipient_sendsExactlyOneMessage() {
        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);

        adapter.deliver(RUN, List.of(subscriber("a@example.com")));

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void deliver_emptyRecipientList_sendsNothing() {
        adapter.deliver(RUN, List.of());

        verify(mailSender, never()).createMimeMessage();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // -------------------------------------------------------------------------
    // deliver() — per-recipient error isolation
    // -------------------------------------------------------------------------

    @Test
    void deliver_replacesUnsubscribePlaceholders() {
        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);

        NewsletterRun runWithPlaceholders = new NewsletterRun(
                "run-002", "Weekly", LocalDate.of(2026, 7, 31),
                "<a href=\"{{unsubscribe_url}}\">Unsub</a> <a href=\"{{preferences_url}}\">Prefs</a>",
                "Unsub: {{unsubscribe_url}} Prefs: {{preferences_url}}",
                NewsletterRunStatus.DRAFT, Instant.parse("2026-07-31T08:00:00Z"));

        Subscriber sub = new Subscriber("test@example.com", "Test", true,
                Instant.now(), null, "tok-abc", null, null);

        adapter.deliver(runWithPlaceholders, List.of(sub));

        // Verifies the message was sent (placeholder replacement doesn't throw)
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void deliver_oneRecipientFails_remainingRecipientsStillReceiveMail() {
        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);

        // First send fails, second should still be attempted
        org.mockito.Mockito.doThrow(new MailSendException("bad address"))
                           .doNothing()
                           .when(mailSender).send(any(MimeMessage.class));

        // Should not throw — errors per recipient are swallowed
        adapter.deliver(RUN, List.of(subscriber("bad@example.com"), subscriber("good@example.com")));

        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }
}
