package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.CompanyRelationship;
import com.wgblackmon.aihealthcare.domain.model.CompanyRelationshipType;
import com.wgblackmon.aihealthcare.domain.model.HealthcareAiCompany;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.CompanyRelationshipPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.HealthcareAiCompanyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.RelationshipClassificationPort;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CompanyRelationshipService}.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-08-04
 * @updated 2026-10-07
 */
@ExtendWith(MockitoExtension.class)
class CompanyRelationshipServiceTest {

    @Mock
    private ArticleIngestionPort articleIngestionPort;

    @Mock
    private CompanyRelationshipPort relationshipPort;

    @Mock
    private RelationshipClassificationPort classificationPort;

    @Mock
    private HealthcareAiCompanyPort companyPort;

    private CompanyRelationshipService service;

    @BeforeEach
    void setUp() {
        service = new CompanyRelationshipService(articleIngestionPort, relationshipPort, null, companyPort);
    }

    private HealthcareAiCompany knownCompany(String name, String nameNormalized) {
        return new HealthcareAiCompany("c-" + nameNormalized, name, nameNormalized, null, null, null,
                null, null, null, null, null, null, null, null, false, null, Instant.now(), null);
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
        when(companyPort.findAll()).thenReturn(List.of());
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
        when(companyPort.findAll()).thenReturn(List.of());
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
    void detectRelationships_numericExtractedEntity_filteredOut() {
        NewsArticle numericArticle = article("a6",
                "Health IT Briefing",
                "In 2025 invests in Acme Health.");
        when(articleIngestionPort.fetchRecentArticles(30)).thenReturn(List.of(numericArticle));

        List<CompanyRelationship> result = service.detectRelationships();

        assertThat(result).isEmpty();
        verify(relationshipPort, never()).saveAll(any());
    }

    @Test
    void detectRelationships_genericDenylistedEntity_filteredOut() {
        NewsArticle genericArticle = article("a7",
                "Health IT Briefing",
                "An agreement now supports Epic Systems rollout.");
        when(articleIngestionPort.fetchRecentArticles(30)).thenReturn(List.of(genericArticle));

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
        when(companyPort.findAll()).thenReturn(List.of());
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
        when(companyPort.findAll()).thenReturn(List.of());
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

    @Test
    void detectRelationships_llmAvailable_usesLlmResultInsteadOfKeywordExtraction() {
        CompanyRelationshipService llmService =
                new CompanyRelationshipService(articleIngestionPort, relationshipPort, classificationPort, companyPort);
        NewsArticle candidateArticle = article("a8",
                "Health IT Briefing",
                "An agreement now supports a new rollout.");
        CompanyRelationship llmRel = new CompanyRelationship(
                "llm-1", "Epic Systems", "Nuance Communications",
                CompanyRelationshipType.INTEGRATION, "https://example.com/a8",
                "Epic integrated Nuance's ambient AI into its EHR.", 0.9, Instant.now());
        when(articleIngestionPort.fetchRecentArticles(30)).thenReturn(List.of(candidateArticle));
        when(classificationPort.classifyRelationships(anyList())).thenReturn(List.of(llmRel));
        when(companyPort.findAll()).thenReturn(List.of());
        when(relationshipPort.existsBySourceAndTargetAndType(anyString(), anyString(), anyString()))
                .thenReturn(false);

        List<CompanyRelationship> result = llmService.detectRelationships();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sourceCompany()).isEqualTo("Epic Systems");
        assertThat(result.get(0).targetCompany()).isEqualTo("Nuance Communications");
    }

    @Test
    void detectRelationships_llmReturnsEmpty_fallsBackToKeywordExtraction() {
        CompanyRelationshipService llmService =
                new CompanyRelationshipService(articleIngestionPort, relationshipPort, classificationPort, companyPort);
        NewsArticle candidateArticle = article("a9",
                "Epic integrates with Nuance AI platform",
                "Epic Systems announced integration with Nuance.");
        when(articleIngestionPort.fetchRecentArticles(30)).thenReturn(List.of(candidateArticle));
        when(classificationPort.classifyRelationships(anyList())).thenReturn(List.of());
        when(companyPort.findAll()).thenReturn(List.of());
        when(relationshipPort.existsBySourceAndTargetAndType(anyString(), anyString(), anyString()))
                .thenReturn(false);

        List<CompanyRelationship> result = llmService.detectRelationships();

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).relationshipType()).isEqualTo(CompanyRelationshipType.INTEGRATION);
    }

    @Test
    void detectRelationships_llmResultWithJunkName_stillFiltered() {
        CompanyRelationshipService llmService =
                new CompanyRelationshipService(articleIngestionPort, relationshipPort, classificationPort, companyPort);
        NewsArticle candidateArticle = article("a10",
                "Health IT Briefing",
                "An agreement now supports a new rollout.");
        CompanyRelationship junkRel = new CompanyRelationship(
                "llm-2", "Agreement", "Some Rollout",
                CompanyRelationshipType.INTEGRATION, "https://example.com/a10",
                "Bad extraction despite LLM prompt.", 0.9, Instant.now());
        when(articleIngestionPort.fetchRecentArticles(30)).thenReturn(List.of(candidateArticle));
        when(classificationPort.classifyRelationships(anyList())).thenReturn(List.of(junkRel));

        List<CompanyRelationship> result = llmService.detectRelationships();

        assertThat(result).isEmpty();
        verify(relationshipPort, never()).saveAll(any());
    }

    @Test
    void detectRelationships_knownCompanyNameVariant_canonicalizesToDirectoryName() {
        CompanyRelationshipService llmService =
                new CompanyRelationshipService(articleIngestionPort, relationshipPort, classificationPort, companyPort);
        NewsArticle candidateArticle = article("a11",
                "R1 RCM completes acquisition",
                "R1 RCM acquires Humata Health, an AI-powered prior authorization company.");
        CompanyRelationship llmRel = new CompanyRelationship(
                "llm-3", "R1 RCM", "Humata Health",
                CompanyRelationshipType.ACQUISITION, "https://example.com/a11",
                "R1 RCM completed its acquisition of Humata Health.", 0.9, Instant.now());
        when(articleIngestionPort.fetchRecentArticles(30)).thenReturn(List.of(candidateArticle));
        when(classificationPort.classifyRelationships(anyList())).thenReturn(List.of(llmRel));
        when(companyPort.findAll()).thenReturn(List.of(knownCompany("R1", "r1")));
        when(relationshipPort.existsBySourceAndTargetAndType(anyString(), anyString(), anyString()))
                .thenReturn(false);

        List<CompanyRelationship> result = llmService.detectRelationships();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sourceCompany()).isEqualTo("R1");
        assertThat(result.get(0).targetCompany()).isEqualTo("Humata Health");
    }

    @Test
    void detectRelationships_noDirectoryMatch_keepsOriginalName() {
        CompanyRelationshipService llmService =
                new CompanyRelationshipService(articleIngestionPort, relationshipPort, classificationPort, companyPort);
        NewsArticle candidateArticle = article("a12",
                "Universities partner on AI research",
                "Yale University partners with Indiana University on an NIH grant.");
        CompanyRelationship llmRel = new CompanyRelationship(
                "llm-4", "Yale University", "Indiana University",
                CompanyRelationshipType.PARTNERSHIP, "https://example.com/a12",
                "Yale and Indiana University partnered on an NIH-funded AI research center.", 0.85, Instant.now());
        when(articleIngestionPort.fetchRecentArticles(30)).thenReturn(List.of(candidateArticle));
        when(classificationPort.classifyRelationships(anyList())).thenReturn(List.of(llmRel));
        when(companyPort.findAll()).thenReturn(List.of(knownCompany("R1", "r1")));
        when(relationshipPort.existsBySourceAndTargetAndType(anyString(), anyString(), anyString()))
                .thenReturn(false);

        List<CompanyRelationship> result = llmService.detectRelationships();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sourceCompany()).isEqualTo("Yale University");
        assertThat(result.get(0).targetCompany()).isEqualTo("Indiana University");
    }
}
