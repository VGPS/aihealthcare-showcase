package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.SsoIdentityProvider;
import com.wgblackmon.aihealthcare.domain.model.SsoProvisioningAction;
import com.wgblackmon.aihealthcare.domain.model.SsoProvisioningEvent;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SsoIdentityProviderPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SsoProvisioningEventPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TransactionalEmailPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SsoProvisioningService} JIT provisioning logic.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
class SsoProvisioningServiceTest {

    private AppUserPort appUserPort;
    private SubscriberPort subscriberPort;
    private SsoIdentityProviderPort providerPort;
    private SsoProvisioningEventPort eventPort;
    private TransactionalEmailPort emailPort;
    private SsoProvisioningService service;

    private static final Instant NOW = Instant.now();
    private static final String REG_ID = "mayo-clinic";

    private SsoIdentityProvider testIdp() {
        return new SsoIdentityProvider(
                REG_ID, "Mayo Clinic",
                "https://idp.mayo.edu", "https://idp.mayo.edu/sso",
                "CERT", null, "email", "displayName",
                SubscriptionTier.ENTERPRISE, true, NOW, NOW);
    }

    @BeforeEach
    void setUp() {
        appUserPort = Mockito.mock(AppUserPort.class);
        subscriberPort = Mockito.mock(SubscriberPort.class);
        providerPort = Mockito.mock(SsoIdentityProviderPort.class);
        eventPort = Mockito.mock(SsoProvisioningEventPort.class);
        emailPort = Mockito.mock(TransactionalEmailPort.class);
        service = new SsoProvisioningService(appUserPort, subscriberPort, providerPort, eventPort, emailPort);

        when(providerPort.findById(REG_ID)).thenReturn(Optional.of(testIdp()));
    }

    @Test
    void newUserCreatesAppUserAndSubscriber() {
        when(appUserPort.findByEmail("new@mayo.edu")).thenReturn(Optional.empty());
        when(eventPort.findByEmail("new@mayo.edu")).thenReturn(Collections.emptyList());

        AppUser result = service.provisionOrLink("new@mayo.edu", "New User", REG_ID);

        assertThat(result.email()).isEqualTo("new@mayo.edu");
        assertThat(result.passwordHash()).isEqualTo(SsoProvisioningService.SSO_PASSWORD_SENTINEL);
        assertThat(result.tier()).isEqualTo(SubscriptionTier.ENTERPRISE);

        verify(appUserPort).save(any(AppUser.class));
        verify(subscriberPort).save(any(Subscriber.class));
        verify(emailPort).notifyAdminNewRegistration("new@mayo.edu", "New User", "ENTERPRISE (SSO)");

        ArgumentCaptor<SsoProvisioningEvent> eventCaptor = ArgumentCaptor.forClass(SsoProvisioningEvent.class);
        verify(eventPort).record(eventCaptor.capture());
        assertThat(eventCaptor.getValue().action()).isEqualTo(SsoProvisioningAction.CREATED);
    }

    @Test
    void existingUserFirstSsoLoginLinksAccount() {
        AppUser existing = new AppUser("doc@mayo.edu", "$2b$hashed", "Dr. Smith",
                "USER", true, SubscriptionTier.SUBSCRIBER, null);
        when(appUserPort.findByEmail("doc@mayo.edu")).thenReturn(Optional.of(existing));
        when(eventPort.findByEmail("doc@mayo.edu")).thenReturn(Collections.emptyList());
        when(subscriberPort.findByEmail("doc@mayo.edu")).thenReturn(Optional.of(
                new Subscriber("doc@mayo.edu", "Dr. Smith", true, NOW,
                        SubscriptionTier.SUBSCRIBER, "token", null, null)));

        AppUser result = service.provisionOrLink("doc@mayo.edu", "Dr. Smith", REG_ID);

        assertThat(result.tier()).isEqualTo(SubscriptionTier.ENTERPRISE);
        assertThat(result.passwordHash()).isEqualTo("$2b$hashed");

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserPort).save(userCaptor.capture());
        assertThat(userCaptor.getValue().tier()).isEqualTo(SubscriptionTier.ENTERPRISE);

