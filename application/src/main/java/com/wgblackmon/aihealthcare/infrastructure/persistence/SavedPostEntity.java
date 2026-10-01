package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity for a saved social post draft ({@code saved_posts} table).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
@Entity
@Table(name = "saved_posts")
public class SavedPostEntity {

    @Id
    private String postId;

    private String platform;
    private String digestDate;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(columnDefinition = "TEXT")
    private String yourTake;

    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant postedAt;

    protected SavedPostEntity() {}

    public SavedPostEntity(String postId, String platform, String digestDate,
                           String body, String comment, String yourTake,
                           String status, Instant createdAt, Instant updatedAt,
                           Instant postedAt) {
        this.postId = postId;
        this.platform = platform;
        this.digestDate = digestDate;
        this.body = body;
        this.comment = comment;
        this.yourTake = yourTake;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.postedAt = postedAt;
    }

    public String getPostId() { return postId; }
    public String getPlatform() { return platform; }
    public String getDigestDate() { return digestDate; }
    public String getBody() { return body; }
    public String getComment() { return comment; }
    public String getYourTake() { return yourTake; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getPostedAt() { return postedAt; }
}
