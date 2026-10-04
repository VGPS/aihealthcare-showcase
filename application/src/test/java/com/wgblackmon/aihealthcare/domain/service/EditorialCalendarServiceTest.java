package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.EditorialDemandSignal;
import com.wgblackmon.aihealthcare.domain.model.EditorialEffort;
import com.wgblackmon.aihealthcare.domain.model.EditorialItem;
import com.wgblackmon.aihealthcare.domain.model.EditorialPriority;
import com.wgblackmon.aihealthcare.domain.model.EditorialStatus;
import com.wgblackmon.aihealthcare.domain.model.EditorialTheme;
import com.wgblackmon.aihealthcare.domain.port.outbound.EditorialCalendarPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EditorialCalendarService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
@ExtendWith(MockitoExtension.class)
class EditorialCalendarServiceTest {

    @Mock
    private EditorialCalendarPort editorialCalendarPort;

    private EditorialCalendarService service;

    @BeforeEach
    void setUp() {
        service = new EditorialCalendarService(editorialCalendarPort);
    }

    @Test
    void getAll_delegatesToPort() {
        EditorialItem item = createItem("slug-1", EditorialStatus.PLANNED);
        when(editorialCalendarPort.findAll()).thenReturn(List.of(item));

        List<EditorialItem> result = service.getAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("slug-1");
    }

    @Test
    void getById_found_returnsItem() {
        EditorialItem item = createItem("texas-traiga", EditorialStatus.PLANNED);
        when(editorialCalendarPort.findById("texas-traiga")).thenReturn(Optional.of(item));

        Optional<EditorialItem> result = service.getById("texas-traiga");

        assertThat(result).isPresent();
        assertThat(result.get().title()).isEqualTo("Test Title");
    }

    @Test
    void getById_notFound_returnsEmpty() {
        when(editorialCalendarPort.findById("nonexistent")).thenReturn(Optional.empty());

        Optional<EditorialItem> result = service.getById("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void getNext_delegatesToPort() {
        EditorialItem item = createItem("next-item", EditorialStatus.PLANNED);
        when(editorialCalendarPort.findNext()).thenReturn(Optional.of(item));

        Optional<EditorialItem> result = service.getNext();

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("next-item");
    }

    @Test
    void getQueue_excludesPublishedItems() {
        EditorialItem planned = createItem("planned-1", EditorialStatus.PLANNED);
        EditorialItem published = createItem("published-1", EditorialStatus.PUBLISHED);
        when(editorialCalendarPort.findAll()).thenReturn(List.of(planned, published));

        List<EditorialItem> result = service.getQueue();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("planned-1");
    }

    @Test
    void advanceStatus_planned_movesToResearching() {
        EditorialItem planned = createItem("slug-1", EditorialStatus.PLANNED);
        EditorialItem researching = createItem("slug-1", EditorialStatus.RESEARCHING);
        when(editorialCalendarPort.findById("slug-1"))
                .thenReturn(Optional.of(planned))
                .thenReturn(Optional.of(researching));

        EditorialItem result = service.advanceStatus("slug-1");

        verify(editorialCalendarPort).updateStatus("slug-1", EditorialStatus.RESEARCHING);
        assertThat(result.status()).isEqualTo(EditorialStatus.RESEARCHING);
    }

    @Test
    void advanceStatus_unknownId_throwsIllegalArgumentException() {
        when(editorialCalendarPort.findById("bad-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.advanceStatus("bad-id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bad-id");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private EditorialItem createItem(String id, EditorialStatus status) {
        return new EditorialItem(
                id, "Test Title", "Test hook",
                EditorialTheme.HEALTHCARE_GOVERNANCE,
                EditorialDemandSignal.HOT,
                EditorialPriority.P0,
                EditorialEffort.M,
                "explainer",
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 9),
                LocalDate.of(2026, 10, 6),
                "Download the checklist",
                status,
                LocalDate.of(2026, 10, 1),
                List.of("healthcare-compliance"),
                List.of(), null);
    }
}
