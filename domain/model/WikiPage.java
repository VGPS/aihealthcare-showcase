package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Immutable domain record representing a single page in the LLM-maintained
 * knowledge wiki.
 *
 * <p>Wiki pages are the durable, compounding asset of the system.  While
 * the newsletter is the weekly delivery vehicle, the accumulated, verified,
 * structured knowledge base is the long-term value.  Each page is compiled
 * from harvested {@link NewsArticle} records by the
 * {@link com.wgblackmon.aihealthcare.domain.port.outbound.KnowledgeCompilationPort}
 * and is fully owned by the wiki layer — source articles are never mutated.
 *
 * <p>The {@code slug} field is the unique identifier for this page, formatted
 * in kebab-case (e.g. {@code "fda-ai-device-guidance"}).  Cross-references
 * between pages use slugs via the {@code relatedSlugs} list.
 *
 * <p>The {@code sources} list provides first-class provenance: every factual
 * claim in {@code contentMarkdown} should trace to at least one {@link SourceRef}.
 *
 * @param slug            kebab-case unique identifier for this wiki page
 * @param title           human-readable page title
 * @param pageType        structural classification of this page
 * @param tags            free-form tags for filtering and grouping
 * @param contentMarkdown full page content in Markdown format
 * @param sources         provenance references linking claims to source articles
 * @param relatedSlugs    slugs of cross-referenced wiki pages
 * @param createdAt       timestamp when this page was first created
 * @param updatedAt       timestamp of the most recent revision; {@code null}
 *                        if the page has never been updated after creation
 * @param revision        monotonically increasing revision number; starts at 1
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
public record WikiPage(
        String slug,
        String title,
        WikiPageType pageType,
        List<String> tags,
        String contentMarkdown,
        List<SourceRef> sources,
        List<String> relatedSlugs,
        Instant createdAt,
        Instant updatedAt,
        int revision
) {

    private static final String SLUG_PATTERN = "^[a-z0-9]+(-[a-z0-9]+)*$";

    /**
     * Compact canonical constructor — validates required fields and
     * creates defensive copies of all list fields.
     */
    public WikiPage {
        if (slug == null || !slug.matches(SLUG_PATTERN)) {
            throw new IllegalArgumentException(
                    "slug must be non-null kebab-case (e.g. 'fda-ai-guidance'), got: " + slug);
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (pageType == null) {
            throw new IllegalArgumentException("pageType must not be null");
        }
        if (tags == null) {
            throw new IllegalArgumentException("tags must not be null");
        }
        if (sources == null) {
            throw new IllegalArgumentException("sources must not be null");
        }
        if (relatedSlugs == null) {
            throw new IllegalArgumentException("relatedSlugs must not be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt must not be null");
        }
        if (revision < 1) {
            throw new IllegalArgumentException("revision must be >= 1, got: " + revision);
        }
        tags = List.copyOf(tags);
        sources = List.copyOf(sources);
        relatedSlugs = List.copyOf(relatedSlugs);
    }
}
