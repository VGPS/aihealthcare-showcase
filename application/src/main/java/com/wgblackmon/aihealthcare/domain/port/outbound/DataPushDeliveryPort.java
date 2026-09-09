package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.DataArtifact;
import com.wgblackmon.aihealthcare.domain.model.DataJob;
import com.wgblackmon.aihealthcare.domain.model.DataPushSchedule;
import com.wgblackmon.aihealthcare.domain.model.PushDeliveryResult;

/**
 * Delivers a completed data artifact to a push schedule's recipients.
 *
 * <p>The implementation decides attachment vs signed link based on
 * artifact size. It must never throw for a delivery failure — it
 * returns a failed {@link PushDeliveryResult} so the scheduler can
 * record the outcome and apply back-off.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public interface DataPushDeliveryPort {

    PushDeliveryResult deliver(DataPushSchedule schedule, DataJob job, DataArtifact artifact);
}
