package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.model.FeaturePost;
import com.wgblackmon.aihealthcare.domain.model.FeaturePostScreenshot;
import com.wgblackmon.aihealthcare.domain.model.FeaturePostSlot;
import com.wgblackmon.aihealthcare.domain.port.outbound.FeaturePostPort;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outbound adapter loading the LinkedIn feature-post library from a YAML file
 * on the classpath.
 *
 * The library is read once at startup and held in memory — it is editorial
 * content of a few dozen entries that changes when the author edits the file
 * and redeploys, so there is nothing to gain from re-reading it per request.
 * Adding a post is a YAML edit, not a code change, which is the whole reason
 * the content lives outside the source tree in the first place.
 *
 * Meta tokens are substituted during load: {{trial_url}} and its siblings are
 * resolved from the file's own {@code meta:} block so a changed URL is a
 * one-line edit rather than thirty. Author-supplied {{PLACEHOLDER}} tokens in
 * the live variants are deliberately left intact for the domain record to
 * report as outstanding.
 *
 * A malformed or missing library is logged and yields an empty list rather
 * than failing startup — a broken marketing content file should never take the
 * application down.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-05
 * @updated 2026-09-05
 */
@Slf4j
@Component
public class YamlFeaturePostAdapter implements FeaturePostPort {

    private final ResourceLoader resourceLoader;
    private final String libraryLocation;

    private List<FeaturePost> posts = Collections.emptyList();
    private int cycleWeeks = 0;

    public YamlFeaturePostAdapter(
            ResourceLoader resourceLoader,
            @Value("${aihealthcare.linkedin.feature-posts-location:"
                    + "classpath:linkedin/feature-posts.yml}") String libraryLocation) {
        log.debug("YamlFeaturePostAdapter() | libraryLocation={}", libraryLocation);
        this.resourceLoader = resourceLoader;
        this.libraryLocation = libraryLocation;
    }

