package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer slice tests for {@link UnsubscribeController}.
 *
 * <p>Tests verify the one-click unsubscribe flow: valid token deactivates
 * subscriber, missing token shows error, invalid token shows error.
 * The endpoint is publicly accessible (no authentication required).
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-07-31
 * @updated 2026-08-05
 */
@Import(SecurityConfig.class)
@WebMvcTest(controllers = {UnsubscribeController.class, GlobalExceptionHandler.class})
class UnsubscribeControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ApiKeyPort apiKeyPort;
    @MockitoBean
    private SubscriberPort subscriberPort;
    @MockitoBean
    private AppUserPort appUserPort;

    @Test
    void unsubscribe_validToken_deactivatesSubscriber() throws Exception {
        Subscriber sub = new Subscriber("test@example.com", "Test User", true,
                Instant.now(), SubscriptionTier.SUBSCRIBER, "abc-123", "cus_1", "sub_1");
        when(subscriberPort.findByUnsubscribeToken("abc-123")).thenReturn(Optional.of(sub));
        when(appUserPort.findByEmail("test@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/unsubscribe").param("token", "abc-123"))
                .andExpect(status().isOk())
                .andExpect(view().name("unsubscribe"))
                .andExpect(model().attribute("success", true))
                .andExpect(model().attribute("email", "test@example.com"));

        ArgumentCaptor<Subscriber> captor = ArgumentCaptor.forClass(Subscriber.class);
        verify(subscriberPort).save(captor.capture());
        Subscriber saved = captor.getValue();
        assertThat(saved.active()).isFalse();
        assertThat(saved.tier()).isEqualTo(SubscriptionTier.FREE);
        assertThat(saved.stripeCustomerId()).isNull();
        assertThat(saved.stripeSubscriptionId()).isNull();
    }

    @Test
    void unsubscribe_validToken_downgradesAppUser() throws Exception {
        Subscriber sub = new Subscriber("test@example.com", "Test User", true,
                Instant.now(), SubscriptionTier.SUBSCRIBER, "abc-123", null, null);
        when(subscriberPort.findByUnsubscribeToken("abc-123")).thenReturn(Optional.of(sub));

        AppUser user = new AppUser("test@example.com", "hash", "Test",
                "USER", true, SubscriptionTier.SUBSCRIBER, null);
        when(appUserPort.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/unsubscribe").param("token", "abc-123"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("success", true));

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserPort).save(userCaptor.capture());
        assertThat(userCaptor.getValue().tier()).isEqualTo(SubscriptionTier.FREE);
    }

    @Test
    void unsubscribe_freeUser_noAppUserDowngrade() throws Exception {
        Subscriber sub = new Subscriber("free@example.com", "Free User", true,
                Instant.now(), SubscriptionTier.FREE, "free-token", null, null);
        when(subscriberPort.findByUnsubscribeToken("free-token")).thenReturn(Optional.of(sub));

        AppUser user = new AppUser("free@example.com", "hash", "Free",
                "USER", true, SubscriptionTier.FREE, null);
        when(appUserPort.findByEmail("free@example.com")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/unsubscribe").param("token", "free-token"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("success", true));

        verify(appUserPort, never()).save(any(AppUser.class));
    }

    @Test
    void unsubscribe_missingToken_showsError() throws Exception {
        mockMvc.perform(get("/unsubscribe"))
                .andExpect(status().isOk())
                .andExpect(view().name("unsubscribe"))
                .andExpect(model().attribute("success", false));
    }

    @Test
    void unsubscribe_invalidToken_showsError() throws Exception {
        when(subscriberPort.findByUnsubscribeToken("bad-token")).thenReturn(Optional.empty());

        mockMvc.perform(get("/unsubscribe").param("token", "bad-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("unsubscribe"))
                .andExpect(model().attribute("success", false));
    }
}
