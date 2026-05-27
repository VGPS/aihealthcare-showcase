package com.wgblackmon.aihealthcare.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for subscription tier feature limits.
 *
 * <p>Maps the {@code aihealthcare.tiers.*} keys from {@code application.yml}
 * into a type-safe bean.  Each tier has:
 * <ul>
 *   <li>{@code archiveDays} — how many days of article archive the tier can access (0 = unlimited)</li>
 *   <li>{@code monthlyQueryLimit} — maximum AI research queries per month</li>
 * </ul>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-26
 * @updated 2026-05-26
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "aihealthcare.tiers")
public class TierLimitProperties {

    private TierConfig free = new TierConfig();
    private TierConfig member = new TierConfig();

    public TierConfig getFree()                    { return free; }
    public void setFree(TierConfig free)           { this.free = free; }

    public TierConfig getMember()                  { return member; }
    public void setMember(TierConfig member)       { this.member = member; }

    /**
     * Nested config for a single tier's limits.
     */
    public static class TierConfig {
        private int archiveDays = 30;
        private int monthlyQueryLimit = 15;

        public int getArchiveDays()                            { return archiveDays; }
        public void setArchiveDays(int archiveDays)            { this.archiveDays = archiveDays; }

        public int getMonthlyQueryLimit()                      { return monthlyQueryLimit; }
        public void setMonthlyQueryLimit(int monthlyQueryLimit) { this.monthlyQueryLimit = monthlyQueryLimit; }
    }
}
