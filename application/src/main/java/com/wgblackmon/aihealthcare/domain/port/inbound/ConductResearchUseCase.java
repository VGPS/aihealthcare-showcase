package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.ResearchAnswer;
import com.wgblackmon.aihealthcare.domain.model.ResearchRequest;

/**
 * Inbound port — conduct a research query and return a structured answer.
 *
 * <p>The single method drives the full research pipeline: planning, source retrieval,
 * synthesis, and citation assembly.  Callers (typically REST controllers) depend only
 * on this interface; the concrete implementation in
 * {@link com.wgblackmon.aihealthcare.domain.service.ResearchOrchestratorService} is
 * wired via {@code AppConfig}.
 *
 * <p>The active pipeline variant (legacy or staged) is selected by the
 * {@link com.wgblackmon.aihealthcare.domain.model.ResearchMode} carried in the
 * {@link ResearchRequest}, with a configured default applied when {@code mode} is
 * {@code null}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-04
 * @updated 2026-05-04
 */
public interface ConductResearchUseCase {

    /**
     * Execute the research pipeline for the given request and return the assembled answer.
     *
     * @param request The research request; must not be {@code null}.
     * @return A structured {@link ResearchAnswer}; never {@code null}.
     * @throws IllegalArgumentException if {@code request.query()} is blank.
     */
    ResearchAnswer conduct(ResearchRequest request);
}
