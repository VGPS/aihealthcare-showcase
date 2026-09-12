package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.Contradiction;
import com.wgblackmon.aihealthcare.domain.model.SourceRef;
import com.wgblackmon.aihealthcare.domain.model.WikiPage;
import com.wgblackmon.aihealthcare.domain.model.WikiPageType;
import com.wgblackmon.aihealthcare.domain.port.outbound.WikiQueryPort;
import com.wgblackmon.aihealthcare.domain.service.PipeDelimitedUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed implementation of {@link WikiQueryPort}.
 *
 * <p>Maps wiki JPA entities to domain records for consumption by the
 * newsletter service and other domain consumers.  Phase 1 uses simple
 * LIKE queries for relevance; future phases may upgrade to pgvector
 * semantic similarity.
 *
 * <p>Source references are loaded from the {@code wiki_source_refs} table
 * and mapped to {@link SourceRef} domain records.  Contradictions are
 * loaded from {@code wiki_contradictions} with pipe-delimited article IDs
 * expanded into {@link SourceRef} lists (with minimal metadata since the
 * full article is not joined).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-09-12
 */
@Slf4j
@Component
public class WikiQueryAdapter implements WikiQueryPort {

    private final WikiPageRepository pageRepository;
    private final WikiSourceRefRepository sourceRefRepository;
    private final WikiContradictionRepository contradictionRepository;

    public WikiQueryAdapter(WikiPageRepository pageRepository,
                            WikiSourceRefRepository sourceRefRepository,
                            WikiContradictionRepository contradictionRepository) {
        log.debug("WikiQueryAdapter() | pageRepository={}, sourceRefRepository={}, contradictionRepository={}",
                pageRepository.getClass().getSimpleName(),
                sourceRefRepository.getClass().getSimpleName(),
                contradictionRepository.getClass().getSimpleName());
        this.pageRepository = pageRepository;
        this.sourceRefRepository = sourceRefRepository;
        this.contradictionRepository = contradictionRepository;
    }

    @Override
    public List<WikiPage> findRelevantPages(String query, int maxResults) {
        log.debug("findRelevantPages() | query={}, maxResults={}", query, maxResults);

        List<WikiPageEntity> entities = pageRepository.searchByKeyword(query);
        List<WikiPage> results = new ArrayList<>();
        int count = 0;
        for (WikiPageEntity entity : entities) {
            if (count >= maxResults) {
                break;
            }
            results.add(toDomain(entity));
            count++;
        }

        log.debug("findRelevantPages() | return={} pages", results.size());
        return results;
    }

    @Override
    public WikiPage getPage(String slug) {
        log.debug("getPage() | slug={}", slug);

        Optional<WikiPageEntity> found = pageRepository.findById(slug);
        if (found.isEmpty()) {
            log.debug("getPage() | return=null");
            return null;
        }

        WikiPage result = toDomain(found.get());
        log.debug("getPage() | return={}", result.slug());
        return result;
    }

    @Override
    public List<Contradiction> recentContradictions(Instant since) {
        log.debug("recentContradictions() | since={}", since);

        List<WikiContradictionEntity> entities =
                contradictionRepository.findByDetectedAtAfterOrderByDetectedAtDesc(since);
        List<Contradiction> results = new ArrayList<>();
        for (WikiContradictionEntity entity : entities) {
            results.add(toContradictionDomain(entity));
        }

        log.debug("recentContradictions() | return={} contradictions", results.size());
        return results;
    }

    private WikiPage toDomain(WikiPageEntity entity) {
        log.debug("toDomain() | slug={}", entity.getSlug());

        List<String> tags = PipeDelimitedUtils.split(entity.getTags());
        List<String> relatedSlugs = PipeDelimitedUtils.split(entity.getRelatedSlugs());
        List<SourceRef> sources = loadSourceRefs(entity.getSlug());

        WikiPage result = new WikiPage(
                entity.getSlug(),
                entity.getTitle(),
                WikiPageType.valueOf(entity.getPageType()),
                tags,
                entity.getContentMarkdown(),
                sources,
                relatedSlugs,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getRevision()
        );

        log.debug("toDomain() | return={}", result.slug());
        return result;
    }

    private List<SourceRef> loadSourceRefs(String pageSlug) {
        log.debug("loadSourceRefs() | pageSlug={}", pageSlug);

        List<WikiSourceRefEntity> refEntities = sourceRefRepository.findByPageSlug(pageSlug);
        List<SourceRef> refs = new ArrayList<>();
        for (WikiSourceRefEntity refEntity : refEntities) {
            refs.add(new SourceRef(
                    refEntity.getArticleId(),
                    refEntity.getSourceName(),
                    refEntity.getHarvestedOn(),
                    refEntity.getExcerpt()
            ));
        }

        log.debug("loadSourceRefs() | return={} refs", refs.size());
        return refs;
    }

    private Contradiction toContradictionDomain(WikiContradictionEntity entity) {
        log.debug("toContradictionDomain() | pageSlug={}", entity.getPageSlug());

        List<SourceRef> priorSources = expandArticleIds(entity.getPriorSourceIds());
        List<SourceRef> newSources = expandArticleIds(entity.getNewSourceIds());

        Contradiction result = new Contradiction(
                entity.getPageSlug(),
                entity.getPriorClaim(),
                entity.getNewClaim(),
                priorSources,
                newSources,
                entity.getDetectedAt()
        );

        log.debug("toContradictionDomain() | return={}", result.pageSlug());
        return result;
    }

    /**
     * Expands pipe-delimited article IDs into minimal {@link SourceRef} records.
     * Since contradictions store only article IDs (not full source metadata),
     * the source name and harvest date use placeholder values.
     */
    private List<SourceRef> expandArticleIds(String pipeDelimited) {
        List<SourceRef> refs = new ArrayList<>();
        if (pipeDelimited == null || pipeDelimited.isBlank()) {
            return refs;
        }
        String[] ids = pipeDelimited.split("\\|");
        for (String id : ids) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) {
                refs.add(new SourceRef(trimmed, "unknown", LocalDate.EPOCH, null));
            }
        }
        return refs;
    }

}
