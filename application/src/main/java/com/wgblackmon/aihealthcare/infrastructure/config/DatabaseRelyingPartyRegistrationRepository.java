package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.model.SsoIdentityProvider;
import com.wgblackmon.aihealthcare.domain.port.outbound.SsoIdentityProviderPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Iterator;

/**
 * Dynamic {@link RelyingPartyRegistrationRepository} backed by the database.
 *
 * <p>Loads active {@link SsoIdentityProvider} records from
 * {@link SsoIdentityProviderPort} and converts each to a Spring Security
 * {@link RelyingPartyRegistration}. Lookups by registrationId are resolved
 * live from the DB — no caching (IdP configurations change rarely and
 * correctness matters more than latency here).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
@Slf4j
public class DatabaseRelyingPartyRegistrationRepository
        implements RelyingPartyRegistrationRepository, Iterable<RelyingPartyRegistration> {

    private final SsoIdentityProviderPort providerPort;
    private final String spEntityId;

    public DatabaseRelyingPartyRegistrationRepository(SsoIdentityProviderPort providerPort,
                                                       String spEntityId) {
        log.debug("DatabaseRelyingPartyRegistrationRepository() | spEntityId={}", spEntityId);
        this.providerPort = providerPort;
        this.spEntityId = spEntityId;
    }

    @Override
    public RelyingPartyRegistration findByRegistrationId(String registrationId) {
        log.debug("findByRegistrationId() | registrationId={}", registrationId);

        RelyingPartyRegistration result = providerPort.findById(registrationId)
                .filter(SsoIdentityProvider::active)
                .map(this::toRegistration)
                .orElse(null);

        log.debug("findByRegistrationId() | return={}", result != null ? registrationId : "null");
        return result;
    }

    @Override
    public Iterator<RelyingPartyRegistration> iterator() {
        return providerPort.findAllActive().stream()
                .map(this::toRegistration)
                .iterator();
    }

    private RelyingPartyRegistration toRegistration(SsoIdentityProvider idp) {
        return RelyingPartyRegistration.withRegistrationId(idp.registrationId())
                .entityId(spEntityId)
                .assertionConsumerServiceLocation("{baseUrl}/login/saml2/sso/{registrationId}")
                .assertingPartyMetadata(party -> party
                        .entityId(idp.entityId())
                        .singleSignOnServiceLocation(idp.ssoUrl())
                        .singleSignOnServiceBinding(Saml2MessageBinding.POST)
                        .verificationX509Credentials(creds ->
                                creds.add(new org.springframework.security.saml2.core.Saml2X509Credential(
                                        parseCertificate(idp.certificate()),
                                        org.springframework.security.saml2.core.Saml2X509Credential.Saml2X509CredentialType.VERIFICATION))))
                .build();
    }

    private X509Certificate parseCertificate(String pem) {
        try {
            String cleaned = pem
                    .replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s+", "");
            byte[] decoded = Base64.getDecoder().decode(cleaned);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(decoded));
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse IdP certificate", e);
        }
    }
}
