package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.CannedPrompt;
import com.wgblackmon.aihealthcare.domain.model.DataFeed;
import com.wgblackmon.aihealthcare.domain.model.DataJob;
import com.wgblackmon.aihealthcare.domain.model.DataRequest;

import java.util.List;

/**
 * Inbound port for the enterprise data PULL feature.
 *
 * <p>Drives job submission, status retrieval, cancellation, and the feed/prompt
 * catalogue. Every method that returns a job scopes to the caller's email —
 * another owner's data is invisible.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public interface RequestEnterpriseDataUseCase {

    DataJob submit(DataRequest request);

    DataJob getJob(String jobId, String ownerEmail);

    List<DataJob> listJobs(String ownerEmail, int page, int size);

    void cancel(String jobId, String ownerEmail);

    List<DataFeed> listFeeds(String ownerEmail);

    List<CannedPrompt> listPrompts(String ownerEmail, String feedId);

    String readLog(String jobId, String ownerEmail, long fromByteOffset);
}
