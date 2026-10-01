package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.EditorialItem;
import com.wgblackmon.aihealthcare.domain.model.EditorialStatus;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageEditorialCalendarUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.EditorialCalendarPort;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Pure domain service implementing the {@link ManageEditorialCalendarUseCase}
 * inbound port for the healthcare AI editorial calendar.
 *
 * <p>All write operations delegate to {@link EditorialCalendarPort}. The
 * {@link #getQueue()} method filters and sorts in-memory so the ordering
 * is controlled by domain logic, not JPA query clauses.
 *
 * <p>This class has no Spring, Lombok, or framework dependencies. It is wired
 * via {@code AppConfig} as a plain bean. Logging uses the JDK's
 * {@link System.Logger} per the domain-purity convention.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
public class EditorialCalendarService implements ManageEditorialCalendarUseCase {

    private static final System.Logger log =
            System.getLogger(EditorialCalendarService.class.getName());

    private final EditorialCalendarPort editorialCalendarPort;

    public EditorialCalendarService(EditorialCalendarPort editorialCalendarPort) {
        log.log(System.Logger.Level.DEBUG,
                () -> "EditorialCalendarService() | editorialCalendarPort=" + editorialCalendarPort);
        this.editorialCalendarPort = editorialCalendarPort;
    }

    @Override
    public List<EditorialItem> getAll() {
        log.log(System.Logger.Level.DEBUG, () -> "getAll()");
        List<EditorialItem> result = editorialCalendarPort.findAll();
        log.log(System.Logger.Level.DEBUG, () -> "getAll() | return=" + result.size());
        return result;
    }

    @Override
    public Optional<EditorialItem> getById(String id) {
        log.log(System.Logger.Level.DEBUG, () -> "getById() | id=" + id);
        Optional<EditorialItem> result = editorialCalendarPort.findById(id);
        log.log(System.Logger.Level.DEBUG, () -> "getById() | return=" + result.isPresent());
        return result;
    }

    @Override
    public List<EditorialItem> getByStatus(EditorialStatus status) {
        log.log(System.Logger.Level.DEBUG, () -> "getByStatus() | status=" + status);
        List<EditorialItem> result = editorialCalendarPort.findByStatus(status);
        log.log(System.Logger.Level.DEBUG, () -> "getByStatus() | return=" + result.size());
        return result;
    }

    @Override
    public List<EditorialItem> getPublished() {
        log.log(System.Logger.Level.DEBUG, () -> "getPublished()");
        List<EditorialItem> result = editorialCalendarPort.findByStatus(EditorialStatus.PUBLISHED);
        log.log(System.Logger.Level.DEBUG, () -> "getPublished() | return=" + result.size());
        return result;
    }

    @Override
    public List<EditorialItem> getQueue() {
        log.log(System.Logger.Level.DEBUG, () -> "getQueue()");
        List<EditorialItem> result = editorialCalendarPort.findAll().stream()
                .filter(item -> item.status() != EditorialStatus.PUBLISHED)
                .sorted(Comparator.comparing((EditorialItem i) -> i.priorityTier().name())
                        .thenComparing(EditorialItem::preferredDate))
                .collect(Collectors.toList());
        log.log(System.Logger.Level.DEBUG, () -> "getQueue() | return=" + result.size());
        return result;
    }

    @Override
    public Optional<EditorialItem> getNext() {
        log.log(System.Logger.Level.DEBUG, () -> "getNext()");
        Optional<EditorialItem> result = editorialCalendarPort.findNext();
        log.log(System.Logger.Level.DEBUG, () -> "getNext() | return=" + result.isPresent());
        return result;
    }

    @Override
    public EditorialItem advanceStatus(String id) {
        log.log(System.Logger.Level.DEBUG, () -> "advanceStatus() | id=" + id);
        EditorialItem item = editorialCalendarPort.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Editorial item not found: " + id));
        EditorialStatus next = item.status().next();
        editorialCalendarPort.updateStatus(id, next);
        EditorialItem result = editorialCalendarPort.findById(id)
                .orElseThrow(() -> new IllegalStateException("Item disappeared after status update: " + id));
        log.log(System.Logger.Level.DEBUG, () -> "advanceStatus() | return=" + result.status());
        return result;
    }
}