        ArgumentCaptor<Subscriber> subCaptor = ArgumentCaptor.forClass(Subscriber.class);
        verify(subscriberPort).save(subCaptor.capture());
        assertThat(subCaptor.getValue().tier()).isEqualTo(SubscriptionTier.ENTERPRISE);

        ArgumentCaptor<SsoProvisioningEvent> eventCaptor = ArgumentCaptor.forClass(SsoProvisioningEvent.class);
        verify(eventPort).record(eventCaptor.capture());
        assertThat(eventCaptor.getValue().action()).isEqualTo(SsoProvisioningAction.LINKED);
    }

    @Test
    void returningSsoUserRecordsLoginOnly() {
        AppUser existing = new AppUser("doc@mayo.edu", "{SSO}", "Dr. Smith",
                "USER", true, SubscriptionTier.ENTERPRISE, null);
        when(appUserPort.findByEmail("doc@mayo.edu")).thenReturn(Optional.of(existing));

        SsoProvisioningEvent priorEvent = new SsoProvisioningEvent(
                "prev-id", REG_ID, "doc@mayo.edu", SsoProvisioningAction.CREATED, NOW);
        when(eventPort.findByEmail("doc@mayo.edu")).thenReturn(List.of(priorEvent));

        AppUser result = service.provisionOrLink("doc@mayo.edu", "Dr. Smith", REG_ID);

        assertThat(result.email()).isEqualTo("doc@mayo.edu");
        verify(appUserPort, never()).save(any());
        verify(subscriberPort, never()).save(any());

        ArgumentCaptor<SsoProvisioningEvent> eventCaptor = ArgumentCaptor.forClass(SsoProvisioningEvent.class);
        verify(eventPort).record(eventCaptor.capture());
        assertThat(eventCaptor.getValue().action()).isEqualTo(SsoProvisioningAction.LOGIN);
    }

    @Test
    void unknownProviderThrows() {
        when(providerPort.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.provisionOrLink("x@x.com", "X", "unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void newUserGetsCorrectRole() {
        when(appUserPort.findByEmail("nurse@mayo.edu")).thenReturn(Optional.empty());
        when(eventPort.findByEmail("nurse@mayo.edu")).thenReturn(Collections.emptyList());

        service.provisionOrLink("nurse@mayo.edu", "Nurse Jane", REG_ID);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserPort).save(captor.capture());
        assertThat(captor.getValue().role()).isEqualTo("USER");
        assertThat(captor.getValue().enabled()).isTrue();
        assertThat(captor.getValue().demoExpiresAt()).isNull();
    }

    @Test
    void linkedUserPreservesOriginalPassword() {
        AppUser existing = new AppUser("admin@mayo.edu", "$2b$original", "Admin",
                "ADMIN", true, SubscriptionTier.SUBSCRIBER, null);
        when(appUserPort.findByEmail("admin@mayo.edu")).thenReturn(Optional.of(existing));
        when(eventPort.findByEmail("admin@mayo.edu")).thenReturn(Collections.emptyList());
        when(subscriberPort.findByEmail("admin@mayo.edu")).thenReturn(Optional.empty());

        service.provisionOrLink("admin@mayo.edu", "Admin", REG_ID);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserPort).save(captor.capture());
        assertThat(captor.getValue().passwordHash()).isEqualTo("$2b$original");
    }

    @Test
    void noAdminNotificationOnLinkOrLogin() {
        AppUser existing = new AppUser("doc@mayo.edu", "$2b$hashed", "Dr. Smith",
                "USER", true, SubscriptionTier.SUBSCRIBER, null);
        when(appUserPort.findByEmail("doc@mayo.edu")).thenReturn(Optional.of(existing));
        when(eventPort.findByEmail("doc@mayo.edu")).thenReturn(Collections.emptyList());
        when(subscriberPort.findByEmail("doc@mayo.edu")).thenReturn(Optional.empty());

        service.provisionOrLink("doc@mayo.edu", "Dr. Smith", REG_ID);

        verify(emailPort, never()).notifyAdminNewRegistration(anyString(), anyString(), anyString());
    }
}
