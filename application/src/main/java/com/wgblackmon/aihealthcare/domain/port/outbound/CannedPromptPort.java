package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.CannedPrompt;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for admin-curated enterprise data prompts.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public interface CannedPromptPort {

    List<CannedPrompt> findAllActive();

    List<CannedPrompt> findByFeedId(String feedId);

    Optional<CannedPrompt> findById(String promptId);

    CannedPrompt save(CannedPrompt prompt);

    void delete(String promptId);
}
