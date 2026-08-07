package com.wgblackmon.aihealthcare.infrastructure.delivery;

import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.service.LogSanitizer;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterDeliveryPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * SMTP-backed adapter implementing {@link NewsletterDeliveryPort} via Spring's
 * {@link JavaMailSender}.
 *
 * <p>In local development the mail sender is pointed at a MailHog Docker
 * container ({@code localhost:1025}, no auth) via {@code application-dev.yml}.
 * In production it is pointed at Amazon SES via {@code application-prod.yml}
 * — no code changes are required to switch; only the active Spring profile changes.
 *
 * <p>Each recipient receives an individual {@link MimeMessage} containing both
 * the HTML and plain-text newsletter content, so mail clients that do not support
 * HTML fall back gracefully.  Delivery is best-effort per recipient: if sending
 * to one address fails the error is logged at {@code ERROR} level and processing
 * continues with the remaining recipients.
 *
 * <p>The sender address is read from
 * {@code aihealthcare.newsletter.from-address} in {@code application.yml}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-13
 * @updated 2026-08-07
 */
@Slf4j
@Component
public class EmailDeliveryAdapter implements NewsletterDeliveryPort {

    private final JavaMailSender mailSender;
    private final String         fromAddress;
    private final String         baseUrl;

    public EmailDeliveryAdapter(
            JavaMailSender mailSender,
            @Value("${aihealthcare.newsletter.from-address}") String fromAddress,
            @Value("${aihealthcare.base-url}") String baseUrl) {
        log.debug("EmailDeliveryAdapter() | fromAddress={}, baseUrl={}", fromAddress, baseUrl);
        this.mailSender  = mailSender;
        this.fromAddress = fromAddress;
        this.baseUrl     = baseUrl;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Iterates through {@code recipients} and sends one {@link MimeMessage}
     * per subscriber.  Failures for individual recipients are caught, logged, and
     * skipped — the overall batch is never aborted by a single bad address.
     */
    @Override
    public void deliver(NewsletterRun run, List<Subscriber> recipients) {
        log.debug("deliver() | runId={}, recipientCount={}", run.runId(), recipients.size());

        int successCount = 0;
        int failureCount = 0;

        for (Subscriber recipient : recipients) {
            try {
                sendToRecipient(run, recipient);
                successCount++;
                log.debug("deliver() | Sent to email={}", recipient.email());
            } catch (MessagingException | MailException ex) {
                failureCount++;
                log.error("deliver() | Failed to send to email={}: {}", LogSanitizer.maskEmail(recipient.email()), ex.getMessage());
            }
        }

        log.info("deliver() | Delivery complete: runId={}, sent={}, failed={}",
                 run.runId(), successCount, failureCount);
        log.debug("deliver() | return=void");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds and sends one multipart MIME message to a single recipient.
     *
     * @param run       The newsletter run supplying the subject and body content.
     * @param recipient The subscriber to send to.
     * @throws MessagingException if constructing or addressing the message fails.
     * @throws MailException      if the SMTP transport layer rejects the message.
     */
    private void sendToRecipient(NewsletterRun run, Subscriber recipient)
            throws MessagingException {
        log.debug("sendToRecipient() | runId={}, email={}", run.runId(), recipient.email());

        String html = replacePlaceholders(run.htmlContent(), recipient);
        String plain = replacePlaceholders(run.plainTextContent(), recipient);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromAddress);
        helper.setTo(recipient.email());
        helper.setSubject(run.title());
        helper.setText(plain, html);

        mailSender.send(message);

        log.debug("sendToRecipient() | return=void");
    }

    /**
     * Replaces per-recipient placeholders in newsletter content.
     *
     * @param content   the raw HTML or plain-text content.
     * @param recipient the subscriber whose tokens should be substituted.
     * @return the content with placeholders replaced.
     */
    private String replacePlaceholders(String content, Subscriber recipient) {
        log.debug("replacePlaceholders() | email={}", recipient.email());

        if (content == null) {
            log.debug("replacePlaceholders() | return=null");
            return null;
        }

        String result = content;

        String unsubscribeUrl = recipient.unsubscribeToken() != null
                ? baseUrl + "/unsubscribe?token=" + recipient.unsubscribeToken()
                : baseUrl + "/profile";
        result = result.replace("{{unsubscribe_url}}", unsubscribeUrl);
        result = result.replace("{{preferences_url}}", baseUrl + "/profile");

        log.debug("replacePlaceholders() | return=(replaced)");
        return result;
    }
}
