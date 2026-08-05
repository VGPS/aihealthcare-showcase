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
 * @updated 2026-08-04
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "aihealthcare.tiers")
public class TierLimitProperties {

    private TierConfig free = new TierConfig();
    private TierConfig subscriber = new TierConfig();
    private TierConfig demo = new TierConfig();
    private TierConfig freePending = new TierConfig();
    private TierConfig enterprise = new TierConfig();

    public TierConfig getFree()                            { return free; }
    public void setFree(TierConfig free)                   { this.free = free; }

    public TierConfig getSubscriber()                      { return subscriber; }
    public void setSubscriber(TierConfig subscriber)       { this.subscriber = subscriber; }

    public TierConfig getDemo()                            { return demo; }
    public void setDemo(TierConfig demo)                   { this.demo = demo; }

    public TierConfig getFreePending()                     { return freePending; }
    public void setFreePending(TierConfig freePending)     { this.freePending = freePending; }

    public TierConfig getEnterprise()                      { return enterprise; }
    public void setEnterprise(TierConfig enterprise)       { this.enterprise = enterprise; }

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
