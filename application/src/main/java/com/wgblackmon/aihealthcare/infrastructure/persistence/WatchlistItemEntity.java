package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code watchlist_items} table.
 *
 * <p>Maps to the immutable {@link com.wgblackmon.aihealthcare.domain.model.WatchlistItem}
 * domain record. Conversion is performed inside {@link WatchlistItemAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
@Entity
@Table(name = "watchlist_items")
public class WatchlistItemEntity {

    @Id
    @Column(name = "item_id", nullable = false, length = 36)
    private String itemId;

    @Column(name = "user_email", nullable = false, length = 255)
    private String userEmail;

    @Column(name = "item_type", nullable = false, length = 20)
    private String itemType;

    @Column(name = "\"value\"", nullable = false, length = 500)
    private String value;

    @Column(name = "label", nullable = false, length = 255)
    private String label;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Required no-arg constructor for JPA. */
    public WatchlistItemEntity() {}

    public String getItemId()                        { return itemId; }
    public void setItemId(String itemId)             { this.itemId = itemId; }

    public String getUserEmail()                     { return userEmail; }
    public void setUserEmail(String userEmail)       { this.userEmail = userEmail; }

    public String getItemType()                      { return itemType; }
    public void setItemType(String itemType)         { this.itemType = itemType; }

    public String getValue()                         { return value; }
    public void setValue(String value)                { this.value = value; }

    public String getLabel()                         { return label; }
    public void setLabel(String label)               { this.label = label; }

    public Instant getCreatedAt()                    { return createdAt; }
    public void setCreatedAt(Instant createdAt)      { this.createdAt = createdAt; }
}
