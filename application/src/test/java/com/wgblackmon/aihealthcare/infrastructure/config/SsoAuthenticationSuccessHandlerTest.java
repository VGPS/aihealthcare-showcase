package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.SsoIdentityProvider;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.SsoIdentityProviderPort;
import com.wgblackmon.aihealthcare.domain.service.SsoProvisioningService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SsoAuthenticationSuccessHandler}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
class SsoAuthenticationSuccessHandlerTest {

    private SsoProvisioningService provisioningService;
    private SsoIdentityProviderPort providerPort;
    private SsoAuthenticationSuccessHandler handler;

    private HttpServletRequest request;
    private HttpServletResponse response;

    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        provisioningService = mock(SsoProvisioningService.class);
        providerPort = mock(SsoIdentityProviderPort.class);
        handler = new SsoAuthenticationSuccessHandler(provisioningService, providerPort);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);

        when(request.getRequestURI()).thenReturn("/login/saml2/sso/test-idp");
        when(request.getContextPath()).thenReturn("");
        when(request.getSession()).thenReturn(mock(jakarta.servlet.http.HttpSession.class));
        when(request.getSession(Mockito.anyBoolean())).thenReturn(mock(jakarta.servlet.http.HttpSession.class));

        SecurityContextHolder.clearContext();
    }

    @Test
    void extractsEmailFromSamlAssertion() throws Exception {
        SsoIdentityProvider idp = testIdp("test-idp", "email", "displayName");
        when(providerPort.findById("test-idp")).thenReturn(Optional.of(idp));

        Saml2AuthenticatedPrincipal principal = mockPrincipal("test-idp",
                "email", "doc@mayo.edu",
                "displayName", "Dr. Smith");

        AppUser user = new AppUser("doc@mayo.edu", "{SSO}", "Dr. Smith",
                "USER", true, SubscriptionTier.ENTERPRISE, null);
        when(provisioningService.provisionOrLink("doc@mayo.edu", "Dr. Smith", "test-idp"))
                .thenReturn(user);

        var auth = new org.springframework.security.saml2.provider.service.authentication.Saml2Authentication(
                principal, "response", List.of());
        handler.onAuthenticationSuccess(request, response, auth);

        verify(provisioningService).provisionOrLink("doc@mayo.edu", "Dr. Smith", "test-idp");

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("doc@mayo.edu");
    }

    @Test
    void usesCustomAttributeNamesFromIdp() throws Exception {
        SsoIdentityProvider idp = testIdp("custom-idp", "mail", "cn");
        when(providerPort.findById("custom-idp")).thenReturn(Optional.of(idp));

        Saml2AuthenticatedPrincipal principal = mockPrincipal("custom-idp",
                "mail", "nurse@kaiser.org",
                "cn", "Nurse Jane");

        AppUser user = new AppUser("nurse@kaiser.org", "{SSO}", "Nurse Jane",
                "USER", true, SubscriptionTier.ENTERPRISE, null);
        when(provisioningService.provisionOrLink("nurse@kaiser.org", "Nurse Jane", "custom-idp"))
                .thenReturn(user);

        var auth = new org.springframework.security.saml2.provider.service.authentication.Saml2Authentication(
                principal, "response", List.of());
        handler.onAuthenticationSuccess(request, response, auth);

        verify(provisioningService).provisionOrLink("nurse@kaiser.org", "Nurse Jane", "custom-idp");
    }

    @Test
    void fallsBackToNameIdWhenEmailAttributeMissing() throws Exception {
        when(providerPort.findById("no-email")).thenReturn(Optional.empty());

        Saml2AuthenticatedPrincipal principal = mock(Saml2AuthenticatedPrincipal.class);
        when(principal.getRelyingPartyRegistrationId()).thenReturn("no-email");
        when(principal.getName()).thenReturn("fallback@example.com");
        when(principal.getAttribute(anyString())).thenReturn(null);

        AppUser user = new AppUser("fallback@example.com", "{SSO}", "fallback@example.com",
                "USER", true, SubscriptionTier.ENTERPRISE, null);
        when(provisioningService.provisionOrLink("fallback@example.com", "fallback@example.com", "no-email"))
                .thenReturn(user);

        var auth = new org.springframework.security.saml2.provider.service.authentication.Saml2Authentication(
                principal, "response", List.of());
        handler.onAuthenticationSuccess(request, response, auth);

        verify(provisioningService).provisionOrLink("fallback@example.com", "fallback@example.com", "no-email");
    }

    @Test
    void setsCorrectRoleOnSecurityContext() throws Exception {
        when(providerPort.findById("admin-idp")).thenReturn(Optional.empty());

        Saml2AuthenticatedPrincipal principal = mock(Saml2AuthenticatedPrincipal.class);
        when(principal.getRelyingPartyRegistrationId()).thenReturn("admin-idp");
        when(principal.getName()).thenReturn("admin@mayo.edu");
        when(principal.getAttribute(anyString())).thenReturn(null);

        AppUser user = new AppUser("admin@mayo.edu", "{SSO}", "Admin",
                "ADMIN", true, SubscriptionTier.ENTERPRISE, null);
        when(provisioningService.provisionOrLink("admin@mayo.edu", "admin@mayo.edu", "admin-idp"))
                .thenReturn(user);

        var auth = new org.springframework.security.saml2.provider.service.authentication.Saml2Authentication(
                principal, "response", List.of());
        handler.onAuthenticationSuccess(request, response, auth);

        var secAuth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(secAuth.getAuthorities()).anyMatch(
                a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @Test
    void setsUserRoleByDefault() throws Exception {
        SsoIdentityProvider idp = testIdp("user-idp", "email", "displayName");
        when(providerPort.findById("user-idp")).thenReturn(Optional.of(idp));

        Saml2AuthenticatedPrincipal principal = mockPrincipal("user-idp",
                "email", "user@hospital.org",
                "displayName", "Regular User");

        AppUser user = new AppUser("user@hospital.org", "{SSO}", "Regular User",
                "USER", true, SubscriptionTier.ENTERPRISE, null);
        when(provisioningService.provisionOrLink("user@hospital.org", "Regular User", "user-idp"))
                .thenReturn(user);

        var auth = new org.springframework.security.saml2.provider.service.authentication.Saml2Authentication(
                principal, "response", List.of());
        handler.onAuthenticationSuccess(request, response, auth);

        var secAuth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(secAuth.getAuthorities()).anyMatch(
                a -> a.getAuthority().equals("ROLE_USER"));
    }

    private SsoIdentityProvider testIdp(String regId, String emailAttr, String nameAttr) {
        return new SsoIdentityProvider(
                regId, "Test IdP " + regId,
                "https://idp.test/" + regId, "https://idp.test/" + regId + "/sso",
                "CERT", null, emailAttr, nameAttr,
                SubscriptionTier.ENTERPRISE, true, NOW, NOW);
    }

    private Saml2AuthenticatedPrincipal mockPrincipal(String regId,
                                                       String emailAttr, String emailVal,
                                                       String nameAttr, String nameVal) {
        Saml2AuthenticatedPrincipal principal = mock(Saml2AuthenticatedPrincipal.class);
        when(principal.getRelyingPartyRegistrationId()).thenReturn(regId);
        when(principal.getName()).thenReturn(emailVal);
        when(principal.getAttribute(emailAttr)).thenReturn(List.of(emailVal));
        when(principal.getAttribute(nameAttr)).thenReturn(List.of(nameVal));
        return principal;
    }
}
