package com.wgblackmon.aihealthcare.infrastructure.delivery;

import com.wgblackmon.aihealthcare.domain.port.outbound.TransactionalEmailPort;
import com.wgblackmon.aihealthcare.domain.service.LogSanitizer;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * SMTP-backed adapter for transactional emails (welcome, demo expiration).
 *
 * <p>Uses {@link JavaMailSender} to send individual HTML emails with inline CSS.
 * In development the mail sender targets MailHog; in production it targets
 * Amazon SES — same as {@link EmailDeliveryAdapter}.
 *
 * <p>Failures are caught and logged at {@code ERROR} level rather than thrown,
 * so transactional email issues never block the triggering operation (registration,
 * demo expiration).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-31
 * @updated 2026-08-07
 */
@Slf4j
@Component
public class TransactionalEmailAdapter implements TransactionalEmailPort {

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String baseUrl;
    private final String adminEmail;

    public TransactionalEmailAdapter(
            JavaMailSender mailSender,
            @Value("${aihealthcare.newsletter.from-address}") String fromAddress,
            @Value("${aihealthcare.base-url}") String baseUrl,
            @Value("${aihealthcare.admin.email}") String adminEmail) {
        log.debug("TransactionalEmailAdapter() | fromAddress={}, baseUrl={}, adminEmail={}", fromAddress, baseUrl, adminEmail);
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.baseUrl = baseUrl;
        this.adminEmail = adminEmail;
    }

    @Override
    public void sendWelcome(String email, String name, int demoDays) {
        log.debug("sendWelcome() | email={}, name={}, demoDays={}", email, name, demoDays);

        String subject = "Welcome to AI Healthcare Intelligence!";
        String html = buildWelcomeHtml(name, demoDays);
        String plain = "Welcome to AI Healthcare Intelligence, " + name + "!\n\n"
                + "Your " + demoDays + "-day demo is now active. "
                + "Explore features at " + baseUrl + "/dashboard\n\n"
                + "Upgrade anytime at " + baseUrl + "/pricing";

        sendEmail(email, subject, html, plain);
        log.debug("sendWelcome() | return=void");
    }

    @Override
    public void sendDemoExpiration(String email, String name) {
        log.debug("sendDemoExpiration() | email={}, name={}", email, name);

        String subject = "Your AI Healthcare Intelligence Demo Has Expired";
        String html = buildExpirationHtml(name);
        String plain = "Hi " + name + ",\n\n"
                + "Your demo period has ended. "
                + "Upgrade to continue receiving full AI Healthcare Intelligence: "
                + baseUrl + "/pricing\n\n"
                + "You can still access limited features with a free account.";

        sendEmail(email, subject, html, plain);
        log.debug("sendDemoExpiration() | return=void");
    }

    @Override
    public void notifyAdminNewRegistration(String userEmail, String displayName, String tier) {
        log.debug("notifyAdminNewRegistration() | userEmail={}, displayName={}, tier={}", userEmail, displayName, tier);

        String subject = "New User Registration: " + displayName;
        String html = "<div style=\"font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px\">"
                + "<div style=\"background:#059669;color:white;padding:24px;border-radius:8px 8px 0 0;text-align:center\">"
                + "<h1 style=\"margin:0;font-size:24px\">New User Registered</h1></div>"
                + "<div style=\"background:white;padding:24px;border:1px solid #e5e7eb;border-top:none;border-radius:0 0 8px 8px\">"
                + "<p>A new user has signed up for AI Healthcare Intelligence:</p>"
                + "<table style=\"width:100%;border-collapse:collapse;margin:16px 0\">"
                + "<tr><td style=\"padding:8px;font-weight:bold;border-bottom:1px solid #e5e7eb\">Name</td>"
                + "<td style=\"padding:8px;border-bottom:1px solid #e5e7eb\">" + escapeHtml(displayName) + "</td></tr>"
                + "<tr><td style=\"padding:8px;font-weight:bold;border-bottom:1px solid #e5e7eb\">Email</td>"
                + "<td style=\"padding:8px;border-bottom:1px solid #e5e7eb\">" + escapeHtml(userEmail) + "</td></tr>"
                + "<tr><td style=\"padding:8px;font-weight:bold\">Tier</td>"
                + "<td style=\"padding:8px\">" + escapeHtml(tier) + "</td></tr>"
                + "</table>"
                + "<p style=\"text-align:center;margin:24px 0\">"
                + "<a href=\"" + baseUrl + "/admin\" style=\"background:#1e40af;color:white;padding:12px 32px;"
                + "border-radius:6px;text-decoration:none;font-weight:bold\">View Admin Panel</a></p>"
                + "</div></div>";
        String plain = "New user registered:\nName: " + displayName + "\nEmail: " + userEmail + "\nTier: " + tier
                + "\n\nManage users at " + baseUrl + "/admin";

        sendEmail(adminEmail, subject, html, plain);
        log.debug("notifyAdminNewRegistration() | return=void");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void sendEmail(String to, String subject, String html, String plain) {
        log.debug("sendEmail() | to={}, subject={}", to, subject);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(plain, html);
            mailSender.send(message);
            log.info("sendEmail() | Transactional email sent: to={}, subject={}", LogSanitizer.maskEmail(to), subject);
        } catch (MessagingException | MailException e) {
            log.error("sendEmail() | Failed to send transactional email: to={}, error={}", LogSanitizer.maskEmail(to), e.getMessage());
        }

        log.debug("sendEmail() | return=void");
    }

