package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.SsoIdentityProvider;
import com.wgblackmon.aihealthcare.domain.model.SsoProvisioningAction;
import com.wgblackmon.aihealthcare.domain.model.SsoProvisioningEvent;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA slice tests for {@link SsoIdentityProviderAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
@DataJpaTest
class SsoIdentityProviderAdapterTest {

    @Autowired
    private SsoIdentityProviderRepository providerRepo;

    @Autowired
    private SsoProvisioningEventRepository eventRepo;

    private SsoIdentityProviderAdapter adapter;

    private static final Instant NOW = Instant.now();

    private SsoIdentityProvider testProvider(String regId, boolean active) {
        return new SsoIdentityProvider(
                regId, "Test IdP " + regId,
                "https://idp.test/" + regId, "https://idp.test/" + regId + "/sso",
                "CERT-" + regId, null, "email", "displayName",
                SubscriptionTier.ENTERPRISE, active, NOW, NOW);
    }

    @BeforeEach
    void setUp() {
        adapter = new SsoIdentityProviderAdapter(providerRepo, eventRepo);
    }

    @Test
    void saveAndFindByIdRoundTrips() {
        SsoIdentityProvider saved = adapter.save(testProvider("acme-health", true));

        Optional<SsoIdentityProvider> found = adapter.findById("acme-health");

        assertThat(found).isPresent();
        assertThat(found.get().label()).isEqualTo("Test IdP acme-health");
        assertThat(found.get().defaultTier()).isEqualTo(SubscriptionTier.ENTERPRISE);
    }

    @Test
    void findAllActiveFiltersInactive() {
        adapter.save(testProvider("active-idp", true));
        adapter.save(testProvider("inactive-idp", false));

        List<SsoIdentityProvider> active = adapter.findAllActive();

        assertThat(active).hasSize(1);
        assertThat(active.get(0).registrationId()).isEqualTo("active-idp");
    }

    @Test
    void deleteByIdRemovesProvider() {
        adapter.save(testProvider("to-delete", true));
        assertThat(adapter.existsById("to-delete")).isTrue();

        adapter.deleteById("to-delete");

        assertThat(adapter.existsById("to-delete")).isFalse();
    }

    @Test
    void recordAndFindProvisioningEvents() {
        SsoProvisioningEvent event = new SsoProvisioningEvent(
                "evt-1", "test-idp", "user@test.com",
                SsoProvisioningAction.CREATED, NOW);
        adapter.record(event);

        List<SsoProvisioningEvent> byEmail = adapter.findByEmail("user@test.com");
        assertThat(byEmail).hasSize(1);
        assertThat(byEmail.get(0).action()).isEqualTo(SsoProvisioningAction.CREATED);

        List<SsoProvisioningEvent> byReg = adapter.findByRegistrationId("test-idp");
        assertThat(byReg).hasSize(1);
    }

    @Test
    void findAllReturnsAllProviders() {
        adapter.save(testProvider("idp-aa", true));
        adapter.save(testProvider("idp-bb", false));

        List<SsoIdentityProvider> all = adapter.findAll();
        assertThat(all).hasSize(2);
    }
}
