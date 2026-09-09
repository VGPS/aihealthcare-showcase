package com.wgblackmon.aihealthcare.domain.model;

/**
 * How a push delivery was sent to the recipient.
 *
 * <p>Recorded on the audit entry so support can immediately answer
 * "did they get the file or the link?" without digging through logs.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public enum PushDeliveryMode {

    ATTACHMENT,
    SIGNED_LINK
}
