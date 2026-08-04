package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.WatchlistMatch;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistMatchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WatchlistPort;
import com.wgblackmon.aihealthcare.domain.model.WatchlistItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA-backed implementation of {@link WatchlistMatchPort}.
 *
 * <p>Converts between the immutable {@link WatchlistMatch} domain record
 * and the mutable {@link WatchlistMatchEntity} JPA entity.
 *
 * <p>The {@link #findByUser} method first retrieves the user's watchlist
 * item IDs via {@link WatchlistPort}, then queries matches for those items.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-08-01
 */
@Slf4j
@Component
public class WatchlistMatchAdapter implements WatchlistMatchPort {

    private final WatchlistMatchRepository matchRepository;
    private final WatchlistPort watchlistPort;

    public WatchlistMatchAdapter(WatchlistMatchRepository matchRepository,
                                  WatchlistPort watchlistPort) {
        log.debug("WatchlistMatchAdapter() | matchRepository={}, watchlistPort={}",
                matchRepository.getClass().getSimpleName(),
                watchlistPort.getClass().getSimpleName());
        this.matchRepository = matchRepository;
        this.watchlistPort = watchlistPort;
    }

    @Override
    public void save(WatchlistMatch match) {
        log.debug("save() | matchId={}, itemId={}, articleId={}", match.matchId(), match.itemId(), match.articleId());
        matchRepository.save(toEntity(match));
        log.debug("save() | return=void");
    }

    @Override
    public void saveAll(List<WatchlistMatch> matches) {
        log.debug("saveAll() | count={}", matches.size());
        List<WatchlistMatchEntity> entities = new ArrayList<>();
        for (WatchlistMatch match : matches) {
            entities.add(toEntity(match));
        }
        matchRepository.saveAll(entities);
        log.debug("saveAll() | return=void");
    }

    @Override
    @Transactional(readOnly = true)
    public List<WatchlistMatch> findByUser(String email, int limit) {
        log.debug("findByUser() | email={}, limit={}", email, limit);

        // Get user's watchlist item IDs
        List<WatchlistItem> items = watchlistPort.findByUser(email);
        if (items.isEmpty()) {
            log.debug("findByUser() | return=0 matches (no watchlist items)");
            return List.of();
        }

        List<String> itemIds = new ArrayList<>();
        for (WatchlistItem item : items) {
            itemIds.add(item.itemId());
        }

        List<WatchlistMatchEntity> entities = matchRepository.findByItemIdInOrderByMatchedOnDesc(itemIds);

        List<WatchlistMatch> result = new ArrayList<>();
        int count = 0;
        for (WatchlistMatchEntity entity : entities) {
            if (count >= limit) {
                break;
            }
            result.add(toDomain(entity));
            count++;
        }

        log.debug("findByUser() | return={} matches", result.size());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WatchlistMatch> findByItem(String itemId, int limit) {
        log.debug("findByItem() | itemId={}, limit={}", itemId, limit);

        List<WatchlistMatchEntity> entities = matchRepository.findByItemIdOrderByMatchedOnDesc(itemId);

        List<WatchlistMatch> result = new ArrayList<>();
        int count = 0;
        for (WatchlistMatchEntity entity : entities) {
            if (count >= limit) {
                break;
            }
            result.add(toDomain(entity));
            count++;
        }

        log.debug("findByItem() | return={} matches", result.size());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WatchlistMatch> findByUserSince(String email, Instant since) {
        log.debug("findByUserSince() | email={}, since={}", email, since);

        List<WatchlistItem> items = watchlistPort.findByUser(email);
        if (items.isEmpty()) {
            log.debug("findByUserSince() | return=0 matches (no watchlist items)");
            return List.of();
        }

        List<String> itemIds = new ArrayList<>();
        for (WatchlistItem item : items) {
            itemIds.add(item.itemId());
        }

        List<WatchlistMatchEntity> entities =
                matchRepository.findByItemIdInAndMatchedOnAfterOrderByMatchedOnDesc(itemIds, since);

        List<WatchlistMatch> result = new ArrayList<>();
        for (WatchlistMatchEntity entity : entities) {
            result.add(toDomain(entity));
        }

        log.debug("findByUserSince() | return={} matches", result.size());
        return result;
    }

    @Override
    public boolean existsByItemAndArticle(String itemId, String articleId) {
        log.debug("existsByItemAndArticle() | itemId={}, articleId={}", itemId, articleId);
        boolean result = matchRepository.existsByItemIdAndArticleId(itemId, articleId);
        log.debug("existsByItemAndArticle() | return={}", result);
        return result;
    }

    private WatchlistMatchEntity toEntity(WatchlistMatch match) {
        WatchlistMatchEntity entity = new WatchlistMatchEntity();
        entity.setMatchId(match.matchId());
        entity.setItemId(match.itemId());
        entity.setArticleId(match.articleId());
        entity.setMatchedOn(match.matchedOn());
        entity.setSnippet(match.snippet());
        return entity;
    }

    private WatchlistMatch toDomain(WatchlistMatchEntity entity) {
        return new WatchlistMatch(
                entity.getMatchId(),
                entity.getItemId(),
                entity.getArticleId(),
                entity.getMatchedOn(),
                entity.getSnippet()
        );
    }
}
