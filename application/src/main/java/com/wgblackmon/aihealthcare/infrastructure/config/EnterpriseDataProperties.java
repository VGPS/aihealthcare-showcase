package com.wgblackmon.aihealthcare.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration properties for the enterprise remote data access feature
 * (ED-1 PULL, ED-2 PUSH).
 *
 * <p>Bound from {@code aihealthcare.enterprise.data.*} in {@code application.yml}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "aihealthcare.enterprise.data")
public class EnterpriseDataProperties {

    private final Environment environment;

    public EnterpriseDataProperties(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validateProductionConstraints() {
        log.debug("validateProductionConstraints()");
        String[] profiles = environment.getActiveProfiles();
        boolean isProduction = Arrays.stream(profiles)
                .anyMatch(p -> "prod".equals(p) || "aws".equals(p));
        if (isProduction && (remote.getAllowedHosts() == null || remote.getAllowedHosts().isEmpty())) {
            throw new IllegalStateException(
                    "aihealthcare.enterprise.data.remote.allowed-hosts must be non-empty under prod/aws profiles. "
                    + "An empty allow-list would permit SSRF to any public host.");
        }
        log.debug("validateProductionConstraints() | return=void (profiles={}, production={})",
                Arrays.toString(profiles), isProduction);
    }

    private boolean enabled = true;
    private String artifactDirectory = "data-exports";
    private String logDirectory = "data-exports/logs";
    private int retentionDays = 14;
    private int maxRowsPerJob = 100_000;
    private long maxArtifactBytes = 52_428_800L;
    private int maxConcurrentJobsPerAccount = 2;
    private long jobTimeoutMs = 900_000L;
    private int staleJobReapMinutes = 30;
    private String reaperCron = "0 */5 * * * *";
    private String retentionCron = "0 15 3 * * *";

    private Executor executor = new Executor();
    private Remote remote = new Remote();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getArtifactDirectory() { return artifactDirectory; }
    public void setArtifactDirectory(String artifactDirectory) { this.artifactDirectory = artifactDirectory; }
    public String getLogDirectory() { return logDirectory; }
    public void setLogDirectory(String logDirectory) { this.logDirectory = logDirectory; }
    public int getRetentionDays() { return retentionDays; }
    public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
    public int getMaxRowsPerJob() { return maxRowsPerJob; }
    public void setMaxRowsPerJob(int maxRowsPerJob) { this.maxRowsPerJob = maxRowsPerJob; }
    public long getMaxArtifactBytes() { return maxArtifactBytes; }
    public void setMaxArtifactBytes(long maxArtifactBytes) { this.maxArtifactBytes = maxArtifactBytes; }
    public int getMaxConcurrentJobsPerAccount() { return maxConcurrentJobsPerAccount; }
    public void setMaxConcurrentJobsPerAccount(int v) { this.maxConcurrentJobsPerAccount = v; }
    public long getJobTimeoutMs() { return jobTimeoutMs; }
    public void setJobTimeoutMs(long jobTimeoutMs) { this.jobTimeoutMs = jobTimeoutMs; }
    public int getStaleJobReapMinutes() { return staleJobReapMinutes; }
    public void setStaleJobReapMinutes(int staleJobReapMinutes) { this.staleJobReapMinutes = staleJobReapMinutes; }
    public String getReaperCron() { return reaperCron; }
    public void setReaperCron(String reaperCron) { this.reaperCron = reaperCron; }
    public String getRetentionCron() { return retentionCron; }
    public void setRetentionCron(String retentionCron) { this.retentionCron = retentionCron; }
    public Executor getExecutor() { return executor; }
    public void setExecutor(Executor executor) { this.executor = executor; }
    public Remote getRemote() { return remote; }
    public void setRemote(Remote remote) { this.remote = remote; }

    public static class Executor {
        private int corePoolSize = 2;
        private int maxPoolSize = 4;
        private int queueCapacity = 50;
        private String threadNamePrefix = "ent-data-";

        public int getCorePoolSize() { return corePoolSize; }
        public void setCorePoolSize(int v) { this.corePoolSize = v; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int v) { this.maxPoolSize = v; }
        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int v) { this.queueCapacity = v; }
        public String getThreadNamePrefix() { return threadNamePrefix; }
        public void setThreadNamePrefix(String v) { this.threadNamePrefix = v; }
    }

    public static class Remote {
        private List<String> allowedHosts = List.of();
        private int connectTimeoutMs = 5_000;
        private int readTimeoutMs = 30_000;
        private long maxResponseBytes = 26_214_400L;

        public List<String> getAllowedHosts() { return allowedHosts; }
        public void setAllowedHosts(List<String> v) { this.allowedHosts = v; }
        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int v) { this.connectTimeoutMs = v; }
        public int getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(int v) { this.readTimeoutMs = v; }
        public long getMaxResponseBytes() { return maxResponseBytes; }
        public void setMaxResponseBytes(long v) { this.maxResponseBytes = v; }
    }
}
