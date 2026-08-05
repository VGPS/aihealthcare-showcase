package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.CompanyRelationship;
import com.wgblackmon.aihealthcare.domain.model.CompanyRelationshipType;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyRelationshipPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CompanyRelationshipService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@ExtendWith(MockitoExtension.class)
class CompanyRelationshipServiceTest {

    @Mock
    private ArticleIngestionPort articleIngestionPort;

    @Mock
    private CompanyRelationshipPort relationshipPort;

    private CompanyRelationshipService service;

    @BeforeEach
    void setUp() {
        service = new CompanyRelationshipService(articleIngestionPort, relationshipPort);
    }

    private NewsArticle article(String id, String title, String body) {
        return new NewsArticle(id, title, URI.create("https://example.com/" + id),
                body, "Test Topic", null, null, "Source", "INDUSTRY", 0.5, Instant.now());
    }

    @Test
    void detectRelationships_partnershipArticle_extractsPartnership() {
        NewsArticle partnerArticle = article("a1",
                "Google Health partners with Mayo Clinic on AI diagnostics",
                "Google Health and Mayo Clinic announced a new partnership.");
        when(articleIngestionPort.fetchRecentArticles(30)).thenReturn(List.of(partnerArticle));
        when(relationshipPort.existsBySourceAndTargetAndType(anyString(), anyString(), anyString()))
                .thenReturn(false);

        List<CompanyRelationship> result = service.detectRelationships();

        assertThat(result).isNotEmpty();
        CompanyRelationship rel = result.get(0);
        assertThat(rel.relationshipType()).isEqualTo(CompanyRelationshipType.PARTNERSHIP);
    }

    @Test
    void detectRelationships_acquisitionArticle_extractsAcquisition() {
        NewsArticle acqArticle = article("a2",
                "Oracle acquires Cerner in healthcare AI deal",
                "Oracle has completed its acquisition of Cerner.");
        when(articleIngestionPort.fetchRecentArticles(30)).thenReturn(List.of(acqArticle));
        when(relationshipPort.existsBySourceAndTargetAndType(anyString(), anyString(), anyString()))
                .thenReturn(false);

        List<CompanyRelationship> result = service.detectRelationships();

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).relationshipType()).isEqualTo(CompanyRelationshipType.ACQUISITION);
    }

    @Test
    void detectRelationships_noPatterns_noResults() {
        NewsArticle boringArticle = article("a3",
                "Weekly healthcare news roundup",
                "This week in healthcare we review several developments.");
        when(articleIngestionPort.fetchRecentArticles(30)).thenReturn(List.of(boringArticle));

        List<CompanyRelationship> result = service.detectRelationships();

        assertThat(result).isEmpty();
        verify(relationshipPort, never()).saveAll(any());
    }

    @Test
    void detectRelationships_duplicateRelationship_skips() {
        NewsArticle partnerArticle = article("a4",
                "Tempus partners with Roche on precision medicine",
                "Tempus and Roche signed a partnership agreement.");
        when(articleIngestionPort.fetchRecentArticles(30)).thenReturn(List.of(partnerArticle));
        when(relationshipPort.existsBySourceAndTargetAndType(anyString(), anyString(), anyString()))
                .thenReturn(true);

        List<CompanyRelationship> result = service.detectRelationships();

        assertThat(result).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void detectRelationships_savesNewRelationships() {
        NewsArticle integrationArticle = article("a5",
                "Epic integrates with Nuance AI platform",
                "Epic Systems announced integration with Nuance.");
        when(articleIngestionPort.fetchRecentArticles(30)).thenReturn(List.of(integrationArticle));
        when(relationshipPort.existsBySourceAndTargetAndType(anyString(), anyString(), anyString()))
                .thenReturn(false);

        service.detectRelationships();

        ArgumentCaptor<List<CompanyRelationship>> captor = ArgumentCaptor.forClass(List.class);
        verify(relationshipPort).saveAll(captor.capture());
        assertThat(captor.getValue()).isNotEmpty();
    }

    @Test
    void getAllRelationships_delegatesToPort() {
        CompanyRelationship rel = new CompanyRelationship("r1", "A", "B",
                CompanyRelationshipType.PARTNERSHIP, "a1", "Summary", 0.8, Instant.now());
        when(relationshipPort.findAll()).thenReturn(List.of(rel));

        List<CompanyRelationship> result = service.getAllRelationships();

        assertThat(result).hasSize(1);
        verify(relationshipPort).findAll();
    }

    @Test
    void getRelationshipsForCompany_delegatesToPort() {
        CompanyRelationship rel = new CompanyRelationship("r1", "Google", "DeepMind",
                CompanyRelationshipType.ACQUISITION, "a1", "Summary", 0.9, Instant.now());
        when(relationshipPort.findByCompany("Google")).thenReturn(List.of(rel));

        List<CompanyRelationship> result = service.getRelationshipsForCompany("Google");

        assertThat(result).hasSize(1);
        verify(relationshipPort).findByCompany("Google");
    }
}
