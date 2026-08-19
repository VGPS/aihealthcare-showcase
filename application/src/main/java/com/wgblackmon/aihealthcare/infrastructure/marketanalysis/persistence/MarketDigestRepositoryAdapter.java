package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import com.wgblackmon.aihealthcare.domain.marketanalysis.AffectedCompany;
import com.wgblackmon.aihealthcare.domain.marketanalysis.FactClassification;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ImpactAssessment;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ImpactDimension;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ImpactDirection;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigest;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestEntry;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketImpactRank;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketNewsItem;
import com.wgblackmon.aihealthcare.domain.marketanalysis.NewsCategory;
import com.wgblackmon.aihealthcare.domain.marketanalysis.PeerGroup;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDigestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA-backed adapter implementing {@link MarketDigestRepository}.
 *
 * <p>Persistence uses four tables in a parent-child hierarchy
 * (market_digest → market_digest_entry → market_digest_impact_assessment /
 * market_digest_affected_company). All relationships are managed explicitly
 * with FK columns — no JPA cascade or {@code @OneToMany} is used.
 *
 * <p><b>Upsert strategy:</b> saving a digest for a date that already exists
 * deletes all existing child rows then the parent row before inserting fresh.
 * This simplifies retry logic and guarantees the stored version matches
 * the most recent call.
 *
 * <p>Source URLs are stored as pipe-delimited TEXT; enum values are stored
 * as their {@code .name()} strings. All IDs are application-generated UUIDs.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Slf4j
@Component
public class MarketDigestRepositoryAdapter implements MarketDigestRepository {

    private static final String PIPE = "|";

    private final MarketDigestJpaRepository digestRepo;
    private final MarketDigestEntryJpaRepository entryRepo;
    private final MarketDigestImpactAssessmentJpaRepository assessmentRepo;
    private final MarketDigestAffectedCompanyJpaRepository companyRepo;

    public MarketDigestRepositoryAdapter(
            MarketDigestJpaRepository digestRepo,
            MarketDigestEntryJpaRepository entryRepo,
            MarketDigestImpactAssessmentJpaRepository assessmentRepo,
            MarketDigestAffectedCompanyJpaRepository companyRepo) {
        this.digestRepo = digestRepo;
        this.entryRepo = entryRepo;
        this.assessmentRepo = assessmentRepo;
        this.companyRepo = companyRepo;
    }

