package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code search_prompts} table.
 *
 * <p>Maps to the {@link com.wgblackmon.aihealthcare.domain.model.SearchPromptConfig}
 * domain record via {@link SearchPromptAdapter}. The {@code engine} column serves as
 * the natural primary key (e.g. {@code "GOOGLE"}, {@code "PERPLEXITY"}).
 *
 * <p>This entity never escapes the persistence package — conversion to and from the
 * domain record is handled exclusively by {@link SearchPromptAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-28
 * @updated 2026-04-28
 */
@Entity
@Table(name = "search_prompts")
public class SearchPromptEntity {

    @Id
    @Column(name = "engine", length = 50, nullable = false)
    private String engine;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "template_text", nullable = false, columnDefinition = "TEXT")
    private String templateText;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active;

    /** Required by JPA. */
    protected SearchPromptEntity() {}

    public SearchPromptEntity(String engine, String name, String templateText,
                               String description, boolean active) {
        this.engine = engine;
        this.name = name;
        this.templateText = templateText;
        this.description = description;
        this.active = active;
    }

    public String getEngine()       { return engine; }
    public String getName()         { return name; }
    public String getTemplateText() { return templateText; }
    public String getDescription()  { return description; }
    public boolean isActive()       { return active; }

    public void setName(String name)               { this.name = name; }
    public void setTemplateText(String templateText) { this.templateText = templateText; }
    public void setDescription(String description) { this.description = description; }
    public void setActive(boolean active)          { this.active = active; }
}
