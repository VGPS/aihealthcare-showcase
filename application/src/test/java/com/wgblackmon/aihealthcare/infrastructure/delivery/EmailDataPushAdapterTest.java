package com.wgblackmon.aihealthcare.infrastructure.delivery;

import com.wgblackmon.aihealthcare.domain.model.*;
import com.wgblackmon.aihealthcare.domain.port.outbound.DataArtifactPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SignedLinkPort;
import com.wgblackmon.aihealthcare.infrastructure.config.EnterpriseDataProperties;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EmailDataPushAdapter} — attachment vs signed link
 * delivery, recipient capping, and error handling.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
class EmailDataPushAdapterTest {

    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneId.of("UTC"));

    private JavaMailSender mailSender;
    private DataArtifactPort artifactPort;
    private SignedLinkPort signedLinkPort;
    private EnterpriseDataProperties properties;
    private EmailDataPushAdapter adapter;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        artifactPort = mock(DataArtifactPort.class);
        signedLinkPort = mock(SignedLinkPort.class);

        properties = new EnterpriseDataProperties(null) {
            @Override public String getSigningSecret() { return "test-secret"; }
        };
        properties.setPush(new EnterpriseDataProperties.Push());
        properties.getPush().setMaxAttachmentBytes(8_388_608L);
        properties.getPush().setMaxRecipients(3);
        properties.getPush().setFromAddress("data@test.com");
        properties.getPush().setLinkExpirySeconds(86400L);

        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
        when(signedLinkPort.createToken(any(), any())).thenReturn("signed-token");

        adapter = new EmailDataPushAdapter(mailSender, artifactPort, signedLinkPort,
                properties, "https://app.test.com", FIXED_CLOCK);
    }

    private DataPushSchedule schedule(List<String> recipients) {
        return new DataPushSchedule(
                "sched-1", "alice@test.com", "Daily Export", "articles",
                null, null, Map.of(), ExportFormat.CSV,
                "0 0 7 * * MON-FRI", "America/Chicago",
                recipients, true, NOW, null, null, null, 0, NOW, NOW
        );
    }

    private DataJob job(Integer rowCount) {
        return new DataJob(
                "job-1", "alice@test.com", null, DataJobMode.PUSH, "articles",
                null, ExportFormat.CSV, DataJobStatus.SUCCEEDED,
                rowCount, 1024L, "abc123", "job-1.csv", null,
                null, null, NOW, NOW, NOW, NOW, NOW.plusSeconds(86400), "sched-1"
        );
    }

    private DataArtifact artifact(long byteSize) {
        return new DataArtifact("job-1", ExportFormat.CSV, "job-1.csv",
                byteSize, "abc123", NOW, NOW.plusSeconds(86400));
    }

    @Test
    void smallArtifact_sendsAttachment() {
        when(artifactPort.read("job-1")).thenReturn(new java.io.ByteArrayInputStream(new byte[1024]));

        PushDeliveryResult result = adapter.deliver(
                schedule(List.of("r@test.com")), job(50), artifact(1024));

        assertThat(result.success()).isTrue();
        assertThat(result.mode()).isEqualTo(PushDeliveryMode.ATTACHMENT);
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void largeArtifact_sendsSignedLink() {
        PushDeliveryResult result = adapter.deliver(
                schedule(List.of("r@test.com")), job(50), artifact(10_000_000L));

        assertThat(result.success()).isTrue();
        assertThat(result.mode()).isEqualTo(PushDeliveryMode.SIGNED_LINK);
        verify(signedLinkPort).createToken(eq("job-1"), any());
    }

    @Test
    void mailException_returnsFailedResult() {
        when(artifactPort.read("job-1")).thenReturn(new java.io.ByteArrayInputStream(new byte[100]));
        doThrow(new MailSendException("SES rejected"))
                .when(mailSender).send(any(MimeMessage.class));

        PushDeliveryResult result = adapter.deliver(
                schedule(List.of("r@test.com")), job(50), artifact(100));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("SES rejected");
    }

    @Test
    void recipientOverflow_capped() {
        when(artifactPort.read("job-1")).thenReturn(new java.io.ByteArrayInputStream(new byte[100]));

        PushDeliveryResult result = adapter.deliver(
                schedule(List.of("a@t.com", "b@t.com", "c@t.com", "d@t.com", "e@t.com")),
                job(50), artifact(100));

        assertThat(result.success()).isTrue();
        assertThat(result.recipients()).hasSize(3);
        verify(mailSender, times(3)).send(any(MimeMessage.class));
    }

    @Test
    void multipleRecipients_eachGetsSeparateEmail() {
        when(artifactPort.read("job-1")).thenReturn(new java.io.ByteArrayInputStream(new byte[100]));

        PushDeliveryResult result = adapter.deliver(
                schedule(List.of("a@t.com", "b@t.com")), job(50), artifact(100));

        assertThat(result.success()).isTrue();
        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }
}
