package com.wgblackmon.aihealthcare.domain.model;

/**
 * Action types recorded during SSO user provisioning events.
 *
 * <p>Tracks the lifecycle of SSO users: first-time account creation,
 * linking an existing account to an IdP, or a routine SSO login.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-15
 * @updated 2026-09-15
 */
public enum SsoProvisioningAction {

    /** New AppUser + Subscriber created on first SAML login. */
    CREATED,

    /** Existing account linked to SSO IdP (tier upgraded to ENTERPRISE). */
    LINKED,

    /** Routine SSO login — account already provisioned. */
    LOGIN
}
