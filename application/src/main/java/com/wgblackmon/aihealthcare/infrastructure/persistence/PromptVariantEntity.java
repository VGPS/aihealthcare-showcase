package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code prompt_variants} table.
 *
 * <p>This is a mutable infrastructure class that mirrors the immutable
 * {@link com.wgblackmon.aihealthcare.domain.model.PromptVariant} domain record.
 * All mapping between the two types happens inside {@link PromptVariantAdapter}.
 *
 * <p>The {@code template_text} column is {@code TEXT} to accommodate long
 * prompt templates without length restrictions.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-17
 * @updated 2026-04-17
 */
@Entity
@Table(name = "prompt_variants")
public class PromptVariantEntity {

    @Id
    @Column(name = "variant_id")
    private String variantId;

    private String name;

    @Column(name = "template_text", columnDefinition = "TEXT")
    private String templateText;

    private String description;

    @Column(name = "created_at")
    private Instant createdAt;

    /** Required no-arg constructor for JPA. */
    public PromptVariantEntity() {}

    public String getVariantId() { return variantId; }
    public void setVariantId(String variantId) { this.variantId = variantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTemplateText() { return templateText; }
    public void setTemplateText(String templateText) { this.templateText = templateText; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
