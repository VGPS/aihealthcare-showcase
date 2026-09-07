package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import com.wgblackmon.aihealthcare.domain.marketanalysis.PeerGroup;
import com.wgblackmon.aihealthcare.domain.marketanalysis.PrivateFundingRound;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.PrivateFundingPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * JPA-backed adapter implementing {@link PrivateFundingPort}.
 *
 * <p>Persists private funding rounds to the {@code private_funding_round} table.
 * Lead investors are stored as a pipe-delimited TEXT column and split on retrieval.
 * Peer group is stored as the enum name string.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Slf4j
@Component
public class PrivateFundingRoundAdapter implements PrivateFundingPort {

    private static final String PIPE = "|";

    private final PrivateFundingRoundJpaRepository repo;

    public PrivateFundingRoundAdapter(PrivateFundingRoundJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public void save(PrivateFundingRound round, PeerGroup peerGroup) {
        log.debug("save() | company={}, stage={}, peerGroup={}",
                round.companyName(), round.roundStage(), peerGroup);

        String investors = String.join(PIPE, round.leadInvestors());
        PrivateFundingRoundEntity entity = new PrivateFundingRoundEntity(
                round.companyName(),
                round.roundStage(),
                round.amountUsd(),
                investors,
                round.announcedAt(),
                peerGroup.name(),
                round.sourceUrl()
        );
        repo.save(entity);

        log.debug("save() | return=void");
    }

    @Override
    public List<PrivateFundingRound> findRecentRounds(Instant since, PeerGroup peerGroupFilter) {
        log.debug("findRecentRounds() | since={}, peerGroupFilter={}", since, peerGroupFilter);

        String peerGroupName = peerGroupFilter != null ? peerGroupFilter.name() : null;
        List<PrivateFundingRoundEntity> entities = repo.findBySinceAndPeerGroup(since, peerGroupName);

        List<PrivateFundingRound> result = new ArrayList<>();
        for (PrivateFundingRoundEntity entity : entities) {
            result.add(toDomain(entity));
        }

        log.debug("findRecentRounds() | return.size={}", result.size());
        return result;
    }

    // ─── private helpers ────────────────────────────────────────────────────

    private PrivateFundingRound toDomain(PrivateFundingRoundEntity entity) {
        List<String> investors = new ArrayList<>();
        if (entity.getLeadInvestors() != null && !entity.getLeadInvestors().isEmpty()) {
            investors.addAll(Arrays.asList(entity.getLeadInvestors().split("\\|")));
        }
        return new PrivateFundingRound(
                entity.getCompanyName(),
                entity.getRoundStage(),
                entity.getAmountUsd(),
                investors,
                entity.getAnnouncedAt(),
                entity.getSourceUrl()
        );
    }
}
