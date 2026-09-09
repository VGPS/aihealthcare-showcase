package com.wgblackmon.aihealthcare.domain.model;

/**
 * Transport protocol for a customer-side remote connection.
 *
 * <p>Only {@code HTTPS_JSON} is supported in ED-1. JDBC and S3 connectors
 * are deferred to ED-3.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public enum RemoteConnectionKind {

    HTTPS_JSON
}
