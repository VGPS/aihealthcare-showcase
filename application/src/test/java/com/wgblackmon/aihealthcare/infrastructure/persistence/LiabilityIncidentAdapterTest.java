package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.HarmType;
import com.wgblackmon.aihealthcare.domain.model.IncidentStatus;
import com.wgblackmon.aihealthcare.domain.model.IncidentType;
import com.wgblackmon.aihealthcare.domain.model.LiabilityIncident;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for {@link LiabilityIncidentAdapter}.
 *
 * <p>Verifies that all enum fields round-trip correctly through the
 * String-based storage convention, that nullable fields survive a
 * save/load cycle, and that {@code findAll} returns rows in
 * descending filing-date order.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-16
 * @updated 2026-11-16
 */
@DataJpaTest
class LiabilityIncidentAdapterTest {

    @Autowired
    private LiabilityIncidentRepository repository;

    private LiabilityIncidentAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LiabilityIncidentAdapter(repository, mock(NewsArticleRepository.class));
    }

    private LiabilityIncident incident(String id, IncidentType type, HarmType harm,
                                       IncidentStatus status, LocalDate filingDate) {
        Instant now = Instant.now();
        return new LiabilityIncident(
                id, type, harm, status,
                "Test incident " + id,
                "Plaintiff A",
                "Defendant Corp",
                "Product liability",
                "USDC Northern CA",
                "24-CV-001",
                LocalDate.of(2026, 1, 15),
                filingDate,
                null, null, null, now, now
        );
    }

    @Test
    void save_andFindById_roundTripsAllFields() {
        LiabilityIncident original = incident(UUID.randomUUID().toString(),
                IncidentType.LAWSUIT, HarmType.MISDIAGNOSIS, IncidentStatus.ACTIVE,
                LocalDate.of(2026, 11, 1));

        adapter.save(original);

        LiabilityIncident loaded = adapter.findById(original.incidentId());

        assertThat(loaded).isNotNull();
        assertThat(loaded.incidentId()).isEqualTo(original.incidentId());
        assertThat(loaded.incidentType()).isEqualTo(IncidentType.LAWSUIT);
        assertThat(loaded.harmType()).isEqualTo(HarmType.MISDIAGNOSIS);
        assertThat(loaded.status()).isEqualTo(IncidentStatus.ACTIVE);
        assertThat(loaded.title()).isEqualTo(original.title());
        assertThat(loaded.defendant()).isEqualTo("Defendant Corp");
        assertThat(loaded.legalTheory()).isEqualTo("Product liability");
        assertThat(loaded.court()).isEqualTo("USDC Northern CA");
        assertThat(loaded.filingDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(loaded.outcome()).isNull();
    }

    @Test
    void save_allEnumCombinations_roundTripCleanly() {
        for (IncidentType type : IncidentType.values()) {
            for (HarmType harm : HarmType.values()) {
                String id = UUID.randomUUID().toString();
                LiabilityIncident i = incident(id, type, harm, IncidentStatus.FILED, null);
                adapter.save(i);
                LiabilityIncident loaded = adapter.findById(id);
                assertThat(loaded.incidentType()).isEqualTo(type);
                assertThat(loaded.harmType()).isEqualTo(harm);
            }
        }
    }

    @Test
    void findAll_returnsRowsInFilingDateDescOrder() {
        LiabilityIncident older = incident(UUID.randomUUID().toString(),
                IncidentType.LAWSUIT, HarmType.DELAYED_CARE, IncidentStatus.FILED,
                LocalDate.of(2026, 6, 1));
        LiabilityIncident newer = incident(UUID.randomUUID().toString(),
                IncidentType.WRONGFUL_DEATH, HarmType.MEDICATION_ERROR, IncidentStatus.ACTIVE,
                LocalDate.of(2026, 11, 13));

        adapter.save(older);
        adapter.save(newer);

        List<LiabilityIncident> results = adapter.findAll();

        assertThat(results).hasSizeGreaterThanOrEqualTo(2);
        int newerIdx = -1;
        int olderIdx = -1;
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).incidentId().equals(newer.incidentId())) newerIdx = i;
            if (results.get(i).incidentId().equals(older.incidentId())) olderIdx = i;
        }
        assertThat(newerIdx).isLessThan(olderIdx);
    }

    @Test
    void findById_returnsNull_whenNotFound() {
        LiabilityIncident result = adapter.findById("nonexistent-id");
        assertThat(result).isNull();
    }

    @Test
    void deleteById_removesRecord() {
        LiabilityIncident i = incident(UUID.randomUUID().toString(),
                IncidentType.FDA_FLAG, HarmType.AUTONOMOUS_DEVICE, IncidentStatus.FILED, null);
        adapter.save(i);

        adapter.deleteById(i.incidentId());

        assertThat(adapter.findById(i.incidentId())).isNull();
    }

    @Test
    void save_withAllNullableFieldsNull_doesNotThrow() {
        Instant now = Instant.now();
        LiabilityIncident minimal = new LiabilityIncident(
                UUID.randomUUID().toString(),
                IncidentType.REGULATORY_SANCTION,
                HarmType.OTHER,
                IncidentStatus.DECIDED,
                "Minimal incident",
                null, "Defendant Co", null, null, null,
                null, null, null, null, null, now, now
        );
        adapter.save(minimal);
        LiabilityIncident loaded = adapter.findById(minimal.incidentId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.plaintiff()).isNull();
        assertThat(loaded.legalTheory()).isNull();
        assertThat(loaded.filingDate()).isNull();
    }
}