    private String buildWelcomeHtml(String name, int demoDays) {
        return "<div style=\"font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px\">"
                + "<div style=\"background:#1e40af;color:white;padding:24px;border-radius:8px 8px 0 0;text-align:center\">"
                + "<h1 style=\"margin:0;font-size:24px\">Welcome to AI Healthcare Intelligence!</h1></div>"
                + "<div style=\"background:white;padding:24px;border:1px solid #e5e7eb;border-top:none;border-radius:0 0 8px 8px\">"
                + "<p>Hi <strong>" + escapeHtml(name) + "</strong>,</p>"
                + "<p>Your <strong>" + demoDays + "-day demo</strong> is now active. "
                + "You have full access to all features including:</p>"
                + "<ul>"
                + "<li>AI-powered multi-model search (Claude, GPT, Gemini, Perplexity)</li>"
                + "<li>Full newsletter archive with unlimited access</li>"
                + "<li>Regulatory alerts and custom watchlists</li>"
                + "<li>Trend analysis and company discovery</li>"
                + "</ul>"
                + "<p style=\"text-align:center;margin:24px 0\">"
                + "<a href=\"" + baseUrl + "/dashboard\" style=\"background:#1e40af;color:white;padding:12px 32px;"
                + "border-radius:6px;text-decoration:none;font-weight:bold\">Go to Dashboard</a></p>"
                + "<p style=\"color:#6b7280;font-size:13px\">When your demo ends, "
                + "<a href=\"" + baseUrl + "/pricing\">upgrade to a subscriber plan</a> "
                + "to keep full access.</p>"
                + "</div></div>";
    }

    private String buildExpirationHtml(String name) {
        return "<div style=\"font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px\">"
                + "<div style=\"background:#dc2626;color:white;padding:24px;border-radius:8px 8px 0 0;text-align:center\">"
                + "<h1 style=\"margin:0;font-size:24px\">Your Demo Has Expired</h1></div>"
                + "<div style=\"background:white;padding:24px;border:1px solid #e5e7eb;border-top:none;border-radius:0 0 8px 8px\">"
                + "<p>Hi <strong>" + escapeHtml(name) + "</strong>,</p>"
                + "<p>Your AI Healthcare Intelligence demo period has ended. "
                + "To continue receiving full access to all features, upgrade to a subscriber plan.</p>"
                + "<p style=\"text-align:center;margin:24px 0\">"
                + "<a href=\"" + baseUrl + "/pricing\" style=\"background:#1e40af;color:white;padding:12px 32px;"
                + "border-radius:6px;text-decoration:none;font-weight:bold\">Upgrade Now</a></p>"
                + "<p style=\"color:#6b7280;font-size:13px\">You can still log in and access limited "
                + "features with your free account.</p>"
                + "</div></div>";
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
