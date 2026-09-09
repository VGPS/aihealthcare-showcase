package com.wgblackmon.aihealthcare.infrastructure.delivery;

import com.wgblackmon.aihealthcare.domain.port.outbound.AdminNotificationPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;

/**
 * Email-backed adapter implementing {@link AdminNotificationPort}.
 *
 * <p>Sends administrative notification emails to the configured admin address
 * when system-level failures occur (e.g. LLM model unavailable). Uses the
 * same {@link JavaMailSender} infrastructure as the newsletter delivery system.
 *
 * <p>Email sending failures are logged but never thrown — notification is
 * best-effort and must not disrupt the calling service.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-10
 * @updated 2026-09-08 — ED-2 notifyScheduleDeactivated
 */
@Slf4j
@Component
public class AdminNotificationAdapter implements AdminNotificationPort {

    private final JavaMailSender mailSender;
    private final String         fromAddress;
    private final String         adminEmail;

    /**
     * Constructs the adapter with mail sender and configured addresses.
     *
     * @param mailSender  Spring-managed mail sender
     * @param fromAddress the newsletter from-address (reused for admin alerts)
     * @param adminEmail  the admin email address to receive notifications
     */
    public AdminNotificationAdapter(
            JavaMailSender mailSender,
            @Value("${aihealthcare.newsletter.from-address}") String fromAddress,
            @Value("${aihealthcare.admin.email:admin@aihealthcare.local}") String adminEmail) {
        log.debug("AdminNotificationAdapter() | fromAddress={}, adminEmail={}", fromAddress, adminEmail);
        this.mailSender  = mailSender;
        this.fromAddress = fromAddress;
        this.adminEmail  = adminEmail;
        log.debug("AdminNotificationAdapter() | return=void");
    }

    @Override
    public void notifyModelFailure(String providerName, String modelId, String query, Exception cause) {
        log.debug("notifyModelFailure() | providerName={}, modelId={}, query={}, cause={}",
                  providerName, modelId, query, cause.getClass().getSimpleName());

        String subject = "[AIHealthcare] Model failure: " + providerName + " (" + modelId + ")";

        StringWriter sw = new StringWriter();
        cause.printStackTrace(new PrintWriter(sw));

        String body = "An AI model synthesis call failed.\n\n"
                + "Provider:   " + providerName + "\n"
                + "Model ID:   " + modelId + "\n"
                + "Query:      " + query + "\n"
                + "Timestamp:  " + Instant.now() + "\n"
                + "Error:      " + cause.getMessage() + "\n\n"
                + "Stack trace:\n" + sw;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(adminEmail);
            helper.setSubject(subject);
            helper.setText(body);
            mailSender.send(message);
            log.info("notifyModelFailure() | admin notification sent to {} for provider '{}'",
                     adminEmail, providerName);
        } catch (MessagingException | MailException e) {
            log.error("notifyModelFailure() | failed to send admin notification: {}", e.getMessage());
        }

        log.debug("notifyModelFailure() | return=void");
    }

    @Override
    public void notifyScheduleDeactivated(String scheduleId, String ownerEmail, String lastError) {
        log.debug("notifyScheduleDeactivated() | scheduleId={}, ownerEmail=[REDACTED]", scheduleId);

        String subject = "[AIHealthcare] Push schedule deactivated: " + scheduleId;
        String body = "A push schedule was automatically deactivated due to consecutive failures.\n\n"
                + "Schedule ID:  " + scheduleId + "\n"
                + "Owner:        " + ownerEmail + "\n"
                + "Timestamp:    " + Instant.now() + "\n"
                + "Last error:   " + (lastError != null ? lastError : "(none)") + "\n\n"
                + "The schedule will not fire again until manually reactivated.";

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(adminEmail);
            helper.setSubject(subject);
            helper.setText(body);
            mailSender.send(message);
            log.info("notifyScheduleDeactivated() | admin notification sent for schedule '{}'", scheduleId);
        } catch (MessagingException | MailException e) {
            log.error("notifyScheduleDeactivated() | failed to send: {}", e.getMessage());
        }

        log.debug("notifyScheduleDeactivated() | return=void");
    }
}
