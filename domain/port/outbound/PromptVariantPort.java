package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.PromptVariant;

import java.util.List;

/**
 * Outbound port — CRUD operations for {@link PromptVariant} records.
 *
 * <p>Implementations live in {@code infrastructure/persistence} and persist
 * prompt templates to the database.  The application layer depends only on
 * this interface; no JPA or SQL details leak inward.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-17
 * @updated 2026-04-17
 */
public interface PromptVariantPort {

    /**
     * Persist a prompt variant.  If a variant with the same ID exists, it is overwritten.
     *
     * @param variant the variant to save
     */
    void save(PromptVariant variant);

    /**
     * Retrieve a prompt variant by its unique identifier.
     *
     * @param variantId the variant identifier
     * @return the matching variant
     * @throws com.wgblackmon.aihealthcare.domain.exception.PromptVariantNotFoundException
     *         if no variant exists for the given ID
     */
    PromptVariant findByVariantId(String variantId);

    /**
     * Retrieve all stored prompt variants.
     *
     * @return all variants, or an empty list if none exist
     */
    List<PromptVariant> findAll();

    /**
     * Delete a prompt variant by its unique identifier.
     *
     * @param variantId the variant identifier
     * @throws com.wgblackmon.aihealthcare.domain.exception.PromptVariantNotFoundException
     *         if no variant exists for the given ID
     */
    void delete(String variantId);
}
