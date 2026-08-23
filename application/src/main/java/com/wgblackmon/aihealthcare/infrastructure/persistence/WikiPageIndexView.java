package com.wgblackmon.aihealthcare.infrastructure.persistence;

import java.time.Instant;

/**
 * Spring Data JPA projection for {@link WikiPageEntity} index listing.
 *
 * <p>Selects only the 8 columns required by the wiki index page, explicitly
 * excluding {@code content_markdown} (avg 1.3 KB/page × 649 pages = 833 KB per
 * {@code findAll()} call).  At 50 concurrent users that column was transferring
 * ~40 MB/s from RDS for data that was immediately discarded.
 *
 * <p>The projection is used by the index variants in {@link WikiPageRepository}
 * ({@code findAllBy}, {@code findAllByPageType}, {@code searchByKeywordForIndex},
 * {@code searchByKeywordAndPageTypeForIndex}).  Full-entity methods are retained
 * for {@link WikiQueryAdapter}'s detail-page and compilation paths.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-23
 * @updated 2026-08-23
 */
public interface WikiPageIndexView {
    String getSlug();
    String getTitle();
    String getPageType();
    String getTags();
    String getRelatedSlugs();
    Instant getCreatedAt();
    Instant getUpdatedAt();
    int getRevision();
}