    /**
     * Reads and parses the library at startup.
     */
    @PostConstruct
    public void load() {
        log.debug("load() | libraryLocation={}", libraryLocation);
        Resource resource = resourceLoader.getResource(libraryLocation);
        if (!resource.exists()) {
            log.warn("LinkedIn feature-post library not found at {} — "
                    + "the feature post page will be empty", libraryLocation);
            return;
        }
        try (InputStream input = resource.getInputStream()) {
            Yaml yaml = new Yaml();
            Object raw = yaml.load(input);
            if (!(raw instanceof Map)) {
                log.error("Feature-post library at {} did not parse to a map — ignoring",
                        libraryLocation);
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) raw;
            Map<String, String> meta = readMeta(root);
            this.posts = readPosts(root, meta);
            this.cycleWeeks = highestWeek(this.posts);
            log.info("Loaded {} LinkedIn feature posts across a {}-week rotation from {}",
                    this.posts.size(), this.cycleWeeks, libraryLocation);
        } catch (IOException e) {
            log.error("Failed to read feature-post library at {}: {}",
                    libraryLocation, e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("Failed to parse feature-post library at {}: {}",
                    libraryLocation, e.getMessage(), e);
        }
    }

    @Override
    public List<FeaturePost> findAll() {
        log.debug("findAll() | postCount={}", posts.size());
        return posts;
    }

    @Override
    public int cycleWeeks() {
        log.debug("cycleWeeks() | cycleWeeks={}", cycleWeeks);
        return cycleWeeks;
    }

    /**
     * Extracts the meta block as a token-to-value map.
     *
     * @param root the parsed YAML root
     * @return meta values keyed by token name; empty when absent
     */
    private Map<String, String> readMeta(Map<String, Object> root) {
        log.debug("readMeta()");
        Map<String, String> meta = new LinkedHashMap<>();
        Object rawMeta = root.get("meta");
        if (!(rawMeta instanceof Map)) {
            return meta;
        }
        @SuppressWarnings("unchecked")
        Map<Object, Object> metaMap = (Map<Object, Object>) rawMeta;
        for (Map.Entry<Object, Object> entry : metaMap.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                meta.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return meta;
    }

    /**
     * Converts the parsed post entries into domain records.
     *
     * Entries that fail validation are logged and skipped so one bad post does
     * not cost the rest of the library.
     *
     * @param root the parsed YAML root
     * @param meta the meta tokens to substitute
     * @return the parsed posts, in file order
     */
    private List<FeaturePost> readPosts(Map<String, Object> root, Map<String, String> meta) {
        log.debug("readPosts()");
        List<FeaturePost> parsed = new ArrayList<>();
        Object rawPosts = root.get("posts");
        if (!(rawPosts instanceof List)) {
            log.warn("Feature-post library has no 'posts' list");
            return parsed;
        }
        List<?> entries = (List<?>) rawPosts;
        for (Object entry : entries) {
            if (!(entry instanceof Map)) {
                log.warn("Skipping non-map entry in posts list");
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> postMap = (Map<String, Object>) entry;
            try {
                parsed.add(toFeaturePost(postMap, meta));
            } catch (RuntimeException e) {
                log.warn("Skipping malformed feature post '{}': {}",
                        postMap.get("id"), e.getMessage());
            }
        }
        return parsed;
    }

    /**
     * Maps one YAML entry to a {@link FeaturePost}.
     *
     * @param postMap the entry
     * @param meta    the meta tokens to substitute
     * @return the domain record
     */
    private FeaturePost toFeaturePost(Map<String, Object> postMap, Map<String, String> meta) {
        log.debug("toFeaturePost() | id={}", postMap.get("id"));
        return new FeaturePost(
                text(postMap, "id", meta),
                text(postMap, "feature", meta),
                text(postMap, "menu_path", meta),
                text(postMap, "url", meta),
                toSlot(postMap.get("slot")),
                text(postMap, "theme", meta),
                text(postMap, "hook", meta),
                text(postMap, "body", meta),
                text(postMap, "live_variant", meta),
                text(postMap, "first_comment", meta),
                toHashtags(postMap.get("hashtags")),
                toScreenshot(postMap.get("screenshot"), meta));
    }

    /**
     * Maps the slot sub-map to a {@link FeaturePostSlot}.
     *
     * @param rawSlot the slot node
     * @return the slot
     */
    private FeaturePostSlot toSlot(Object rawSlot) {
        log.debug("toSlot()");
        if (!(rawSlot instanceof Map)) {
            throw new IllegalArgumentException("post has no slot block");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> slotMap = (Map<String, Object>) rawSlot;
        Object week = slotMap.get("week");
        Object weekday = slotMap.get("weekday");
        if (week == null || weekday == null) {
            throw new IllegalArgumentException("slot requires both week and weekday");
        }
        return new FeaturePostSlot(
                Integer.parseInt(String.valueOf(week).trim()),
                DayOfWeek.valueOf(String.valueOf(weekday).trim().toUpperCase()));
    }

    /**
     * Maps the hashtag list, tolerating a missing or malformed node.
     *
     * @param rawHashtags the hashtags node
     * @return the hashtags, never null
     */
    private List<String> toHashtags(Object rawHashtags) {
        log.debug("toHashtags()");
        List<String> hashtags = new ArrayList<>();
        if (!(rawHashtags instanceof List)) {
            return hashtags;
        }
        List<?> values = (List<?>) rawHashtags;
        for (Object value : values) {
            if (value != null) {
                hashtags.add(String.valueOf(value).trim());
            }
        }
        return hashtags;
    }

    /**
     * Maps the screenshot sub-map to a {@link FeaturePostScreenshot}.
     *
     * @param rawScreenshot the screenshot node
     * @param meta          the meta tokens to substitute
     * @return the screenshot spec, or one with null fields when absent
     */
    private FeaturePostScreenshot toScreenshot(Object rawScreenshot, Map<String, String> meta) {
        log.debug("toScreenshot()");
        if (!(rawScreenshot instanceof Map)) {
            return new FeaturePostScreenshot(null, null, null, null, null);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> shot = (Map<String, Object>) rawScreenshot;
        return new FeaturePostScreenshot(
                text(shot, "page", meta),
                text(shot, "prompt", meta),
                text(shot, "capture", meta),
                text(shot, "redact", meta),
                text(shot, "alt_text", meta));
    }

    /**
     * Reads a string field and substitutes meta tokens in it.
     *
     * @param source the map to read from
     * @param key    the field name
     * @param meta   the meta tokens
     * @return the resolved text, or null when the field is absent
     */
    private String text(Map<String, Object> source, String key, Map<String, String> meta) {
        Object value = source.get(key);
        if (value == null) {
            return null;
        }
        return substitute(String.valueOf(value).trim(), meta);
    }

    /**
     * Replaces {{token}} occurrences with their meta values.
     *
     * Tokens with no matching meta entry are left exactly as written — those
     * are the author-supplied placeholders the domain record reports as
     * outstanding, and silently blanking them would hide the very thing the
     * check exists to catch.
     *
     * @param value the raw text
     * @param meta  the meta tokens
     * @return the text with known tokens resolved
     */
    private String substitute(String value, Map<String, String> meta) {
        if (value.indexOf("{{") < 0) {
            return value;
        }
        String resolved = value;
        for (Map.Entry<String, String> entry : meta.entrySet()) {
            resolved = resolved.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return resolved;
    }

    /**
     * Finds the highest week number present, which defines the cycle length.
     *
     * @param parsed the parsed posts
     * @return the highest week number, or 0 when there are none
     */
    private int highestWeek(List<FeaturePost> parsed) {
        log.debug("highestWeek() | postCount={}", parsed.size());
        int highest = 0;
        for (FeaturePost post : parsed) {
            if (post.slot().week() > highest) {
                highest = post.slot().week();
            }
        }
        return highest;
    }
}
