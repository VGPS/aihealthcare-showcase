package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.IntelReport;
import com.wgblackmon.aihealthcare.domain.model.SourceCitation;
import com.wgblackmon.aihealthcare.domain.port.outbound.IntelReportPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed implementation of {@link IntelReportPort}.
 *
 * <p>Persists and retrieves {@link IntelReport} domain records via
 * {@link IntelReportRepository}. Entities never escape to the domain layer.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
@Slf4j
@Component
public class IntelReportAdapter implements IntelReportPort {

    private final IntelReportRepository repository;

    public IntelReportAdapter(IntelReportRepository repository) {
        log.debug("IntelReportAdapter() | repository={}", repository.getClass().getSimpleName());
        this.repository = repository;
    }

    @Override
    public void save(IntelReport report) {
        log.debug("save() | reportId={}", report.reportId());
        repository.save(toEntity(report));
        log.debug("save() | return=void");
    }

    @Override
    public Optional<IntelReport> findById(String reportId) {
        log.debug("findById() | reportId={}", reportId);
        Optional<IntelReport> result = repository.findById(reportId).map(this::toDomain);
        log.debug("findById() | return={}", result.isPresent() ? "found" : "empty");
        return result;
    }

    @Override
    public List<IntelReport> findAll() {
        log.debug("findAll() | (no args)");
        List<IntelReportEntity> entities = repository.findAllByOrderByGeneratedAtDesc();
        List<IntelReport> result = new ArrayList<>();
        for (IntelReportEntity entity : entities) {
            result.add(toDomain(entity));
        }
        log.debug("findAll() | return={} reports", result.size());
        return result;
    }

    private IntelReportEntity toEntity(IntelReport report) {
        log.debug("toEntity() | reportId={}", report.reportId());
        IntelReportEntity entity = new IntelReportEntity();
        entity.setReportId(report.reportId());
        entity.setQuery(report.query());
        entity.setHtmlContent(report.htmlContent());
        entity.setSourceCount(report.sourceCount());
        entity.setUserEmail(report.userEmail());
        entity.setGeneratedAt(report.generatedAt());
        entity.setSourcesData(serializeSources(report.sources()));
        log.debug("toEntity() | return={}", entity.getReportId());
        return entity;
    }

    private IntelReport toDomain(IntelReportEntity entity) {
        log.debug("toDomain() | reportId={}", entity.getReportId());
        IntelReport result = new IntelReport(
                entity.getReportId(),
                entity.getQuery(),
                entity.getHtmlContent(),
                entity.getSourceCount(),
                entity.getUserEmail(),
                entity.getGeneratedAt(),
                deserializeSources(entity.getSourcesData()));
        log.debug("toDomain() | return={}", result.reportId());
        return result;
    }

    private String serializeSources(List<SourceCitation> sources) {
        if (sources == null || sources.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (SourceCitation s : sources) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(s.citationNumber()).append("|")
              .append(s.title() != null ? s.title() : "").append("|")
              .append(s.url() != null ? s.url() : "").append("|")
              .append(s.retrievedAt() != null ? s.retrievedAt().toString() : "");
        }
        return sb.toString();
    }

    private List<SourceCitation> deserializeSources(String data) {
        if (data == null || data.isBlank()) {
            return List.of();
        }
        List<SourceCitation> result = new ArrayList<>();
        for (String line : data.split("\n")) {
            String[] parts = line.split("\\|", -1);
            if (parts.length >= 3) {
                int num = 0;
                try { num = Integer.parseInt(parts[0]); } catch (NumberFormatException ignored) {}
                String title = parts[1];
                String url = parts[2];
                Instant retrievedAt = null;
                if (parts.length >= 4 && !parts[3].isBlank()) {
                    try { retrievedAt = Instant.parse(parts[3]); } catch (Exception ignored) {}
                }
                result.add(new SourceCitation(num, title, url, retrievedAt));
            }
        }
        return result;
    }
}
