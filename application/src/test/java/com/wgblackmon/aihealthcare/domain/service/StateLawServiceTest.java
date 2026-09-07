package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.LawCategory;
import com.wgblackmon.aihealthcare.domain.model.LawChangeEvent;
import com.wgblackmon.aihealthcare.domain.model.LawSource;
import com.wgblackmon.aihealthcare.domain.model.LawStatus;
import com.wgblackmon.aihealthcare.domain.model.NewBillCandidate;
import com.wgblackmon.aihealthcare.domain.model.SourceCheckResult;
import com.wgblackmon.aihealthcare.domain.model.SourceType;
import com.wgblackmon.aihealthcare.domain.model.StateCode;
import com.wgblackmon.aihealthcare.domain.model.StateLaw;
import com.wgblackmon.aihealthcare.domain.port.outbound.LawChangeEventPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.LawSourceMonitorPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewBillCandidatePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.StateLawPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StateLawService} with mocked outbound ports.
 *
 * <p>Verifies that each use-case method delegates to the correct port
 * and that {@link StateLawService#getUpcoming(int)} correctly filters
 * by effective date window.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
class StateLawServiceTest {

    private StateLawPort stateLawPort;
    private LawChangeEventPort changeEventPort;
    private NewBillCandidatePort candidatePort;
    private LawSourceMonitorPort sourceMonitorPort;
    private StateLawService service;

    @BeforeEach
    void setUp() {
        stateLawPort = mock(StateLawPort.class);
        changeEventPort = mock(LawChangeEventPort.class);
        candidatePort = mock(NewBillCandidatePort.class);
        sourceMonitorPort = mock(LawSourceMonitorPort.class);
        service = new StateLawService(stateLawPort, changeEventPort, candidatePort, sourceMonitorPort);
    }

    @Test
    void getAll_delegatesToPort() {
        StateLaw law = createTestLaw("ca-ab-3030", StateCode.CA, "AI Act");
        when(stateLawPort.findAll()).thenReturn(List.of(law));

        List<StateLaw> result = service.getAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("ca-ab-3030");
        verify(stateLawPort).findAll();
    }

    @Test
    void getById_found_returnsDomain() {
        StateLaw law = createTestLaw("ca-ab-3030", StateCode.CA, "AI Act");
        when(stateLawPort.findById("ca-ab-3030")).thenReturn(Optional.of(law));

        Optional<StateLaw> result = service.getById("ca-ab-3030");

        assertThat(result).isPresent();
        assertThat(result.get().title()).isEqualTo("AI Act");
        verify(stateLawPort).findById("ca-ab-3030");
    }

    @Test
    void getById_notFound_returnsEmpty() {
        when(stateLawPort.findById("nonexistent")).thenReturn(Optional.empty());

        Optional<StateLaw> result = service.getById("nonexistent");

        assertThat(result).isEmpty();
        verify(stateLawPort).findById("nonexistent");
    }

    @Test
    void getByState_delegatesToPort() {
        StateLaw law = createTestLaw("tx-hb-1709", StateCode.TX, "TX AI Law");
        when(stateLawPort.findByState(StateCode.TX)).thenReturn(List.of(law));

        List<StateLaw> result = service.getByState(StateCode.TX);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).stateCode()).isEqualTo(StateCode.TX);
        verify(stateLawPort).findByState(StateCode.TX);
    }

    @Test
    void getByCategory_delegatesToPort() {
        StateLaw law = createTestLaw("co-sb-1", StateCode.CO, "CO AI Act");
        when(stateLawPort.findByCategory(LawCategory.COMPREHENSIVE_AI_ACT))
                .thenReturn(List.of(law));

        List<StateLaw> result = service.getByCategory(LawCategory.COMPREHENSIVE_AI_ACT);

        assertThat(result).hasSize(1);
        verify(stateLawPort).findByCategory(LawCategory.COMPREHENSIVE_AI_ACT);
    }

    @Test
    void getByStatus_delegatesToPort() {
        StateLaw law = createTestLaw("co-sb-205", StateCode.CO, "CO AI Act");
        when(stateLawPort.findByStatus(LawStatus.ENACTED_STAYED))
                .thenReturn(List.of(law));

        List<StateLaw> result = service.getByStatus(LawStatus.ENACTED_STAYED);

        assertThat(result).hasSize(1);
        verify(stateLawPort).findByStatus(LawStatus.ENACTED_STAYED);
    }

    @Test
    void search_delegatesToPort() {
        StateLaw law = createTestLaw("ca-ab-3030", StateCode.CA, "AI Disclosure");
        when(stateLawPort.search("disclosure")).thenReturn(List.of(law));

        List<StateLaw> result = service.search("disclosure");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("AI Disclosure");
        verify(stateLawPort).search("disclosure");
    }

    @Test
    void getUpcoming_filtersWithinDays() {
        LocalDate today = LocalDate.now();
        String within30 = today.plusDays(15).toString();
        String within90 = today.plusDays(60).toString();
        String beyondWindow = today.plusDays(120).toString();
        String pastDate = today.minusDays(10).toString();

        StateLaw lawWithin = createTestLawWithEffectiveDate("law-1", StateCode.CA, within30);
        StateLaw lawAlsoWithin = createTestLawWithEffectiveDate("law-2", StateCode.TX, within90);
        StateLaw lawBeyond = createTestLawWithEffectiveDate("law-3", StateCode.NY, beyondWindow);
        StateLaw lawPast = createTestLawWithEffectiveDate("law-4", StateCode.FL, pastDate);
        StateLaw lawNoDate = createTestLaw("law-5", StateCode.CO, "No date law");

        when(stateLawPort.findAll()).thenReturn(
                List.of(lawWithin, lawAlsoWithin, lawBeyond, lawPast, lawNoDate));

        List<StateLaw> result = service.getUpcoming(90);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(StateLaw::id)
                .containsExactlyInAnyOrder("law-1", "law-2");
    }

    @Test
    void getUnreviewedChanges_delegatesToChangeEventPort() {
        LawChangeEvent event = new LawChangeEvent(1L, "ca-ab-3030", Instant.now(),
                "CONTENT_CHANGED", "Source content hash changed", false);
        when(changeEventPort.findUnreviewed()).thenReturn(List.of(event));

        List<LawChangeEvent> result = service.getUnreviewedChanges();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).lawId()).isEqualTo("ca-ab-3030");
        verify(changeEventPort).findUnreviewed();
    }

    @Test
    void reviewChange_delegatesToChangeEventPort() {
        service.reviewChange(42L);

        verify(changeEventPort).markReviewed(42L);
    }

    @Test
    void getUnreviewedCandidates_delegatesToCandidatePort() {
        NewBillCandidate candidate = new NewBillCandidate(1L, StateCode.NY, "SB 9999",
                "New AI Bill", "Summary", List.of("https://example.com"),
                Instant.now(), 0.85, false, null);
        when(candidatePort.findUnreviewed()).thenReturn(List.of(candidate));

        List<NewBillCandidate> result = service.getUnreviewedCandidates();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).billNumber()).isEqualTo("SB 9999");
        verify(candidatePort).findUnreviewed();
    }

    @Test
    void triggerRefresh_noChanges_returnsZero() {
        StateLaw law = createTestLaw("ca-ab-3030", StateCode.CA, "AI Act");
        when(stateLawPort.findAll()).thenReturn(List.of(law));

        SourceCheckResult unchanged = new SourceCheckResult(
                200, "same-hash", false, Instant.now(), null);
        when(sourceMonitorPort.checkUrl(anyString(), any())).thenReturn(unchanged);

        int result = service.triggerRefresh();

        assertThat(result).isZero();
        verify(changeEventPort, never()).recordChangeEvent(any());
        verify(stateLawPort).updateSourceMonitoringFields(
                eq("ca-ab-3030"), eq("https://example.com"),
                any(), eq("same-hash"), eq(200), eq(false));
    }

    @Test
    void triggerRefresh_contentChanged_recordsEvent() {
        StateLaw law = createTestLaw("ca-ab-3030", StateCode.CA, "AI Act");
        when(stateLawPort.findAll()).thenReturn(List.of(law));

        SourceCheckResult changed = new SourceCheckResult(
                200, "new-hash", true, Instant.now(), null);
        when(sourceMonitorPort.checkUrl(anyString(), any())).thenReturn(changed);

        int result = service.triggerRefresh();

        assertThat(result).isEqualTo(1);
        verify(changeEventPort).recordChangeEvent(any(LawChangeEvent.class));
    }

    @Test
    void triggerRefresh_urlUnavailable_recordsEventWithType() {
        StateLaw law = createTestLaw("ca-ab-3030", StateCode.CA, "AI Act");
        when(stateLawPort.findAll()).thenReturn(List.of(law));

        SourceCheckResult unavailable = new SourceCheckResult(
                404, null, true, Instant.now(), "HTTP 404 error response");
        when(sourceMonitorPort.checkUrl(anyString(), any())).thenReturn(unavailable);

        int result = service.triggerRefresh();

        assertThat(result).isEqualTo(1);
        verify(changeEventPort).recordChangeEvent(any(LawChangeEvent.class));
    }

    @Test
    void triggerRefresh_exceptionForOneSource_continuesProcessing() {
        StateLaw law1 = createTestLaw("law-1", StateCode.CA, "Law 1");
        StateLaw law2 = createTestLaw("law-2", StateCode.TX, "Law 2");
        when(stateLawPort.findAll()).thenReturn(List.of(law1, law2));

        when(sourceMonitorPort.checkUrl(anyString(), any()))
                .thenThrow(new RuntimeException("Network error"))
                .thenReturn(new SourceCheckResult(200, "hash", false, Instant.now(), null));

        int result = service.triggerRefresh();

        assertThat(result).isZero();
        verify(stateLawPort).updateSourceMonitoringFields(
                eq("law-2"), anyString(), any(), anyString(), any(Integer.class), anyBoolean());
    }

    // --- Helpers ---

    private StateLaw createTestLaw(String id, StateCode state, String title) {
        return new StateLaw(id, state, state.displayName(), "HB 100", title, 2026,
                "2026-06-01", null, null, null,
                LawStatus.ENACTED, null,
                List.of(LawCategory.PAYER_UTILIZATION_REVIEW),
                "Insurers", "Must disclose AI use", "Dept of Insurance",
                List.of(new LawSource(SourceType.OFFICIAL, "https://example.com",
                        null, null, null, false)),
                null, "1.0", Instant.now(), Instant.now());
    }

    private StateLaw createTestLawWithEffectiveDate(String id, StateCode state,
                                                     String effectiveDate) {
        return new StateLaw(id, state, state.displayName(), "HB 100", "Test Law", 2026,
                null, null, effectiveDate, null,
                LawStatus.ENACTED, null,
                List.of(LawCategory.PROVIDER_CLINICAL_USE),
                null, null, null,
                List.of(new LawSource(SourceType.OFFICIAL, "https://example.com",
                        null, null, null, false)),
                null, "1.0", Instant.now(), Instant.now());
    }
}
