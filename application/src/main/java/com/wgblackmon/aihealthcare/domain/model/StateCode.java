package com.wgblackmon.aihealthcare.domain.model;

import java.util.Optional;

/**
 * Enumeration of all 50 US states plus the District of Columbia.
 *
 * <p>Each value carries a human-readable {@code displayName} (e.g. "Alabama")
 * used for UI labels and search results. The static {@link #fromCode(String)}
 * method provides safe parsing from string values.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
public enum StateCode {

    AL("Alabama"),
    AK("Alaska"),
    AZ("Arizona"),
    AR("Arkansas"),
    CA("California"),
    CO("Colorado"),
    CT("Connecticut"),
    DE("Delaware"),
    DC("District of Columbia"),
    FL("Florida"),
    GA("Georgia"),
    HI("Hawaii"),
    ID("Idaho"),
    IL("Illinois"),
    IN("Indiana"),
    IA("Iowa"),
    KS("Kansas"),
    KY("Kentucky"),
    LA("Louisiana"),
    ME("Maine"),
    MD("Maryland"),
    MA("Massachusetts"),
    MI("Michigan"),
    MN("Minnesota"),
    MS("Mississippi"),
    MO("Missouri"),
    MT("Montana"),
    NE("Nebraska"),
    NV("Nevada"),
    NH("New Hampshire"),
    NJ("New Jersey"),
    NM("New Mexico"),
    NY("New York"),
    NC("North Carolina"),
    ND("North Dakota"),
    OH("Ohio"),
    OK("Oklahoma"),
    OR("Oregon"),
    PA("Pennsylvania"),
    RI("Rhode Island"),
    SC("South Carolina"),
    SD("South Dakota"),
    TN("Tennessee"),
    TX("Texas"),
    UT("Utah"),
    VT("Vermont"),
    VA("Virginia"),
    WA("Washington"),
    WV("West Virginia"),
    WI("Wisconsin"),
    WY("Wyoming");

    private final String displayName;

    StateCode(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the full state name (e.g. "California").
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Parses a state code string (case-insensitive) into a {@code StateCode}.
     *
     * @param code the two-letter state code (e.g. "CA", "ca")
     * @return the matching {@code StateCode}, or empty if not found
     */
    public static Optional<StateCode> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String upper = code.trim().toUpperCase();
        for (StateCode sc : values()) {
            if (sc.name().equals(upper)) {
                return Optional.of(sc);
            }
        }
        return Optional.empty();
    }
}
