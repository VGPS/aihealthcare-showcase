package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.Contradiction;
import com.wgblackmon.aihealthcare.domain.model.SourceRef;
import com.wgblackmon.aihealthcare.domain.model.WikiPage;
import com.wgblackmon.aihealthcare.domain.model.WikiPageType;
import com.wgblackmon.aihealthcare.domain.port.outbound.WikiQueryPort;
import com.wgblackmon.aihealthcare.domain.service.PipeDelimitedUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * JPA-backed implementation of {@link WikiQueryPort}.
 *
 * <p>Maps wiki JPA entities to domain records for consumption by the
 * newsletter service and other domain consumers.  A blank query runs the
 * keyword/LIKE path only (preserved exactly, since {@code WikiLintScheduler},
 * {@code WikiLintController}, and {@code FeedHarvestScheduler} all call
 * {@code findRelevantPages("", N)} expecting "everything").  A non-blank
 * query merges that keyword leg with a pgvector semantic-similarity leg
 * (Slice W7) — keyword hits are guaranteed inclusion, with semantic hits
 * filling any remaining slots up to {@code maxResults}, deduped by slug.
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
 * @updated 2026-10-26
 */
@Slf4j
@Component
public class WikiQueryAdapter implements WikiQueryPort {

    private final WikiPageRepository pageRepository;
    private final WikiSourceRefRepository sourceRefRepository;
    private final WikiContradictionRepository contradictionRepository;
    private final VectorStore vectorStore;

    public WikiQueryAdapter(WikiPageRepository pageRepository,
                            WikiSourceRefRepository sourceRefRepository,
                            WikiContradictionRepository contradictionRepository,
                            ObjectProvider<VectorStore> vectorStoreProvider) {
        log.debug("WikiQueryAdapter() | pageRepository={}, sourceRefRepository={}, contradictionRepository={}",
                pageRepository.getClass().getSimpleName(),
                sourceRefRepository.getClass().getSimpleName(),
                contradictionRepository.getClass().getSimpleName());
        this.pageRepository = pageRepository;
        this.sourceRefRepository = sourceRefRepository;
        this.contradictionRepository = contradictionRepository;
        this.vectorStore = vectorStoreProvider.getIfAvailable();
    }

    @Override
    public List<WikiPage> findRelevantPages(String query, int maxResults) {
        log.debug("findRelevantPages() | query={}, maxResults={}", query, maxResults);

        if (query == null || query.isBlank()) {
            List<WikiPage> results = keywordMatches(query, maxResults);
            log.debug("findRelevantPages() | return={} pages (blank query, keyword only)", results.size());
            return results;
        }

        Set<String> seenSlugs = new LinkedHashSet<>();
        List<WikiPage> results = new ArrayList<>();
        for (WikiPage page : keywordMatches(query, maxResults)) {
            if (seenSlugs.add(page.slug())) {
                results.add(page);
            }
        }

        if (results.size() < maxResults) {
            for (WikiPage page : semanticMatches(query, maxResults)) {
                if (results.size() >= maxResults) {
                    break;
                }
                if (seenSlugs.add(page.slug())) {
                    results.add(page);
                }
            }
        }

        log.debug("findRelevantPages() | return={} pages ({} keyword+semantic merged)",
                results.size(), seenSlugs.size());
        return results;
    }

    /**
     * Runs the keyword/LIKE search and caps it at {@code maxResults}.
     * This is the sole retrieval path for a blank query and the first
     * (guaranteed-inclusion) leg of a non-blank query's hybrid search.
     */
    private List<WikiPage> keywordMatches(String query, int maxResults) {
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
        return results;
    }

    /**
     * Runs a vector-similarity search scoped to wiki-page embeddings
     * ({@code type=wiki_page} metadata) so paraphrased queries can surface
     * pages a substring match would miss. Returns an empty list if the
     * vector store is unavailable or the search fails — callers already
     * treat this leg as best-effort supplemental results.
     */
    private List<WikiPage> semanticMatches(String query, int maxResults) {
        if (vectorStore == null) {
            return List.of();
        }

        List<Document> docs;
        try {
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(maxResults)
                    .filterExpression("type == 'wiki_page'")
                    .build();
            docs = vectorStore.similaritySearch(request);
        } catch (Exception ex) {
            log.warn("semanticMatches() | vector search failed: {}", ex.getMessage());
            return List.of();
        }

        List<WikiPage> results = new ArrayList<>();
        for (Document doc : docs) {
            Object slugObj = doc.getMetadata().get("slug");
            if (slugObj == null) {
                log.warn("semanticMatches() | Document {} has no slug metadata, skipping", doc.getId());
                continue;
            }
            Optional<WikiPageEntity> entity = pageRepository.findById(slugObj.toString());
            if (entity.isEmpty()) {
                log.warn("semanticMatches() | slug={} from vector store not found in DB, skipping",
                        slugObj);
                continue;
            }
            results.add(toDomain(entity.get()));
        }
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
