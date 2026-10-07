package com.wgblackmon.aihealthcare.infrastructure.persistence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring service that collapses duplicate wiki pages caused by LLM slug drift.
 *
 * <p>Each compilation run instructs Claude to reuse existing slugs, but the model
 * sometimes appends {@code -2026}, {@code -update}, or similar suffixes to titles
 * that already have a canonical slug.  Because {@link com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageEntity}
 * uses the slug as its primary key, every new slug creates a separate row in the
 * {@code wiki_pages} table.
 *
 * <p>This service groups all pages by a normalised title (lowercase, strip
 * non-alphanumeric, collapse whitespace), picks the canonical page within each
 * duplicate group (highest revision number; ties broken by shortest slug), then
 * deletes the child records and the non-canonical page rows for the duplicates.
 *
 * <p>This service is intentionally NOT public-facing and should only be called
 * from the {@code POST /monitoring/wiki/deduplicate} admin endpoint or tests.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-07
 * @updated 2026-10-07
 */
@Slf4j
@Service
public class WikiDeduplicationService {

    private final WikiPageRepository pageRepository;
    private final WikiSourceRefRepository sourceRefRepository;
    private final WikiContradictionRepository contradictionRepository;
    private final WikiPageRevisionRepository revisionRepository;

    public WikiDeduplicationService(WikiPageRepository pageRepository,
                                     WikiSourceRefRepository sourceRefRepository,
                                     WikiContradictionRepository contradictionRepository,
                                     WikiPageRevisionRepository revisionRepository) {
        log.debug("WikiDeduplicationService() | constructing");
        this.pageRepository = pageRepository;
        this.sourceRefRepository = sourceRefRepository;
        this.contradictionRepository = contradictionRepository;
        this.revisionRepository = revisionRepository;
        log.debug("WikiDeduplicationService() | return=void");
    }

    /**
     * Finds all duplicate wiki pages (same normalised title, different slugs),
     * preserves the canonical page in each group, and deletes the rest along
     * with their child records (source refs, contradictions, revisions).
     *
     * @return summary of what was cleaned up
     */
    @Transactional
    public DeduplicationResult deduplicate() {
        log.debug("deduplicate() | (no args)");

        List<WikiPageEntity> allPages = pageRepository.findAll();
        log.debug("deduplicate() | totalPages={}", allPages.size());

        // Group pages by normalised title
        Map<String, List<WikiPageEntity>> groupedByTitle = new LinkedHashMap<>();
        for (WikiPageEntity page : allPages) {
            String nt = normalizeTitle(page.getTitle());
            groupedByTitle.computeIfAbsent(nt, k -> new ArrayList<>()).add(page);
        }

        int groupsDeduplicated = 0;
        int pagesDeleted = 0;

        for (Map.Entry<String, List<WikiPageEntity>> entry : groupedByTitle.entrySet()) {
            List<WikiPageEntity> group = entry.getValue();
            if (group.size() <= 1) continue;

            // Sort: highest revision first; ties → shortest slug
            group.sort((a, b) -> {
                int revCmp = Integer.compare(b.getRevision(), a.getRevision());
                if (revCmp != 0) return revCmp;
                return Integer.compare(a.getSlug().length(), b.getSlug().length());
            });

            WikiPageEntity canonical = group.get(0);
            groupsDeduplicated++;

            for (int i = 1; i < group.size(); i++) {
                WikiPageEntity duplicate = group.get(i);
                log.info("deduplicate() | deleting duplicate slug='{}' (canonical='{}', title='{}')",
                        duplicate.getSlug(), canonical.getSlug(), canonical.getTitle());

                sourceRefRepository.deleteByPageSlug(duplicate.getSlug());
                contradictionRepository.deleteByPageSlug(duplicate.getSlug());
                revisionRepository.deleteByPageSlug(duplicate.getSlug());
                pageRepository.delete(duplicate);
                pagesDeleted++;
            }
        }

        int remainingPages = allPages.size() - pagesDeleted;
        DeduplicationResult result = new DeduplicationResult(groupsDeduplicated, pagesDeleted, remainingPages);
        log.info("deduplicate() | groupsDeduplicated={}, pagesDeleted={}, remainingPages={}",
                groupsDeduplicated, pagesDeleted, remainingPages);
        log.debug("deduplicate() | return={}", result);
        return result;
    }

    /**
     * Normalises a wiki page title for duplicate detection.
     * Lowercases, strips non-alphanumeric characters (replacing with spaces),
     * and collapses consecutive whitespace.
     *
     * @param title raw wiki page title; may be null
     * @return normalised title, never null
     */
    static String normalizeTitle(String title) {
        if (title == null) return "";
        return title.toLowerCase()
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Result summary returned by {@link #deduplicate()}.
     *
     * @param groupsDeduplicated number of title groups that had more than one page
     * @param pagesDeleted       total non-canonical pages removed
     * @param remainingPages     total pages remaining after cleanup
     */
    public record DeduplicationResult(int groupsDeduplicated, int pagesDeleted, int remainingPages) {}
}
