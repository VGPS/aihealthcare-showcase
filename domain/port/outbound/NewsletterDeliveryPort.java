package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;

import java.util.List;

/**
 * Outbound port for delivering a newsletter to a list of recipients.
 *
 * <p>Implementations live in {@code infrastructure.delivery} and use a concrete
 * transport mechanism (SMTP via Spring Mail in production, MailHog locally).
 * The domain and application layers depend only on this interface and are
 * completely insulated from JavaMail, SMTP, or any other delivery technology.
 *
 * <p>Delivery is best-effort per recipient: if one address fails, the implementation
 * should log the error and continue to the remaining recipients rather than
 * aborting the entire batch.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-13
 * @updated 2026-04-13
 */
public interface NewsletterDeliveryPort {

    /**
     * Sends the given newsletter run to each recipient in the list.
     *
     * @param run        The newsletter run whose HTML and plain-text content will
     *                   be sent as the email body.
     * @param recipients The list of subscribers to deliver to; must not be empty.
     */
    void deliver(NewsletterRun run, List<Subscriber> recipients);
}
