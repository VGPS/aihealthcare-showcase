package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code watchlist_matches} table.
 *
 * <p>Maps to the immutable {@link com.wgblackmon.aihealthcare.domain.model.WatchlistMatch}
 * domain record. Conversion is performed inside {@link WatchlistMatchAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
@Entity
@Table(name = "watchlist_matches")
public class WatchlistMatchEntity {

    @Id
    @Column(name = "match_id", nullable = false, length = 36)
    private String matchId;

    @Column(name = "item_id", nullable = false, length = 36)
    private String itemId;

    @Column(name = "article_id", nullable = false, length = 255)
    private String articleId;

    @Column(name = "matched_on", nullable = false)
    private Instant matchedOn;

    @Lob
    @Column(name = "snippet")
    private String snippet;

    /** Required no-arg constructor for JPA. */
    public WatchlistMatchEntity() {}

    public String getMatchId()                       { return matchId; }
    public void setMatchId(String matchId)           { this.matchId = matchId; }

    public String getItemId()                        { return itemId; }
    public void setItemId(String itemId)             { this.itemId = itemId; }

    public String getArticleId()                     { return articleId; }
    public void setArticleId(String articleId)       { this.articleId = articleId; }

    public Instant getMatchedOn()                    { return matchedOn; }
    public void setMatchedOn(Instant matchedOn)      { this.matchedOn = matchedOn; }

    public String getSnippet()                       { return snippet; }
    public void setSnippet(String snippet)           { this.snippet = snippet; }
}
