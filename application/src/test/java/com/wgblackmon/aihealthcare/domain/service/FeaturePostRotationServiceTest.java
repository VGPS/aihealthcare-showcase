package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.FeaturePost;
import com.wgblackmon.aihealthcare.domain.model.FeaturePostScreenshot;
import com.wgblackmon.aihealthcare.domain.model.FeaturePostSlot;
import com.wgblackmon.aihealthcare.domain.port.outbound.FeaturePostPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link FeaturePostRotationService}.
 *
 * The rotation rule is pure date arithmetic over an in-memory library, so
 * these tests use a hand-rolled stub port rather than a mocking framework or a
 * Spring context — the same mock-port pattern used by the newsletter tests.
 *
 * The anchor throughout is Monday 2026-09-07, and the stub library fills every
 * slot of a two-week cycle unless a test says otherwise.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-05
 * @updated 2026-09-05
 */
class FeaturePostRotationServiceTest {

    private static final LocalDate ANCHOR = LocalDate.of(2026, 9, 7); // a Monday

    private StubFeaturePostPort port;
    private FeaturePostRotationService service;

    @BeforeEach
    void setUp() {
        port = new StubFeaturePostPort(twoWeekLibrary());
        service = new FeaturePostRotationService(port, ANCHOR);
    }

    @Test
    @DisplayName("anchor Monday resolves to week 1 Monday")
    void anchorMondayResolvesToFirstSlot() {
        Optional<FeaturePost> post = service.postFor(ANCHOR);

        assertTrue(post.isPresent());
        assertEquals("w1-MONDAY", post.get().id());
    }

    @Test
    @DisplayName("a mid-cycle weekday resolves to its own slot")
    void midCycleWeekdayResolvesCorrectly() {
        // Thursday of the second week of the cycle
        Optional<FeaturePost> post = service.postFor(ANCHOR.plusDays(7 + 3));

        assertTrue(post.isPresent());
        assertEquals("w2-THURSDAY", post.get().id());
    }

    @Test
    @DisplayName("the cycle wraps once the last week is exhausted")
    void cycleWrapsAfterLastWeek() {
        // Week 3 of the calendar is week 1 of a two-week cycle
        Optional<FeaturePost> post = service.postFor(ANCHOR.plusWeeks(2));

        assertTrue(post.isPresent());
        assertEquals("w1-MONDAY", post.get().id());
    }

    @Test
    @DisplayName("dates before the anchor wrap backwards rather than going negative")
    void datesBeforeAnchorWrapBackwards() {
        // One week before the anchor is week 2 of the cycle, not week zero
        Optional<FeaturePost> post = service.postFor(ANCHOR.minusWeeks(1));

        assertTrue(post.isPresent());
        assertEquals("w2-MONDAY", post.get().id());
    }

    @Test
    @DisplayName("weekends have no scheduled post")
    void weekendsHaveNoPost() {
        LocalDate saturday = ANCHOR.plusDays(5);
        LocalDate sunday = ANCHOR.plusDays(6);

        assertTrue(service.postFor(saturday).isEmpty());
        assertTrue(service.postFor(sunday).isEmpty());
    }

    @Test
    @DisplayName("nextPostFrom skips the weekend to Monday")
    void nextPostFromSkipsWeekend() {
        LocalDate saturday = ANCHOR.plusDays(5);

        Optional<FeaturePost> post = service.nextPostFrom(saturday);

        assertTrue(post.isPresent());
        assertEquals("w2-MONDAY", post.get().id());
    }

    @Test
    @DisplayName("nextPostFrom returns the same day's post when one is scheduled")
    void nextPostFromReturnsSameDayWhenScheduled() {
        Optional<FeaturePost> post = service.nextPostFrom(ANCHOR);

        assertTrue(post.isPresent());
        assertEquals("w1-MONDAY", post.get().id());
    }

    @Test
    @DisplayName("nextPostFrom skips an unfilled slot")
    void nextPostFromSkipsUnfilledSlot() {
        List<FeaturePost> sparse = new ArrayList<>();
        sparse.add(post("only", 1, DayOfWeek.WEDNESDAY));
        service = new FeaturePostRotationService(new StubFeaturePostPort(sparse), ANCHOR);

        Optional<FeaturePost> post = service.nextPostFrom(ANCHOR);

        assertTrue(post.isPresent());
        assertEquals("only", post.get().id());
    }

    @Test
    @DisplayName("weekOf returns Monday through Friday in order")
    void weekOfReturnsWeekdaysInOrder() {
        List<FeaturePost> week = service.weekOf(ANCHOR.plusDays(2));

        assertEquals(5, week.size());
        assertEquals(DayOfWeek.MONDAY, week.get(0).slot().weekday());
        assertEquals(DayOfWeek.FRIDAY, week.get(4).slot().weekday());
        assertEquals("w1-FRIDAY", week.get(4).id());
    }

