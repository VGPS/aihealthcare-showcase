package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.ClaimType;
import com.wgblackmon.aihealthcare.domain.model.ClaimVerdict;
import com.wgblackmon.aihealthcare.domain.model.FrontierClaim;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ClaimClassifierPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.FrontierClaimPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FrontierClaimService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-05
 * @updated 2026-10-05
 */
@ExtendWith(MockitoExtension.class)
class FrontierClaimServiceTest {

    @Mock
    private ClaimClassifierPort classifierPort;

    @Mock
    private FrontierClaimPort claimPort;

    private FrontierClaimService service;

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    @BeforeEach
    void setUp() {
        service = new FrontierClaimService(classifierPort, claimPort);
    }

    @Test
    void detectClaims_savesNewClaims() {
        NewsArticle article = buildArticle("art-1");
        FrontierClaim claim = buildClaim("id-1", "OpenAI", "GPT-5 is best.");
        when(classifierPort.classifyClaims(any())).thenReturn(List.of(claim));
        when(claimPort.findAll()).thenReturn(List.of());

        List<FrontierClaim> result = service.detectClaims(List.of(article));

        assertThat(result).hasSize(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FrontierClaim>> captor = ArgumentCaptor.forClass(List.class);
        verify(claimPort).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
    }

    @Test
    void detectClaims_deduplicatesByCompanyAndText() {
        NewsArticle article = buildArticle("art-1");
        FrontierClaim existing = buildClaim("id-1", "OpenAI", "GPT-5 is best.");
        FrontierClaim duplicate = buildClaim("id-2", "OpenAI", "GPT-5 is best.");
        when(classifierPort.classifyClaims(any())).thenReturn(List.of(duplicate));
        when(claimPort.findAll()).thenReturn(List.of(existing));

        List<FrontierClaim> result = service.detectClaims(List.of(article));

        assertThat(result).isEmpty();
        verify(claimPort, never()).saveAll(anyList());
    }

    @Test
    void detectClaims_emptyArticles_returnsEmptyWithoutCallingClassifier() {
        List<FrontierClaim> result = service.detectClaims(List.of());

        assertThat(result).isEmpty();
        verify(classifierPort, never()).classifyClaims(any());
    }

    @Test
    void detectClaims_differentCompanySameText_bothSaved() {
        NewsArticle article = buildArticle("art-1");
        FrontierClaim claimA = buildClaim("id-1", "OpenAI", "Our model is best.");
        FrontierClaim claimB = buildClaim("id-2", "Anthropic", "Our model is best.");
        when(classifierPort.classifyClaims(any())).thenReturn(List.of(claimA, claimB));
        when(claimPort.findAll()).thenReturn(List.of());

        List<FrontierClaim> result = service.detectClaims(List.of(article));

        assertThat(result).hasSize(2);
    }

    @Test
    void getAll_delegatesToPort() {
        FrontierClaim claim = buildClaim("id-1", "Google", "Gemini is revolutionary.");
        when(claimPort.findAll()).thenReturn(List.of(claim));

        List<FrontierClaim> result = service.getAll();

        assertThat(result).hasSize(1);
        verify(claimPort).findAll();
    }

    @Test
    void getByCompany_delegatesToPort() {
        FrontierClaim claim = buildClaim("id-1", "Meta", "Llama outperforms all.");
        when(claimPort.findByCompany("Meta")).thenReturn(List.of(claim));

        List<FrontierClaim> result = service.getByCompany("Meta");

        assertThat(result).hasSize(1);
    }

    @Test
    void getByVerdict_delegatesToPortWithEnumName() {
        FrontierClaim claim = buildClaim("id-1", "Meta", "Llama outperforms all.");
        when(claimPort.findByVerdict("MARKETING_HYPE")).thenReturn(List.of(claim));

        List<FrontierClaim> result = service.getByVerdict(ClaimVerdict.MARKETING_HYPE);

        assertThat(result).hasSize(1);
        verify(claimPort).findByVerdict("MARKETING_HYPE");
    }

    @Test
    void getById_delegatesToPort() {
        FrontierClaim claim = buildClaim("id-1", "OpenAI", "GPT-5 is best.");
        when(claimPort.findById("id-1")).thenReturn(Optional.of(claim));

        Optional<FrontierClaim> result = service.getById("id-1");

        assertThat(result).isPresent();
    }

    private NewsArticle buildArticle(String id) {
        return new NewsArticle(id, "AI News", URI.create("https://example.com/" + id),
                "Article body", "AI", null, 1L, "Source", "INDUSTRY", 0.5, null);
    }

    private FrontierClaim buildClaim(String id, String company, String text) {
        return new FrontierClaim(
                id, company, text,
                null, "https://example.com", "Example",
                ClaimType.CAPABILITY_CLAIM, ClaimVerdict.MARKETING_HYPE,
                "No evidence.", "art-1", NOW, null);
    }
}
