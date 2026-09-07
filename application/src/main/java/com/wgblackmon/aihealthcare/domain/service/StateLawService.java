package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.LawCategory;
import com.wgblackmon.aihealthcare.domain.model.LawChangeEvent;
import com.wgblackmon.aihealthcare.domain.model.LawSource;
import com.wgblackmon.aihealthcare.domain.model.LawStatus;
import com.wgblackmon.aihealthcare.domain.model.NewBillCandidate;
import com.wgblackmon.aihealthcare.domain.model.SourceCheckResult;
import com.wgblackmon.aihealthcare.domain.model.StateCode;
import com.wgblackmon.aihealthcare.domain.model.StateLaw;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageStateLawsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.LawChangeEventPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.LawSourceMonitorPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewBillCandidatePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.StateLawPort;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pure domain service implementing the {@link ManageStateLawsUseCase} inbound port
 * for the state health-AI legislation registry.
 *
 * <p>All query methods delegate to the appropriate outbound port. The
 * {@link #getUpcoming(int)} method filters in-memory by parsing each law's
 * {@code effectiveDate} as an ISO {@link LocalDate} and comparing it to today.
 *
 * <p>This class has no Spring, Lombok, or framework dependencies. It is wired
 * via {@code AppConfig} as a plain bean. Logging uses the JDK's
 * {@link System.Logger} per the domain-purity convention.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
public class StateLawService implements ManageStateLawsUseCase {

    private static final System.Logger log =
            System.getLogger(StateLawService.class.getName());

    private final StateLawPort stateLawPort;
    private final LawChangeEventPort changeEventPort;
    private final NewBillCandidatePort candidatePort;
    private final LawSourceMonitorPort sourceMonitorPort;

    public StateLawService(StateLawPort stateLawPort,
                           LawChangeEventPort changeEventPort,
                           NewBillCandidatePort candidatePort,
                           LawSourceMonitorPort sourceMonitorPort) {
        log.log(System.Logger.Level.DEBUG,
                () -> "StateLawService() | stateLawPort=" + stateLawPort
                        + ", changeEventPort=" + changeEventPort
                        + ", candidatePort=" + candidatePort
                        + ", sourceMonitorPort=" + sourceMonitorPort);
        this.stateLawPort = stateLawPort;
        this.changeEventPort = changeEventPort;
        this.candidatePort = candidatePort;
        this.sourceMonitorPort = sourceMonitorPort;
    }

    @Override
    public List<StateLaw> getAll() {
        log.log(System.Logger.Level.DEBUG, () -> "getAll()");
        List<StateLaw> result = stateLawPort.findAll();
        log.log(System.Logger.Level.DEBUG, () -> "getAll() | return=" + result.size());
        return result;
    }

    @Override
    public Optional<StateLaw> getById(String id) {
        log.log(System.Logger.Level.DEBUG, () -> "getById() | id=" + id);
        Optional<StateLaw> result = stateLawPort.findById(id);
        log.log(System.Logger.Level.DEBUG, () -> "getById() | return=" + result);
        return result;
    }

    @Override
    public List<StateLaw> getByState(StateCode stateCode) {
        log.log(System.Logger.Level.DEBUG, () -> "getByState() | stateCode=" + stateCode);
        List<StateLaw> result = stateLawPort.findByState(stateCode);
        log.log(System.Logger.Level.DEBUG, () -> "getByState() | return=" + result.size());
        return result;
    }

    @Override
    public List<StateLaw> getByCategory(LawCategory category) {
        log.log(System.Logger.Level.DEBUG, () -> "getByCategory() | category=" + category);
        List<StateLaw> result = stateLawPort.findByCategory(category);
        log.log(System.Logger.Level.DEBUG, () -> "getByCategory() | return=" + result.size());
        return result;
    }

    @Override
    public List<StateLaw> getByStatus(LawStatus status) {
        log.log(System.Logger.Level.DEBUG, () -> "getByStatus() | status=" + status);
        List<StateLaw> result = stateLawPort.findByStatus(status);
        log.log(System.Logger.Level.DEBUG, () -> "getByStatus() | return=" + result.size());
        return result;
    }

    @Override
    public List<StateLaw> getUpcoming(int days) {
        log.log(System.Logger.Level.DEBUG, () -> "getUpcoming() | days=" + days);
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(days);

        List<StateLaw> allLaws = stateLawPort.findAll();
        List<StateLaw> upcoming = new ArrayList<>();

        for (StateLaw law : allLaws) {
            if (law.effectiveDate() == null || law.effectiveDate().isBlank()) {
                continue;
            }
            try {
                LocalDate effective = LocalDate.parse(law.effectiveDate());
                if (!effective.isBefore(today) && !effective.isAfter(horizon)) {
                    upcoming.add(law);
                }
            } catch (Exception e) {
                log.log(System.Logger.Level.WARNING,
                        () -> "getUpcoming() | skipping law " + law.id()
                                + " — unparseable effectiveDate: " + law.effectiveDate());
            }
        }

        log.log(System.Logger.Level.DEBUG, () -> "getUpcoming() | return=" + upcoming.size());
        return upcoming;
    }

    @Override
    public List<StateLaw> search(String query) {
        log.log(System.Logger.Level.DEBUG, () -> "search() | query=" + query);
        List<StateLaw> result = stateLawPort.search(query);
        log.log(System.Logger.Level.DEBUG, () -> "search() | return=" + result.size());
        return result;
    }

    @Override
    public List<LawChangeEvent> getUnreviewedChanges() {
        log.log(System.Logger.Level.DEBUG, () -> "getUnreviewedChanges()");
        List<LawChangeEvent> result = changeEventPort.findUnreviewed();
        log.log(System.Logger.Level.DEBUG,
                () -> "getUnreviewedChanges() | return=" + result.size());
        return result;
    }

    @Override
    public void reviewChange(Long eventId) {
        log.log(System.Logger.Level.DEBUG, () -> "reviewChange() | eventId=" + eventId);
        changeEventPort.markReviewed(eventId);
        log.log(System.Logger.Level.DEBUG, () -> "reviewChange() | return=void");
    }

    @Override
    public List<NewBillCandidate> getUnreviewedCandidates() {
        log.log(System.Logger.Level.DEBUG, () -> "getUnreviewedCandidates()");
        List<NewBillCandidate> result = candidatePort.findUnreviewed();
        log.log(System.Logger.Level.DEBUG,
                () -> "getUnreviewedCandidates() | return=" + result.size());
        return result;
    }

    @Override
    public int triggerRefresh() {
        log.log(System.Logger.Level.DEBUG, () -> "triggerRefresh()");

        List<StateLaw> allLaws = stateLawPort.findAll();
        int changedCount = 0;
        int checkedCount = 0;

        for (StateLaw law : allLaws) {
            for (LawSource source : law.sources()) {
                try {
                    SourceCheckResult result = sourceMonitorPort.checkUrl(
                            source.url(), source.lastContentHash());
                    checkedCount++;

                    stateLawPort.updateSourceMonitoringFields(
                            law.id(), source.url(),
                            result.fetchedAt(), result.contentHash(),
                            result.httpStatus(), result.changed());

                    if (result.changed()) {
                        changedCount++;
                        String detail = result.errorMessage() != null
                                ? "URL returned HTTP " + result.httpStatus() + ": " + result.errorMessage()
                                : "Content hash changed (HTTP " + result.httpStatus() + ")";
                        LawChangeEvent event = new LawChangeEvent(
                                null, law.id(),
                                result.fetchedAt(),
                                result.httpStatus() >= 400 ? "URL_UNAVAILABLE" : "CONTENT_CHANGED",
                                detail + " — " + source.url(),
                                false);
                        changeEventPort.recordChangeEvent(event);
                    }
                } catch (Exception e) {
                    log.log(System.Logger.Level.WARNING,
                            () -> "triggerRefresh() | failed checking " + source.url()
                                    + " for law " + law.id() + ": " + e.getMessage());
                }
            }
        }

        final int finalChecked = checkedCount;
        final int finalChanged = changedCount;
        log.log(System.Logger.Level.INFO,
                () -> "triggerRefresh() | checked=" + finalChecked
                        + ", changed=" + finalChanged);
        log.log(System.Logger.Level.DEBUG, () -> "triggerRefresh() | return=" + finalChanged);
        return changedCount;
    }
}
