package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.FeaturePost;
import com.wgblackmon.aihealthcare.domain.model.FeaturePostSlot;
import com.wgblackmon.aihealthcare.domain.port.inbound.RotateFeaturePostsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.FeaturePostPort;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Domain service resolving a calendar date to the LinkedIn feature post due
 * that day.
 *
 * The rotation is a fixed-length weekday cycle anchored to a Monday. Week
 * position is computed as the number of whole weeks between the anchor and the
 * date's own Monday, taken modulo the cycle length, so the schedule repeats
 * indefinitely without any stored state and resolves dates before the anchor
 * as correctly as dates after it.
 *
 * This class contains the whole of the rotation rule and nothing else. It has
 * no dependency on Spring, Lombok, or the YAML the posts happen to be stored
 * in — it takes a port and a clock-independent date argument, which is what
 * makes the rule directly unit-testable without a running context.
 *
 * Per the project's domain-purity convention this class uses the JDK's
 * {@code System.Logger} rather than Lombok's {@code @Slf4j}, which is not
 * available inside the domain package.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-05
 * @updated 2026-09-05
 */
public class FeaturePostRotationService implements RotateFeaturePostsUseCase {

    private static final System.Logger log =
            System.getLogger(FeaturePostRotationService.class.getName());

    /** Days scanned forward before {@link #nextPostFrom(LocalDate)} gives up. */
    private static final int FORWARD_SCAN_DAYS = 400;

    private final FeaturePostPort featurePostPort;
    private final LocalDate anchorMonday;

    /**
     * Creates the rotation service.
     *
     * @param featurePostPort supplies the post library
     * @param anchorDate      any date in the week the cycle starts; normalised
     *                        internally to that week's Monday
     */
    public FeaturePostRotationService(FeaturePostPort featurePostPort, LocalDate anchorDate) {
        Objects.requireNonNull(featurePostPort, "featurePostPort must not be null");
        Objects.requireNonNull(anchorDate, "anchorDate must not be null");
        this.featurePostPort = featurePostPort;
        this.anchorMonday = mondayOf(anchorDate);
        log.log(System.Logger.Level.DEBUG,
                () -> "FeaturePostRotationService() | anchorMonday=" + this.anchorMonday);
    }

    @Override
    public Optional<FeaturePost> postFor(LocalDate date) {
        log.log(System.Logger.Level.DEBUG, () -> "postFor() | date=" + date);
        if (date == null || isWeekend(date)) {
            return Optional.empty();
        }
        int cycleWeeks = featurePostPort.cycleWeeks();
        if (cycleWeeks < 1) {
            return Optional.empty();
        }
        FeaturePostSlot slot = slotFor(date, cycleWeeks);
        FeaturePost post = indexBySlot().get(slot.key());
        return Optional.ofNullable(post);
    }

    @Override
    public Optional<FeaturePost> nextPostFrom(LocalDate date) {
        log.log(System.Logger.Level.DEBUG, () -> "nextPostFrom() | date=" + date);
        if (date == null) {
            return Optional.empty();
        }
        LocalDate cursor = date;
        for (int day = 0; day < FORWARD_SCAN_DAYS; day++) {
            Optional<FeaturePost> found = postFor(cursor);
            if (found.isPresent()) {
                return found;
            }
            cursor = cursor.plusDays(1);
        }
        log.log(System.Logger.Level.WARNING,
                () -> "nextPostFrom() found no post within " + FORWARD_SCAN_DAYS
                        + " days of " + date + " — is the library empty?");
        return Optional.empty();
    }

    @Override
    public List<FeaturePost> weekOf(LocalDate date) {
        log.log(System.Logger.Level.DEBUG, () -> "weekOf() | date=" + date);
        List<FeaturePost> week = new ArrayList<>();
        if (date == null) {
            return week;
        }
        LocalDate monday = mondayOf(date);
        for (int offset = 0; offset < 5; offset++) {
            Optional<FeaturePost> found = postFor(monday.plusDays(offset));
            if (found.isPresent()) {
                week.add(found.get());
            }
        }
        return week;
    }

    @Override
    public List<FeaturePost> schedule() {
        log.log(System.Logger.Level.DEBUG, () -> "schedule()");
        List<FeaturePost> ordered = new ArrayList<>(featurePostPort.findAll());
        ordered.sort(Comparator
                .comparingInt((FeaturePost post) -> post.slot().week())
                .thenComparingInt(post -> post.slot().weekday().getValue()));
        return ordered;
    }

    @Override
    public Optional<FeaturePost> findById(String id) {
        log.log(System.Logger.Level.DEBUG, () -> "findById() | id=" + id);
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        List<FeaturePost> posts = featurePostPort.findAll();
        for (FeaturePost post : posts) {
            if (id.equals(post.id())) {
                return Optional.of(post);
            }
        }
        return Optional.empty();
    }

    /**
     * Computes the rotation slot a date falls on.
     *
     * Uses floor-modulo so that dates before the anchor wrap backwards through
     * the cycle rather than producing a negative week number.
     *
     * @param date       the weekday to resolve
     * @param cycleWeeks the cycle length
     * @return the slot for that date
     */
    private FeaturePostSlot slotFor(LocalDate date, int cycleWeeks) {
        long weeksElapsed = ChronoUnit.WEEKS.between(anchorMonday, mondayOf(date));
        int weekIndex = Math.floorMod((int) weeksElapsed, cycleWeeks);
        return new FeaturePostSlot(weekIndex + 1, date.getDayOfWeek());
    }

    /**
     * Builds the slot-key to post index.
     *
     * Rebuilt per call rather than cached, because the adapter behind the port
     * owns caching and a stale index here would silently outlive a library
     * reload. The library is tens of entries; the cost is not worth a
     * correctness risk.
     *
     * @return posts keyed by {@link FeaturePostSlot#key()}
     */
    private Map<String, FeaturePost> indexBySlot() {
        Map<String, FeaturePost> index = new HashMap<>();
        List<FeaturePost> posts = featurePostPort.findAll();
        for (FeaturePost post : posts) {
            String key = post.slot().key();
            FeaturePost existing = index.put(key, post);
            if (existing != null) {
                log.log(System.Logger.Level.WARNING,
                        () -> "indexBySlot() | slot " + key + " claimed by both '"
                                + existing.id() + "' and '" + post.id()
                                + "' — the later entry wins");
            }
        }
        return index;
    }

    /**
     * Returns the Monday of the week containing the given date.
     *
     * @param date any date
     * @return that week's Monday
     */
    private static LocalDate mondayOf(LocalDate date) {
        int shift = date.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue();
        return date.minusDays(shift);
    }

    /**
     * Indicates whether the given date falls on a weekend.
     *
     * @param date the date to test
     * @return true for Saturday and Sunday
     */
    private static boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
