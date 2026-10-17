package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.CompanyOutreach;
import com.wgblackmon.aihealthcare.domain.model.HealthcareAiCompany;
import com.wgblackmon.aihealthcare.domain.model.OutreachPurpose;
import com.wgblackmon.aihealthcare.domain.model.OutreachStatus;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyOutreachPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.HealthcareAiCompanyPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OutreachSyncService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-17
 * @updated 2026-10-17
 */
@ExtendWith(MockitoExtension.class)
class OutreachSyncServiceTest {

    @Mock
    private CompanyOutreachPort outreachPort;

    @Mock
    private HealthcareAiCompanyPort companyPort;

    private OutreachSyncService service;

    private static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");

    @BeforeEach
    void setUp() {
        service = new OutreachSyncService(outreachPort, companyPort);
    }

    private static HealthcareAiCompany company(String id, String name) {
        return new HealthcareAiCompany(id, name, id, null, null, null, null,
                null, null, null, null, null, null, List.of(), false, List.of(), NOW, NOW);
    }

    private static CompanyOutreach row(String slug, OutreachPurpose purpose) {
        return new CompanyOutreach(1L, slug, purpose, OutreachStatus.NOT_STARTED,
                null, null, null, null, NOW, NOW);
    }

    @Test
    void syncOutreachRows_noCompanies_returnsZero() {
        when(companyPort.findAll()).thenReturn(List.of());
        when(outreachPort.findAll()).thenReturn(List.of());

        int created = service.syncOutreachRows();

        assertThat(created).isZero();
        verify(outreachPort, never()).save(any());
    }

    @Test
    void syncOutreachRows_allRowsExist_returnsZero() {
        when(companyPort.findAll()).thenReturn(List.of(company("tempus", "Tempus AI")));
        when(outreachPort.findAll()).thenReturn(List.of(
                row("tempus", OutreachPurpose.EMPLOYMENT),
                row("tempus", OutreachPurpose.SUBSCRIPTION)));

        int created = service.syncOutreachRows();

        assertThat(created).isZero();
        verify(outreachPort, never()).save(any());
    }

    @Test
    void syncOutreachRows_newCompany_createsBothRows() {
        when(companyPort.findAll()).thenReturn(List.of(company("aidoc", "Aidoc")));
        when(outreachPort.findAll()).thenReturn(List.of());
        when(outreachPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int created = service.syncOutreachRows();

        assertThat(created).isEqualTo(2);
        ArgumentCaptor<CompanyOutreach> captor = ArgumentCaptor.forClass(CompanyOutreach.class);
        verify(outreachPort, times(2)).save(captor.capture());
        List<CompanyOutreach> saved = captor.getAllValues();
        assertThat(saved).extracting(CompanyOutreach::slug).containsOnly("aidoc");
        assertThat(saved).extracting(CompanyOutreach::purpose)
                .containsExactlyInAnyOrder(OutreachPurpose.EMPLOYMENT, OutreachPurpose.SUBSCRIPTION);
        assertThat(saved).extracting(CompanyOutreach::status)
                .containsOnly(OutreachStatus.NOT_STARTED);
    }

    @Test
    void syncOutreachRows_partialRowsExist_createsOnlyMissing() {
        when(companyPort.findAll()).thenReturn(List.of(company("nabla", "Nabla")));
        when(outreachPort.findAll()).thenReturn(List.of(row("nabla", OutreachPurpose.EMPLOYMENT)));
        when(outreachPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int created = service.syncOutreachRows();

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<CompanyOutreach> captor = ArgumentCaptor.forClass(CompanyOutreach.class);
        verify(outreachPort, times(1)).save(captor.capture());
        assertThat(captor.getValue().purpose()).isEqualTo(OutreachPurpose.SUBSCRIPTION);
    }

    @Test
    void syncOutreachRows_neverCreatesAcquisitionRows() {
        when(companyPort.findAll()).thenReturn(List.of(company("tempus", "Tempus AI")));
        when(outreachPort.findAll()).thenReturn(List.of());
        when(outreachPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int created = service.syncOutreachRows();

        assertThat(created).isEqualTo(2);
        ArgumentCaptor<CompanyOutreach> captor = ArgumentCaptor.forClass(CompanyOutreach.class);
        verify(outreachPort, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(CompanyOutreach::purpose)
                .doesNotContain(OutreachPurpose.ACQUISITION);
    }

    @Test
    void syncOutreachRows_idempotent_secondRunCreatesZero() {
        when(companyPort.findAll()).thenReturn(List.of(company("khealth", "K Health")));
        when(outreachPort.findAll()).thenReturn(List.of());
        when(outreachPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.syncOutreachRows();

        // Second run: now both rows exist
        when(outreachPort.findAll()).thenReturn(List.of(
                row("khealth", OutreachPurpose.EMPLOYMENT),
                row("khealth", OutreachPurpose.SUBSCRIPTION)));

        int created = service.syncOutreachRows();

        assertThat(created).isZero();
    }
}
