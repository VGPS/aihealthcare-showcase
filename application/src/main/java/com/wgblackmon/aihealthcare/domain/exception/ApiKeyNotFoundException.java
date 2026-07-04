package com.wgblackmon.aihealthcare.domain.exception;

/**
 * Thrown when an API key lookup by ID finds no matching record.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-03
 * @updated 2026-07-03
 */
public class ApiKeyNotFoundException extends RuntimeException {

    private final String keyId;

    public ApiKeyNotFoundException(String keyId) {
        super("API key not found: " + keyId);
        this.keyId = keyId;
    }

    public String getKeyId() {
        return keyId;
    }
}