    @Test
    @DisplayName("schedule is ordered by week then weekday regardless of file order")
    void scheduleIsOrdered() {
        List<FeaturePost> shuffled = new ArrayList<>();
        shuffled.add(post("late", 2, DayOfWeek.FRIDAY));
        shuffled.add(post("early", 1, DayOfWeek.TUESDAY));
        shuffled.add(post("middle", 1, DayOfWeek.THURSDAY));
        service = new FeaturePostRotationService(new StubFeaturePostPort(shuffled), ANCHOR);

        List<FeaturePost> ordered = service.schedule();

        assertEquals("early", ordered.get(0).id());
        assertEquals("middle", ordered.get(1).id());
        assertEquals("late", ordered.get(2).id());
    }

    @Test
    @DisplayName("findById returns the matching post and empty for anything else")
    void findByIdMatchesOnlyExactIds() {
        assertTrue(service.findById("w1-MONDAY").isPresent());
        assertTrue(service.findById("no-such-post").isEmpty());
        assertTrue(service.findById(null).isEmpty());
        assertTrue(service.findById("   ").isEmpty());
    }

    @Test
    @DisplayName("an empty library yields no post rather than an error")
    void emptyLibraryYieldsNoPost() {
        service = new FeaturePostRotationService(
                new StubFeaturePostPort(new ArrayList<>()), ANCHOR);

        assertTrue(service.postFor(ANCHOR).isEmpty());
        assertTrue(service.nextPostFrom(ANCHOR).isEmpty());
        assertTrue(service.weekOf(ANCHOR).isEmpty());
    }

    @Test
    @DisplayName("an anchor given as a mid-week date is normalised to that week's Monday")
    void anchorIsNormalisedToMonday() {
        FeaturePostRotationService wednesdayAnchored =
                new FeaturePostRotationService(port, ANCHOR.plusDays(2));

        Optional<FeaturePost> post = wednesdayAnchored.postFor(ANCHOR);

        assertTrue(post.isPresent());
        assertEquals("w1-MONDAY", post.get().id());
    }

    @Test
    @DisplayName("a live variant with placeholders is reported as not ready")
    void unresolvedTokensAreReported() {
        FeaturePost withTokens = new FeaturePost(
                "tokens", "Feature", "Menu", "/url",
                new FeaturePostSlot(1, DayOfWeek.MONDAY), "signal",
                "hook", "body",
                "Top mover was {{COMPANY}} at {{AMOUNT}} — and {{COMPANY}} again.",
                "links", List.of("#Tag"),
                new FeaturePostScreenshot("/p", "None", "c", "r", "a"));

        List<String> tokens = withTokens.unresolvedTokens();

        assertEquals(2, tokens.size());
        assertEquals("COMPANY", tokens.get(0));
        assertEquals("AMOUNT", tokens.get(1));
        assertFalse(withTokens.liveVariantReady());
    }

    // ---------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------

    /**
     * Builds a fully-populated two-week cycle: ten posts, ids w1-MONDAY
     * through w2-FRIDAY.
     *
     * @return the stub library
     */
    private static List<FeaturePost> twoWeekLibrary() {
        List<FeaturePost> library = new ArrayList<>();
        DayOfWeek[] weekdays = {
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
        };
        for (int week = 1; week <= 2; week++) {
            for (DayOfWeek weekday : weekdays) {
                library.add(post("w" + week + "-" + weekday.name(), week, weekday));
            }
        }
        return library;
    }

    private static FeaturePost post(String id, int week, DayOfWeek weekday) {
        return new FeaturePost(
                id, "Feature " + id, "Menu > " + id, "/dashboard/" + id,
                new FeaturePostSlot(week, weekday), "theme",
                "hook", "body for " + id, "live variant", "first comment",
                List.of("#HealthcareAI"),
                new FeaturePostScreenshot("/page", "None", "capture", "redact", "alt"));
    }

    /**
     * Hand-rolled stub standing in for the YAML adapter.
     */
    private static final class StubFeaturePostPort implements FeaturePostPort {

        private final List<FeaturePost> posts;

        private StubFeaturePostPort(List<FeaturePost> posts) {
            this.posts = posts;
        }

        @Override
        public List<FeaturePost> findAll() {
            return posts;
        }

        @Override
        public int cycleWeeks() {
            int highest = 0;
            for (FeaturePost post : posts) {
                if (post.slot().week() > highest) {
                    highest = post.slot().week();
                }
            }
            return highest;
        }
    }
}
