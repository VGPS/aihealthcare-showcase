package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.SsoProvisioningEvent;

import java.util.List;

/**
 * Outbound port for recording and querying SSO provisioning audit events.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
public interface SsoProvisioningEventPort {

    void record(SsoProvisioningEvent event);

    List<SsoProvisioningEvent> findByEmail(String email);

    List<SsoProvisioningEvent> findByRegistrationId(String registrationId);
}
