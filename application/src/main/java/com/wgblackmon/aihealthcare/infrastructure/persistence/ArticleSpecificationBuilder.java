package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.ArticleSearchCriteria;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a JPA {@link Specification} from an {@link ArticleSearchCriteria}.
 *
 * <p>Each non-null, non-blank field in the criteria becomes an AND-combined
 * predicate. Text fields use case-insensitive {@code LIKE '%value%'};
 * date fields use {@code >=} / {@code <=} range bounds.
 *
 * <p>This is a static utility class — not a Spring component. Called directly
 * by {@link ArticleSearchQueryAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-06-01
 * @updated 2026-06-06
 */
@Slf4j
public final class ArticleSpecificationBuilder {

    private ArticleSpecificationBuilder() {
        // static utility — not instantiable
    }

    /**
     * Builds a {@link Specification} from the given criteria.
     *
     * <p>Only non-null (and for strings, non-blank) fields produce predicates.
     * All predicates are AND-combined. If no fields are set, a match-all
     * specification is returned.
     *
     * @param criteria the search criteria with optional filter fields
     * @return the combined specification
     */
    public static Specification<NewsArticleEntity> fromCriteria(ArticleSearchCriteria criteria) {
        log.debug("fromCriteria() | criteria={}", criteria);

        List<Specification<NewsArticleEntity>> specs = new ArrayList<>();

        if (criteria.title() != null && !criteria.title().isBlank()) {
            String pattern = "%" + criteria.title().toLowerCase() + "%";
            specs.add((root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern));
        }

        if (criteria.topic() != null && !criteria.topic().isBlank()) {
            String pattern = "%" + criteria.topic().toLowerCase() + "%";
            specs.add((root, query, cb) -> cb.like(cb.lower(root.get("topic")), pattern));
        }

        if (criteria.author() != null && !criteria.author().isBlank()) {
            String pattern = "%" + criteria.author().toLowerCase() + "%";
            specs.add((root, query, cb) -> cb.like(cb.lower(root.get("author")), pattern));
        }

        if (criteria.sourceName() != null && !criteria.sourceName().isBlank()) {
            String pattern = "%" + criteria.sourceName().toLowerCase() + "%";
            specs.add((root, query, cb) -> cb.like(cb.lower(root.get("sourceName")), pattern));
        }

        if (criteria.bodyText() != null && !criteria.bodyText().isBlank()) {
            String pattern = "%" + criteria.bodyText().toLowerCase() + "%";
            specs.add((root, query, cb) -> cb.like(cb.lower(root.get("bodyText")), pattern));
        }

        if (criteria.publishedFrom() != null) {
            specs.add((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("publishedAt"), criteria.publishedFrom()));
        }

        if (criteria.publishedTo() != null) {
            specs.add((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("publishedAt"), criteria.publishedTo()));
        }

        Specification<NewsArticleEntity> combined = Specification.where(null);
        for (Specification<NewsArticleEntity> spec : specs) {
            combined = combined.and(spec);
        }

        log.debug("fromCriteria() | return=Specification with {} predicates", specs.size());
        return combined;
    }
}
