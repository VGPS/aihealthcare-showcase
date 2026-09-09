package com.wgblackmon.aihealthcare.infrastructure.delivery;

import com.wgblackmon.aihealthcare.domain.model.DataArtifact;
import com.wgblackmon.aihealthcare.domain.model.DataJob;
import com.wgblackmon.aihealthcare.domain.model.DataPushSchedule;
import com.wgblackmon.aihealthcare.domain.model.PushDeliveryMode;
import com.wgblackmon.aihealthcare.domain.model.PushDeliveryResult;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataArtifactPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataPushDeliveryPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SignedLinkPort;
import com.wgblackmon.aihealthcare.infrastructure.config.EnterpriseDataProperties;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Email-based push delivery — sends completed data artifacts to schedule
 * recipients as attachments (small files) or signed download links (large files).
 *
 * <p>Never throws. Any {@link MailException} is caught and returned as a
 * failed {@link PushDeliveryResult}. The scheduler owns the retry/back-off
 * policy; this adapter owns delivery only.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Slf4j
@Component
public class EmailDataPushAdapter implements DataPushDeliveryPort {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("UTC"));

    private final JavaMailSender mailSender;
    private final DataArtifactPort artifactPort;
    private final SignedLinkPort signedLinkPort;
    private final EnterpriseDataProperties properties;
    private final String baseUrl;
    private final Clock clock;

    public EmailDataPushAdapter(JavaMailSender mailSender,
                                DataArtifactPort artifactPort,
                                SignedLinkPort signedLinkPort,
                                EnterpriseDataProperties properties,
                                @Value("${aihealthcare.base-url:http://localhost:8080}") String baseUrl,
                                Clock clock) {
        this.mailSender = mailSender;
        this.artifactPort = artifactPort;
        this.signedLinkPort = signedLinkPort;
        this.properties = properties;
        this.baseUrl = baseUrl;
        this.clock = clock;
        log.debug("EmailDataPushAdapter() | fromAddress={}, maxAttachmentBytes={}",
                properties.getPush().getFromAddress(), properties.getPush().getMaxAttachmentBytes());
    }

    @Override
    public PushDeliveryResult deliver(DataPushSchedule schedule, DataJob job, DataArtifact artifact) {
        log.debug("deliver() | scheduleId={}, jobId={}, artifactBytes={}",
                schedule.scheduleId(), job.jobId(), artifact.byteSize());

        Instant now = clock.instant();
        List<String> effectiveRecipients = capRecipients(schedule.recipients());
        long maxAttach = properties.getPush().getMaxAttachmentBytes();

        PushDeliveryMode mode;
        byte[] fileBytes = null;
        String downloadLink = null;

        if (artifact.byteSize() <= maxAttach) {
            mode = PushDeliveryMode.ATTACHMENT;
            try {
                fileBytes = artifactPort.read(job.jobId()).readAllBytes();
            } catch (Exception e) {
                log.error("deliver() | failed to read artifact: {}", e.getMessage());
                return failedResult(schedule, job, PushDeliveryMode.ATTACHMENT,
                        effectiveRecipients, now, "Failed to read artifact: " + e.getMessage());
            }
        } else {
            mode = PushDeliveryMode.SIGNED_LINK;
        }

        if (mode == PushDeliveryMode.SIGNED_LINK || mode == PushDeliveryMode.ATTACHMENT) {
            try {
                long expirySeconds = properties.getPush().getLinkExpirySeconds();
                Instant expiresAt = now.plusSeconds(expirySeconds);
                String token = signedLinkPort.createToken(job.jobId(), expiresAt);
                downloadLink = baseUrl + "/d/" + token;
            } catch (IllegalStateException e) {
                if (mode == PushDeliveryMode.SIGNED_LINK) {
                    return failedResult(schedule, job, mode, effectiveRecipients, now,
                            "Signing secret not configured and artifact too large to attach");
                }
                downloadLink = null;
            }
        }

        String subject = "[AIHealthcare] " + schedule.label() + " — "
                + (job.rowCount() != null ? job.rowCount() + " rows" : "data") + " — "
                + DATE_FMT.format(now);

        String htmlBody = buildHtmlBody(schedule, job, artifact, mode, downloadLink);
        String plainBody = buildPlainBody(schedule, job, artifact, mode, downloadLink);

        try {
            for (String recipient : effectiveRecipients) {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(properties.getPush().getFromAddress());
                helper.setTo(recipient);
                helper.setSubject(subject);
                helper.setText(plainBody, htmlBody);

                if (mode == PushDeliveryMode.ATTACHMENT && fileBytes != null) {
                    String fileName = schedule.label().replaceAll("[^a-zA-Z0-9._-]", "_")
                            + "." + artifact.format().name().toLowerCase();
                    helper.addAttachment(fileName, new ByteArrayResource(fileBytes));
                }

                mailSender.send(message);
            }

            PushDeliveryResult result = new PushDeliveryResult(
                    schedule.scheduleId(), job.jobId(), mode,
                    effectiveRecipients, artifact.byteSize(), now, true, null
            );
            log.debug("deliver() | return=success, mode={}, recipients={}", mode, effectiveRecipients.size());
            return result;
        } catch (MailException | jakarta.mail.MessagingException e) {
            log.error("deliver() | mail send failed: {}", e.getMessage());
            return failedResult(schedule, job, mode, effectiveRecipients, now, e.getMessage());
        }
    }

    private List<String> capRecipients(List<String> recipients) {
        int max = properties.getPush().getMaxRecipients();
        if (recipients.size() <= max) {
            return recipients;
        }
        log.warn("capRecipients() | capping from {} to {} recipients", recipients.size(), max);
        return new ArrayList<>(recipients.subList(0, max));
    }

    private PushDeliveryResult failedResult(DataPushSchedule schedule, DataJob job,
                                             PushDeliveryMode mode, List<String> recipients,
                                             Instant now, String error) {
        return new PushDeliveryResult(
                schedule.scheduleId(), job.jobId(), mode,
                recipients, 0L, now, false, error
        );
    }

    private String buildHtmlBody(DataPushSchedule schedule, DataJob job,
                                  DataArtifact artifact, PushDeliveryMode mode, String downloadLink) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style=\"font-family: Arial, sans-serif; color: #333;\">");
        sb.append("<h2 style=\"color: #1a56db;\">").append(escapeHtml(schedule.label())).append("</h2>");
        sb.append("<p>Your scheduled data export is ready.</p>");
        sb.append("<table style=\"border-collapse: collapse; margin: 16px 0;\">");
        appendRow(sb, "Format", artifact.format().name());
        if (job.rowCount() != null) {
            appendRow(sb, "Rows", String.valueOf(job.rowCount()));
        }
        appendRow(sb, "Size", formatBytes(artifact.byteSize()));
        sb.append("</table>");

        if (mode == PushDeliveryMode.ATTACHMENT) {
            sb.append("<p>The data file is attached to this email.</p>");
            if (downloadLink != null) {
                sb.append("<p>You can also <a href=\"").append(escapeHtml(downloadLink))
                        .append("\">download it here</a> (link expires in 24 hours).</p>");
            }
        } else {
            sb.append("<p>The file is too large to attach. ")
                    .append("<a href=\"").append(escapeHtml(downloadLink))
                    .append("\">Download your data here</a> (link expires in 24 hours).</p>");
        }

        sb.append("<hr style=\"border: 1px solid #e5e7eb; margin-top: 24px;\"/>");
        sb.append("<p style=\"font-size: 12px; color: #6b7280;\">")
                .append("AIHealthcare Enterprise Data Push — schedule: ")
                .append(escapeHtml(schedule.scheduleId())).append("</p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private String buildPlainBody(DataPushSchedule schedule, DataJob job,
                                   DataArtifact artifact, PushDeliveryMode mode, String downloadLink) {
        StringBuilder sb = new StringBuilder();
        sb.append(schedule.label()).append("\n\n");
        sb.append("Your scheduled data export is ready.\n\n");
        sb.append("Format: ").append(artifact.format().name()).append("\n");
        if (job.rowCount() != null) {
            sb.append("Rows: ").append(job.rowCount()).append("\n");
        }
        sb.append("Size: ").append(formatBytes(artifact.byteSize())).append("\n\n");

        if (mode == PushDeliveryMode.ATTACHMENT) {
            sb.append("The data file is attached to this email.\n");
            if (downloadLink != null) {
                sb.append("You can also download it at: ").append(downloadLink).append("\n");
            }
        } else {
            sb.append("Download your data at: ").append(downloadLink).append("\n");
            sb.append("(Link expires in 24 hours)\n");
        }
        return sb.toString();
    }

    private void appendRow(StringBuilder sb, String label, String value) {
        sb.append("<tr>")
                .append("<td style=\"padding: 4px 12px 4px 0; font-weight: bold;\">").append(label).append("</td>")
                .append("<td style=\"padding: 4px 0;\">").append(escapeHtml(value)).append("</td>")
                .append("</tr>");
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1_048_576) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / 1_048_576.0);
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
