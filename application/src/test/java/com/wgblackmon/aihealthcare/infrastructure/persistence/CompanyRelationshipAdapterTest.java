package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.CompanyRelationship;
import com.wgblackmon.aihealthcare.domain.model.CompanyRelationshipType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link CompanyRelationshipAdapter} using H2 in-memory DB.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@DataJpaTest
class CompanyRelationshipAdapterTest {

    @Autowired
    private CompanyRelationshipRepository repository;

    private CompanyRelationshipAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new CompanyRelationshipAdapter(repository);
    }

    private CompanyRelationship relationship(String id, String source, String target,
                                              CompanyRelationshipType type) {
        return new CompanyRelationship(id, source, target, type,
                "article-1", source + " and " + target, 0.8, Instant.now());
    }

    @Test
    void saveAll_persistsRelationships() {
        adapter.saveAll(List.of(
                relationship("r1", "Google", "DeepMind", CompanyRelationshipType.ACQUISITION),
                relationship("r2", "Tempus", "Roche", CompanyRelationshipType.PARTNERSHIP)
        ));
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void findAll_returnsOrderedByDetectedAtDesc() {
        Instant earlier = Instant.parse("2026-01-01T00:00:00Z");
        Instant later = Instant.parse("2026-06-01T00:00:00Z");

        adapter.saveAll(List.of(
                new CompanyRelationship("r1", "A", "B", CompanyRelationshipType.PARTNERSHIP,
                        "a1", "Old", 0.5, earlier),
                new CompanyRelationship("r2", "C", "D", CompanyRelationshipType.INVESTMENT,
                        "a2", "New", 0.8, later)
        ));

        List<CompanyRelationship> result = adapter.findAll();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).relationshipId()).isEqualTo("r2");
    }

    @Test
    void findByCompany_findsAsSourceOrTarget() {
        adapter.saveAll(List.of(
                relationship("r1", "Google", "DeepMind", CompanyRelationshipType.ACQUISITION),
                relationship("r2", "Mayo Clinic", "Google", CompanyRelationshipType.PARTNERSHIP),
                relationship("r3", "Epic", "Nuance", CompanyRelationshipType.INTEGRATION)
        ));

        List<CompanyRelationship> result = adapter.findByCompany("Google");
        assertThat(result).hasSize(2);
    }

    @Test
    void existsBySourceAndTargetAndType_trueWhenExists() {
        adapter.saveAll(List.of(
                relationship("r1", "Google", "DeepMind", CompanyRelationshipType.ACQUISITION)
        ));
        assertThat(adapter.existsBySourceAndTargetAndType("Google", "DeepMind", "ACQUISITION"))
                .isTrue();
    }

    @Test
    void existsBySourceAndTargetAndType_falseWhenMissing() {
        assertThat(adapter.existsBySourceAndTargetAndType("X", "Y", "PARTNERSHIP"))
                .isFalse();
    }
}
