package com.wgblackmon.aihealthcare.domain.marketanalysis;

import com.wgblackmon.aihealthcare.domain.marketanalysis.port.PriceReactionPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PriceReactionQueryService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-28
 * @updated 2026-08-28
 */
@ExtendWith(MockitoExtension.class)
class PriceReactionQueryServiceTest {

    @Mock
    private PriceReactionPort reactionPort;

    @Test
    void findReactions_delegatesToPort() {
        Instant publishedAt = Instant.parse("2026-08-19T12:00:00Z");
        PriceReactionSnapshot snapshot = new PriceReactionSnapshot(
                "entry-1", "DOCS", ReactionHorizon.ONE_DAY,
                new BigDecimal("10.00"), new BigDecimal("10.80"),
                new BigDecimal("8.00"), Instant.now());
        when(reactionPort.findByTickerAndPublishedAt("DOCS", publishedAt)).thenReturn(List.of(snapshot));

        PriceReactionQueryService service = new PriceReactionQueryService(reactionPort);
        List<PriceReactionSnapshot> result = service.findReactions("DOCS", publishedAt);

        assertThat(result).containsExactly(snapshot);
        verify(reactionPort).findByTickerAndPublishedAt("DOCS", publishedAt);
    }

    @Test
    void findReactions_whenNoneCaptured_returnsEmpty() {
        Instant publishedAt = Instant.parse("2026-08-19T12:00:00Z");
        when(reactionPort.findByTickerAndPublishedAt("DOCS", publishedAt)).thenReturn(List.of());

        PriceReactionQueryService service = new PriceReactionQueryService(reactionPort);
        List<PriceReactionSnapshot> result = service.findReactions("DOCS", publishedAt);

        assertThat(result).isEmpty();
    }
}
