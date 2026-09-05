package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.FeaturePost;
import com.wgblackmon.aihealthcare.domain.port.inbound.RotateFeaturePostsUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * Feature-post page at GET /dashboard/linkedin/features.
 *
 * Shows the LinkedIn post due today from the rotating feature library, ready
 * to copy, alongside the rest of the current week and the full schedule. It
 * sits beside the existing daily-article generator at /dashboard/linkedin
 * rather than replacing it: that page posts about the day's harvested news,
 * this one posts about a product feature on a fixed rotation.
 *
 * Three query parameters exist, all optional. {@code date} previews another
 * day, {@code id} jumps to a specific post regardless of schedule, and neither
 * changes any state — this page is read-only over the YAML library.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-05
 * @updated 2026-09-05
 */
@Slf4j
@Controller
public class FeaturePostController {

    private static final DateTimeFormatter DATE_LABEL =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy");

    private final RotateFeaturePostsUseCase rotateFeaturePostsUseCase;

    public FeaturePostController(RotateFeaturePostsUseCase rotateFeaturePostsUseCase) {
        log.debug("FeaturePostController()");
        this.rotateFeaturePostsUseCase = rotateFeaturePostsUseCase;
    }

    /**
     * Renders the feature post due on the requested date.
     *
     * @param dateParam optional ISO date to preview; defaults to today
     * @param id        optional post id, overriding the schedule
     * @param model     the view model
     * @return the feature-post view name
     */
    @GetMapping("/dashboard/linkedin/features")
    public String featurePost(@RequestParam(name = "date", required = false) String dateParam,
                              @RequestParam(name = "id", required = false) String id,
                              Model model) {
        log.debug("featurePost() | date={}, id={}", dateParam, id);

        LocalDate date = parseDate(dateParam);
        Optional<FeaturePost> selected = resolvePost(date, id);
        List<FeaturePost> week = rotateFeaturePostsUseCase.weekOf(date);
        List<FeaturePost> schedule = rotateFeaturePostsUseCase.schedule();

        model.addAttribute("pageTitle", "LinkedIn Feature Post");
        model.addAttribute("date", date);
        model.addAttribute("dateLabel", date.format(DATE_LABEL));
        model.addAttribute("previousDate", date.minusDays(1));
        model.addAttribute("nextDate", date.plusDays(1));
        model.addAttribute("weekPosts", week);
        model.addAttribute("schedule", schedule);
        model.addAttribute("cycleWeeks", cycleWeeks(schedule));

        if (selected.isPresent()) {
            FeaturePost post = selected.get();
            model.addAttribute("post", post);
            model.addAttribute("unresolvedTokens", post.unresolvedTokens());
            model.addAttribute("scheduledToday",
                    rotateFeaturePostsUseCase.postFor(date).isPresent());
        } else {
            model.addAttribute("post", null);
            model.addAttribute("unresolvedTokens", List.of());
            model.addAttribute("scheduledToday", false);
        }
        return "feature-post";
    }

    /**
     * Picks the post to display: an explicitly requested id wins, then the
     * post scheduled for the date, then the next one due.
     *
     * The final fallback is what makes the page useful on a Saturday — rather
     * than an empty screen, it shows Monday's post so a draft can be prepared.
     *
     * @param date the date in view
     * @param id   the optional requested post id
     * @return the post to display, or empty when the library is empty
     */
    private Optional<FeaturePost> resolvePost(LocalDate date, String id) {
        log.debug("resolvePost() | date={}, id={}", date, id);
        if (id != null && !id.isBlank()) {
            Optional<FeaturePost> byId = rotateFeaturePostsUseCase.findById(id);
            if (byId.isPresent()) {
                return byId;
            }
            log.warn("Requested feature post id '{}' not found in library", id);
        }
        Optional<FeaturePost> scheduled = rotateFeaturePostsUseCase.postFor(date);
        if (scheduled.isPresent()) {
            return scheduled;
        }
        return rotateFeaturePostsUseCase.nextPostFrom(date);
    }

    /**
     * Parses the date parameter, falling back to today on absence or garbage.
     *
     * @param dateParam the raw parameter
     * @return the resolved date
     */
    private LocalDate parseDate(String dateParam) {
        log.debug("parseDate() | dateParam={}", dateParam);
        if (dateParam == null || dateParam.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(dateParam.trim());
        } catch (DateTimeParseException e) {
            log.warn("Unparseable date parameter '{}' — falling back to today", dateParam);
            return LocalDate.now();
        }
    }

    /**
     * Derives the cycle length from the loaded schedule, for display.
     *
     * @param schedule the ordered schedule
     * @return the number of weeks in the cycle
     */
    private int cycleWeeks(List<FeaturePost> schedule) {
        log.debug("cycleWeeks() | scheduleSize={}", schedule.size());
        int highest = 0;
        for (FeaturePost post : schedule) {
            if (post.slot().week() > highest) {
                highest = post.slot().week();
            }
        }
        return highest;
    }
}
