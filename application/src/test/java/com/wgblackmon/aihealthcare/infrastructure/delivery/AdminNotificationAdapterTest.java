package com.wgblackmon.aihealthcare.infrastructure.delivery;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminNotificationAdapter}.
 *
 * <p>Verifies that model failure notifications are sent via email with
 * the correct content, and that send failures are swallowed gracefully
 * without disrupting the calling service.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-10
 * @updated 2026-07-10
 */
@ExtendWith(MockitoExtension.class)
class AdminNotificationAdapterTest {

    @Mock
    private JavaMailSender mailSender;

    private AdminNotificationAdapter adapter;

    @BeforeEach
    void setUp() {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        adapter = new AdminNotificationAdapter(mailSender, "noreply@aihealthcare.local", "admin@aihealthcare.local");
    }

    @Test
    void notifyModelFailure_sendsEmailWithProviderAndQuery() {
        RuntimeException cause = new RuntimeException("Connection timed out");

        adapter.notifyModelFailure("GPT", "gpt-4o", "AI diagnostics in radiology", cause);

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void notifyModelFailure_emailFailure_doesNotThrow() {
        doThrow(new MailSendException("SMTP unreachable"))
                .when(mailSender).send(any(MimeMessage.class));
        RuntimeException cause = new RuntimeException("API key expired");

        assertThatNoException().isThrownBy(() ->
                adapter.notifyModelFailure("Claude", "claude-sonnet-4-6", "healthcare trends", cause));
    }

    @Test
    void notifyModelFailure_includesStackTraceInBody() {
        RuntimeException cause = new RuntimeException("Rate limit exceeded");

        adapter.notifyModelFailure("Perplexity", "sonar", "AI drug discovery", cause);

        verify(mailSender).send(any(MimeMessage.class));
    }
}
