package com.wgblackmon.aihealthcare.infrastructure.delivery;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TransactionalEmailAdapter}.
 *
 * <p>Verifies that welcome and demo expiration emails are sent via
 * {@link JavaMailSender}. No real SMTP connection is made.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-31
 * @updated 2026-07-31
 */
@ExtendWith(MockitoExtension.class)
class TransactionalEmailAdapterTest {

    @Mock
    private JavaMailSender mailSender;

    private TransactionalEmailAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TransactionalEmailAdapter(mailSender, "no-reply@bigskylabs.ai", "https://app.bigskylabs.ai", "admin@bigskylabs.ai");
    }

    @Test
    void sendWelcome_sendsOneMessage() {
        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);

        adapter.sendWelcome("user@example.com", "Test User", 7);

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendDemoExpiration_sendsOneMessage() {
        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);

        adapter.sendDemoExpiration("user@example.com", "Test User");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void notifyAdminNewRegistration_sendsOneMessage() {
        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);

        adapter.notifyAdminNewRegistration("newuser@example.com", "New User", "DEMO");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendPasswordReset_sendsOneMessage() {
        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);

        adapter.sendPasswordReset("user@example.com", "Test User", "tok-1");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendWelcome_mailFailure_doesNotThrow() {
        MimeMessage mockMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mockMessage);
        org.mockito.Mockito.doThrow(new MailSendException("SMTP error"))
                .when(mailSender).send(any(MimeMessage.class));

        // Should not throw — failures are logged and swallowed
        adapter.sendWelcome("bad@example.com", "Test", 7);

        verify(mailSender).send(any(MimeMessage.class));
    }
}
