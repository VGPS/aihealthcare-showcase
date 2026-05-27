package com.wgblackmon.aihealthcare.infrastructure.persistence;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key class for {@link UsageRecordEntity}.
 *
 * <p>Required by JPA when using {@code @IdClass} with a composite key.
 * The field names must match exactly the {@code @Id} fields in the entity.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-26
 * @updated 2026-05-26
 */
public class UsageRecordId implements Serializable {

    private String email;
    private String yearMonth;

    /** Required no-arg constructor for JPA. */
    public UsageRecordId() {}

    public UsageRecordId(String email, String yearMonth) {
        this.email     = email;
        this.yearMonth = yearMonth;
    }

    public String getEmail()       { return email; }
    public String getYearMonth()   { return yearMonth; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UsageRecordId that = (UsageRecordId) o;
        return Objects.equals(email, that.email) && Objects.equals(yearMonth, that.yearMonth);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email, yearMonth);
    }
}
