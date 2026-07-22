package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.CompanyProfile;
import com.wgblackmon.aihealthcare.domain.model.TrendDirection;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyProfilePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed implementation of {@link CompanyProfilePort}.
 *
 * <p>Maps between {@link CompanyProfile} domain records and
 * {@link CompanyProfileEntity} JPA entities. Categories and article IDs
 * are stored as pipe-delimited strings.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
@Slf4j
@Component
public class CompanyProfileAdapter implements CompanyProfilePort {

    private final CompanyProfileRepository repository;

    public CompanyProfileAdapter(CompanyProfileRepository repository) {
        log.debug("CompanyProfileAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public void save(CompanyProfile profile) {
        log.debug("save() | slug={}, articleCount={}", profile.slug(), profile.articleCount());

        CompanyProfileEntity entity = toEntity(profile);
        repository.save(entity);

        log.debug("save() | return=void");
    }

    @Override
    public Optional<CompanyProfile> findBySlug(String slug) {
        log.debug("findBySlug() | slug={}", slug);

        Optional<CompanyProfile> result = repository.findById(slug).map(this::toDomain);

        log.debug("findBySlug() | return={}", result.isPresent() ? "present" : "empty");
        return result;
    }

    @Override
    public List<CompanyProfile> findAll() {
        log.debug("findAll()");

        List<CompanyProfileEntity> entities = repository.findAllByOrderByArticleCountDesc();
        List<CompanyProfile> result = new ArrayList<>();
        for (CompanyProfileEntity entity : entities) {
            result.add(toDomain(entity));
        }

        log.debug("findAll() | return={} profiles", result.size());
        return result;
    }

    @Override
    public List<CompanyProfile> findByCategory(String category) {
        log.debug("findByCategory() | category={}", category);

        List<CompanyProfileEntity> entities = repository.findByCategoriesPipeContaining(category);
        List<CompanyProfile> result = new ArrayList<>();
        for (CompanyProfileEntity entity : entities) {
            result.add(toDomain(entity));
        }

        log.debug("findByCategory() | return={} profiles", result.size());
        return result;
    }

    private CompanyProfileEntity toEntity(CompanyProfile profile) {
        CompanyProfileEntity entity = new CompanyProfileEntity();
        entity.setSlug(profile.slug());
        entity.setName(profile.name());
        entity.setUrl(profile.url());
        entity.setDescription(profile.description());
        entity.setCategoriesPipe(String.join("|", profile.categories()));
        entity.setArticleIdsPipe(String.join("|", profile.articleIds()));
        entity.setFirstDiscoveredAt(profile.firstDiscoveredAt());
        entity.setLastUpdatedAt(profile.lastUpdatedAt());
        entity.setArticleCount(profile.articleCount());
        entity.setTrendDirection(profile.trendDirection() != null
                ? profile.trendDirection().name() : TrendDirection.STABLE.name());
        return entity;
    }

    private CompanyProfile toDomain(CompanyProfileEntity entity) {
        List<String> categories = parsePipe(entity.getCategoriesPipe());
        List<String> articleIds = parsePipe(entity.getArticleIdsPipe());

        TrendDirection direction;
        try {
            direction = TrendDirection.valueOf(entity.getTrendDirection());
        } catch (Exception e) {
            direction = TrendDirection.STABLE;
        }

        return new CompanyProfile(
                entity.getSlug(),
                entity.getName(),
                entity.getUrl(),
                entity.getDescription(),
                categories,
                articleIds,
                entity.getFirstDiscoveredAt(),
                entity.getLastUpdatedAt(),
                entity.getArticleCount(),
                direction
        );
    }

    private List<String> parsePipe(String pipe) {
        if (pipe == null || pipe.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        String[] parts = pipe.split("\\|");
        for (String part : parts) {
            if (!part.isBlank()) {
                result.add(part.trim());
            }
        }
        return result;
    }
}
