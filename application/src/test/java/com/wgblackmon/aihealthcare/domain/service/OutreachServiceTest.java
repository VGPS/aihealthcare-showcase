package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.CompanyContact;
import com.wgblackmon.aihealthcare.domain.model.CompanyOutreach;
import com.wgblackmon.aihealthcare.domain.model.ContactSource;
import com.wgblackmon.aihealthcare.domain.model.ContactStatus;
import com.wgblackmon.aihealthcare.domain.model.OutreachPurpose;
import com.wgblackmon.aihealthcare.domain.model.OutreachStatus;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyContactPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyOutreachPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OutreachService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-03
 * @updated 2026-10-03
 */
@ExtendWith(MockitoExtension.class)
class OutreachServiceTest {

    @Mock
    private CompanyOutreachPort outreachPort;

    @Mock
    private CompanyContactPort contactPort;

    private OutreachService service;

    private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

    @BeforeEach
    void setUp() {
        service = new OutreachService(outreachPort, contactPort);
    }

    @Test
    void addOutreach_savesWithNotStartedStatus() {
        ArgumentCaptor<CompanyOutreach> cap = ArgumentCaptor.forClass(CompanyOutreach.class);
        CompanyOutreach saved = new CompanyOutreach(1L, "microsoft", OutreachPurpose.EMPLOYMENT,
                OutreachStatus.NOT_STARTED, null, "first notes", NOW, NOW);
        when(outreachPort.save(any())).thenReturn(saved);

        service.addOutreach("microsoft", OutreachPurpose.EMPLOYMENT, "first notes");

        verify(outreachPort).save(cap.capture());
        assertThat(cap.getValue().slug()).isEqualTo("microsoft");
        assertThat(cap.getValue().status()).isEqualTo(OutreachStatus.NOT_STARTED);
        assertThat(cap.getValue().contactedAt()).isNull();
    }

    @Test
    void updateOutreachStatus_firstTransitionOutOfNotStarted_setsContactedAt() {
        CompanyOutreach existing = new CompanyOutreach(1L, "acme", OutreachPurpose.SUBSCRIPTION,
                OutreachStatus.NOT_STARTED, null, null, NOW, NOW);
        when(outreachPort.findById(1L)).thenReturn(Optional.of(existing));
        ArgumentCaptor<CompanyOutreach> cap = ArgumentCaptor.forClass(CompanyOutreach.class);
        when(outreachPort.save(any())).thenAnswer(i -> i.getArgument(0));

        service.updateOutreachStatus(1L, OutreachStatus.IN_PROGRESS);

        verify(outreachPort).save(cap.capture());
        assertThat(cap.getValue().status()).isEqualTo(OutreachStatus.IN_PROGRESS);
        assertThat(cap.getValue().contactedAt()).isNotNull();
    }

    @Test
    void updateOutreachStatus_alreadyHasContactedAt_doesNotOverwrite() {
        CompanyOutreach existing = new CompanyOutreach(1L, "acme", OutreachPurpose.EMPLOYMENT,
                OutreachStatus.IN_PROGRESS, NOW, null, NOW, NOW);
        when(outreachPort.findById(1L)).thenReturn(Optional.of(existing));
        ArgumentCaptor<CompanyOutreach> cap = ArgumentCaptor.forClass(CompanyOutreach.class);
        when(outreachPort.save(any())).thenAnswer(i -> i.getArgument(0));

        service.updateOutreachStatus(1L, OutreachStatus.RESPONDED);

        verify(outreachPort).save(cap.capture());
        assertThat(cap.getValue().contactedAt()).isEqualTo(NOW);
    }

    @Test
    void updateOutreachStatus_notFound_throws() {
        when(outreachPort.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateOutreachStatus(99L, OutreachStatus.DECLINED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addContact_savesWithIdentifiedStatus() {
        ArgumentCaptor<CompanyContact> cap = ArgumentCaptor.forClass(CompanyContact.class);
        CompanyContact saved = new CompanyContact(1L, "acme", "Jane Smith", "VP", null, null,
                ContactSource.LINKEDIN, ContactStatus.IDENTIFIED, null, NOW, NOW);
        when(contactPort.save(any())).thenReturn(saved);

        service.addContact("acme", "Jane Smith", "VP", null, null, ContactSource.LINKEDIN, null);

        verify(contactPort).save(cap.capture());
        assertThat(cap.getValue().fullName()).isEqualTo("Jane Smith");
        assertThat(cap.getValue().status()).isEqualTo(ContactStatus.IDENTIFIED);
    }

    @Test
    void listContacts_delegatesToPort() {
        when(contactPort.findBySlug("acme")).thenReturn(List.of());
        List<CompanyContact> result = service.listContacts("acme");
        assertThat(result).isEmpty();
        verify(contactPort).findBySlug("acme");
    }

    @Test
    void listAllOutreach_delegatesToPort() {
        when(outreachPort.findAll()).thenReturn(List.of());
        service.listAllOutreach();
        verify(outreachPort).findAll();
    }
}
