package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity for the {@code enterprise_data_prompts} table — admin-curated,
 * parameterised prompts for the enterprise data catalogue.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Entity
@Table(name = "enterprise_data_prompts")
public class EnterpriseDataPromptEntity {

    @Id
    @Column(name = "prompt_id", length = 64)
    private String promptId;

    @Column(name = "label", nullable = false, length = 160)
    private String label;

    @Column(name = "description", length = 1024)
    private String description;

    @Column(name = "feed_id", nullable = false, length = 64)
    private String feedId;

    @Column(name = "template_text", nullable = false, columnDefinition = "TEXT")
    private String templateText;

    @Column(name = "parameters_json", columnDefinition = "TEXT")
    private String parametersJson;

    @Column(name = "min_tier", nullable = false, length = 32)
    private String minTier;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required no-arg constructor for JPA. */
    public EnterpriseDataPromptEntity() {}

    public String getPromptId() { return promptId; }
    public void setPromptId(String promptId) { this.promptId = promptId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getFeedId() { return feedId; }
    public void setFeedId(String feedId) { this.feedId = feedId; }
    public String getTemplateText() { return templateText; }
    public void setTemplateText(String templateText) { this.templateText = templateText; }
    public String getParametersJson() { return parametersJson; }
    public void setParametersJson(String parametersJson) { this.parametersJson = parametersJson; }
    public String getMinTier() { return minTier; }
    public void setMinTier(String minTier) { this.minTier = minTier; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
