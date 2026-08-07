package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity representing a row in the {@code topics} table.
 *
 * <p>This is a mutable infrastructure class that mirrors the immutable
 * {@link com.wgblackmon.aihealthcare.domain.model.Topic} domain record.
 * All mapping between the two types happens inside the adapter layer —
 * this entity never escapes into the domain or application layers.
 *
 * <p>The {@code topics} table is seeded at startup via {@code data.sql}
 * with one row for "AI Healthcare".  Additional topics can be added by
 * inserting rows without any code changes.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-11
 * @updated 2026-08-07
 */
@Entity
@Table(name = "topics")
public class TopicEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    @Column(name = "prompt_context", columnDefinition = "TEXT")
    private String promptContext;

    private String tone;

    private boolean active;

    /** Required no-arg constructor for JPA. */
    public TopicEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getPromptContext() { return promptContext; }
    public void setPromptContext(String promptContext) { this.promptContext = promptContext; }

    public String getTone() { return tone; }
    public void setTone(String tone) { this.tone = tone; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