    @Override
    @Transactional
    public void save(MarketDigest digest) {
        log.debug("save() | date={}, entries={}", digest.date(), digest.entries().size());

        deleteExistingByDate(digest.date());

        String digestId = UUID.randomUUID().toString();
        digestRepo.save(new MarketDigestEntity(digestId, digest.date(), digest.generatedAt()));

        for (MarketDigestEntry entry : digest.entries()) {
            String entryId = UUID.randomUUID().toString();
            entryRepo.save(toEntryEntity(entryId, digestId, entry));

            for (ImpactAssessment assessment : entry.impactAssessments()) {
                String assessmentId = UUID.randomUUID().toString();
                assessmentRepo.save(new MarketDigestImpactAssessmentEntity(
                        assessmentId,
                        entryId,
                        assessment.dimension().name(),
                        assessment.direction().name(),
                        assessment.rationale()
                ));
            }

            for (AffectedCompany company : entry.affectedCompanies()) {
                String companyId = UUID.randomUUID().toString();
                companyRepo.save(new MarketDigestAffectedCompanyEntity(
                        companyId,
                        entryId,
                        company.name(),
                        company.tickerSymbol(),
                        company.role(),
                        company.peerGroup() != null ? company.peerGroup().name() : null,
                        null, null, null
                ));
            }
        }

        log.debug("save() | return=void");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MarketDigest> findByDate(LocalDate date) {
        log.debug("findByDate() | date={}", date);

        Optional<MarketDigestEntity> digestEntityOpt = digestRepo.findByDigestDate(date);
        if (digestEntityOpt.isEmpty()) {
            log.debug("findByDate() | return=empty");
            return Optional.empty();
        }

        MarketDigest result = hydrateDigest(digestEntityOpt.get());
        log.debug("findByDate() | return={}", result);
        return Optional.of(result);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MarketDigest> findLatest() {
        log.debug("findLatest()");

        Optional<MarketDigestEntity> digestEntityOpt = digestRepo.findTopByOrderByDigestDateDesc();
        if (digestEntityOpt.isEmpty()) {
            log.debug("findLatest() | return=empty");
            return Optional.empty();
        }

        MarketDigest result = hydrateDigest(digestEntityOpt.get());
        log.debug("findLatest() | return={}", result);
        return Optional.of(result);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MarketDigest> findAll() {
        log.debug("findAll()");

        List<MarketDigestEntity> entities = digestRepo.findAllByOrderByDigestDateDesc();
        List<MarketDigest> results = new ArrayList<>();
        for (MarketDigestEntity entity : entities) {
            results.add(hydrateDigest(entity));
        }

        log.debug("findAll() | return.size={}", results.size());
        return results;
    }

    // ─── private helpers ────────────────────────────────────────────────────

    private MarketDigest hydrateDigest(MarketDigestEntity digestEntity) {
        List<MarketDigestEntryEntity> entryEntities = entryRepo.findByDigestId(digestEntity.getDigestId());
        List<MarketDigestEntry> entries = new ArrayList<>();
        for (MarketDigestEntryEntity entryEntity : entryEntities) {
            List<ImpactAssessment> assessments = loadAssessments(entryEntity.getEntryId());
            List<AffectedCompany> companies = loadCompanies(entryEntity.getEntryId());
            entries.add(toDomainEntry(entryEntity, assessments, companies));
        }
        return new MarketDigest(digestEntity.getDigestDate(), entries, digestEntity.getGeneratedAt());
    }

    private void deleteExistingByDate(LocalDate date) {
        Optional<MarketDigestEntity> existing = digestRepo.findByDigestDate(date);
        if (existing.isEmpty()) {
            return;
        }
        String existingDigestId = existing.get().getDigestId();
        List<MarketDigestEntryEntity> existingEntries = entryRepo.findByDigestId(existingDigestId);
        for (MarketDigestEntryEntity entry : existingEntries) {
            assessmentRepo.deleteByEntryId(entry.getEntryId());
            companyRepo.deleteByEntryId(entry.getEntryId());
        }
        entryRepo.deleteByDigestId(existingDigestId);
        digestRepo.deleteByDigestDate(date);
    }

    private MarketDigestEntryEntity toEntryEntity(String entryId, String digestId, MarketDigestEntry entry) {
        String sourceUrls = encodeSourceUrls(entry.newsItem().sourceUrls());
        return new MarketDigestEntryEntity(
                entryId,
                digestId,
                entry.newsItem().headline(),
                entry.newsItem().summary(),
                sourceUrls,
                entry.newsItem().category().name(),
                entry.factClassification().name(),
                entry.rank().value(),
                entry.newsItem().publishedAt(),
                entry.newsItem().dealSizeUsd()
        );
    }

    private String encodeSourceUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < urls.size(); i++) {
            if (i > 0) {
                sb.append(PIPE);
            }
            sb.append(urls.get(i));
        }
        return sb.toString();
    }

    private List<String> decodeSourceUrls(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        String[] parts = raw.split("\\|");
        for (String part : parts) {
            if (!part.isBlank()) {
                result.add(part);
            }
        }
        return result;
    }

    private List<ImpactAssessment> loadAssessments(String entryId) {
        List<MarketDigestImpactAssessmentEntity> entities = assessmentRepo.findByEntryId(entryId);
        List<ImpactAssessment> results = new ArrayList<>();
        for (MarketDigestImpactAssessmentEntity entity : entities) {
            results.add(new ImpactAssessment(
                    ImpactDimension.valueOf(entity.getDimension()),
                    ImpactDirection.valueOf(entity.getDirection()),
                    entity.getRationale()
            ));
        }
        return results;
    }

    private List<AffectedCompany> loadCompanies(String entryId) {
        List<MarketDigestAffectedCompanyEntity> entities = companyRepo.findByEntryId(entryId);
        List<AffectedCompany> results = new ArrayList<>();
        for (MarketDigestAffectedCompanyEntity entity : entities) {
            PeerGroup peerGroup = entity.getPeerGroup() != null
                    ? PeerGroup.valueOf(entity.getPeerGroup())
                    : null;
            results.add(new AffectedCompany(
                    entity.getCompanyName(),
                    entity.getTickerSymbol(),
                    entity.getRole(),
                    peerGroup
            ));
        }
        return results;
    }

    private MarketDigestEntry toDomainEntry(MarketDigestEntryEntity entity,
                                            List<ImpactAssessment> assessments,
                                            List<AffectedCompany> companies) {
        List<String> sourceUrls = decodeSourceUrls(entity.getSourceUrls());

        MarketNewsItem newsItem = new MarketNewsItem(
                entity.getHeadline(),
                entity.getSummary(),
                sourceUrls,
                entity.getPublishedAt(),
                NewsCategory.valueOf(entity.getCategory()),
                entity.getDealSizeUsd()
        );

        return new MarketDigestEntry(
                newsItem,
                assessments,
                FactClassification.valueOf(entity.getFactClassification()),
                new MarketImpactRank(entity.getMarketImpactRank()),
                companies
        );
    }
}
