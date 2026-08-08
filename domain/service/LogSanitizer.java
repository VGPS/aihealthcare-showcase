package com.wgblackmon.aihealthcare.domain.service;

/**
 * Masks PII values for safe inclusion in log statements.
 *
 * <p>Produces partially redacted representations that preserve enough
 * structure for debugging (first 2 local chars, first domain char,
 * TLD) without exposing the full value.  Example:
 * {@code "user@gmail.com"} becomes {@code "us***@g***.com"}.
 *
 * <p>This class uses only JDK types so it is safe for the domain module.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-07
 * @updated 2026-08-07
 */
public final class LogSanitizer {

    private LogSanitizer() {}

    /**
     * Masks an email address for log output.
     *
     * @param email the raw email address (may be null)
     * @return masked form such as {@code "us***@g***.com"}, or
     *         {@code "[REDACTED]"} if the input is null or malformed
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "[REDACTED]";
        }
        int atIdx = email.indexOf('@');
        String local = email.substring(0, atIdx);
        String domain = email.substring(atIdx + 1);

        String maskedLocal = local.length() <= 2
                ? "***"
                : local.substring(0, 2) + "***";

        int dotIdx = domain.lastIndexOf('.');
        String maskedDomain;
        if (dotIdx > 0) {
            maskedDomain = domain.substring(0, 1) + "***" + domain.substring(dotIdx);
        } else {
            maskedDomain = "***";
        }

        return maskedLocal + "@" + maskedDomain;
    }
}
