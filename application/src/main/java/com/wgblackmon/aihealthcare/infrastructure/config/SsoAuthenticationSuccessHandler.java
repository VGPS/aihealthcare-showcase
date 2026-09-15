package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.SsoIdentityProvider;
import com.wgblackmon.aihealthcare.domain.port.outbound.SsoIdentityProviderPort;
import com.wgblackmon.aihealthcare.domain.service.SsoProvisioningService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

import java.io.IOException;
import java.util.List;

/**
 * Handles successful SAML2 authentication by provisioning or linking the
 * user and establishing a Spring Security session.
 *
 * <p>Extracts email and display name from the SAML assertion using the
 * IdP's configured attribute names, delegates to
 * {@link SsoProvisioningService} for JIT provisioning, then sets a
 * {@link UsernamePasswordAuthenticationToken} with the user's role
 * so that downstream {@code hasRole()} checks work normally.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
@Slf4j
public class SsoAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final SsoProvisioningService provisioningService;
    private final SsoIdentityProviderPort providerPort;
    private final SavedRequestAwareAuthenticationSuccessHandler delegate;

    public SsoAuthenticationSuccessHandler(SsoProvisioningService provisioningService,
                                           SsoIdentityProviderPort providerPort) {
        log.debug("SsoAuthenticationSuccessHandler()");
        this.provisioningService = provisioningService;
        this.providerPort = providerPort;
        this.delegate = new SavedRequestAwareAuthenticationSuccessHandler();
        this.delegate.setDefaultTargetUrl("/dashboard");
        this.delegate.setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        log.debug("onAuthenticationSuccess() | principal={}", authentication.getName());

        if (!(authentication.getPrincipal() instanceof Saml2AuthenticatedPrincipal principal)) {
            log.warn("onAuthenticationSuccess() | unexpected principal type: {}",
                     authentication.getPrincipal().getClass().getSimpleName());
            delegate.onAuthenticationSuccess(request, response, authentication);
            return;
        }

        String registrationId = principal.getRelyingPartyRegistrationId();
        SsoIdentityProvider idp = providerPort.findById(registrationId).orElse(null);

        String emailAttr = idp != null ? idp.emailAttribute() : "email";
        String nameAttr = idp != null ? idp.displayNameAttribute() : "displayName";

        String email = getFirstAttribute(principal, emailAttr);
        if (email == null) {
            email = principal.getName();
        }

        String displayName = getFirstAttribute(principal, nameAttr);
        if (displayName == null) {
            displayName = email;
        }

        log.debug("onAuthenticationSuccess() | email={}, displayName={}, registrationId={}",
                  email, displayName, registrationId);

        AppUser user = provisioningService.provisionOrLink(email, displayName, registrationId);

        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                email, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.role())));
        SecurityContextHolder.getContext().setAuthentication(token);

        log.debug("onAuthenticationSuccess() | session established for {}", email);
        delegate.onAuthenticationSuccess(request, response, token);
    }

    private String getFirstAttribute(Saml2AuthenticatedPrincipal principal, String attrName) {
        List<Object> values = principal.getAttribute(attrName);
        if (values != null && !values.isEmpty()) {
            return values.get(0).toString();
        }
        return null;
    }
}
