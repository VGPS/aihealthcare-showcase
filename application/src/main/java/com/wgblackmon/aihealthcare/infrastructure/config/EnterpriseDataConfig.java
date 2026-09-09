package com.wgblackmon.aihealthcare.infrastructure.config;

import com.wgblackmon.aihealthcare.domain.port.inbound.ManageRemoteConnectionsUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.RequestEnterpriseDataUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.*;
import com.wgblackmon.aihealthcare.domain.service.EnterpriseDataService;
import com.wgblackmon.aihealthcare.domain.service.RemoteConnectionService;
import com.wgblackmon.aihealthcare.infrastructure.enterprise.ConfinedFileStore;
import com.wgblackmon.aihealthcare.infrastructure.enterprise.EnterpriseDataJobRunner;
import com.wgblackmon.aihealthcare.infrastructure.enterprise.FileDataArtifactAdapter;
import com.wgblackmon.aihealthcare.infrastructure.enterprise.FileDataJobLogAdapter;
import com.wgblackmon.aihealthcare.infrastructure.enterprise.source.RemoteEndpointGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Configuration for the enterprise remote data access feature.
 *
 * <p>Creates the dedicated async executor and ensures the artifact and log
 * directories exist at startup.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08 — Inc 10: added Clock bean for reaper/retention schedulers
 */
@Slf4j
@Configuration
public class EnterpriseDataConfig {

    @Bean(name = "enterpriseDataExecutor")
    public Executor enterpriseDataExecutor(EnterpriseDataProperties props) {
        log.debug("enterpriseDataExecutor() | props={}", props.getClass().getSimpleName());
        var ex = props.getExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(ex.getCorePoolSize());
        executor.setMaxPoolSize(ex.getMaxPoolSize());
        executor.setQueueCapacity(ex.getQueueCapacity());
        executor.setThreadNamePrefix(ex.getThreadNamePrefix());
        executor.initialize();
        log.info("enterpriseDataExecutor() | core={}, max={}, queue={}, prefix={}",
                ex.getCorePoolSize(), ex.getMaxPoolSize(), ex.getQueueCapacity(),
                ex.getThreadNamePrefix());
        log.debug("enterpriseDataExecutor() | return={}", executor);
        return executor;
    }

    @Bean
    public DataArtifactPort dataArtifactPort(EnterpriseDataProperties props) {
        log.debug("dataArtifactPort()");
        Path dir = ensureDirectory(props.getArtifactDirectory());
        ConfinedFileStore store = new ConfinedFileStore(dir);
        DataArtifactPort result = new FileDataArtifactAdapter(
                store, props.getMaxArtifactBytes(), props.getRetentionDays());
        log.debug("dataArtifactPort() | return={}", result.getClass().getSimpleName());
        return result;
    }

    @Bean
    public RemoteEndpointGuard remoteEndpointGuard(EnterpriseDataProperties props) {
        log.debug("remoteEndpointGuard()");
        var remote = props.getRemote();
        RemoteEndpointGuard result = new RemoteEndpointGuard(
                remote.getAllowedHosts(),
                remote.getConnectTimeoutMs(),
                remote.getReadTimeoutMs(),
                remote.getMaxResponseBytes());
        log.debug("remoteEndpointGuard() | return={}", result.getClass().getSimpleName());
        return result;
    }

    @Bean
    public DataJobLogPort dataJobLogPort(EnterpriseDataProperties props) {
        log.debug("dataJobLogPort()");
        Path dir = ensureDirectory(props.getLogDirectory());
        ConfinedFileStore store = new ConfinedFileStore(dir);
        DataJobLogPort result = new FileDataJobLogAdapter(store);
        log.debug("dataJobLogPort() | return={}", result.getClass().getSimpleName());
        return result;
    }

    @Bean
    public EnterpriseDataJobRunner enterpriseDataJobRunner(List<EnterpriseDataSourcePort> sources,
                                                           DataJobPort dataJobPort,
                                                           DataJobLogPort dataJobLogPort,
                                                           DataArtifactPort dataArtifactPort,
                                                           DataAccessAuditPort auditPort,
                                                           EnterpriseDataProperties props) {
        log.debug("enterpriseDataJobRunner()");
        EnterpriseDataJobRunner result = new EnterpriseDataJobRunner(
                sources, dataJobPort, dataJobLogPort, dataArtifactPort, auditPort,
                props.getJobTimeoutMs());
        log.debug("enterpriseDataJobRunner() | return={}", result.getClass().getSimpleName());
        return result;
    }

    @Bean
    public RequestEnterpriseDataUseCase enterpriseDataService(
            List<EnterpriseDataSourcePort> sources,
            DataJobPort dataJobPort,
            DataJobLogPort dataJobLogPort,
            CannedPromptPort cannedPromptPort,
            @Nullable @Autowired(required = false) PromptToQueryPort promptToQueryPort,
            DataAccessAuditPort auditPort,
            AppUserPort appUserPort,
            UsageTrackingPort usageTrackingPort,
            EnterpriseDataJobRunner runner,
            EnterpriseDataProperties props,
            Clock clock) {
        log.debug("enterpriseDataService()");
        RequestEnterpriseDataUseCase result = new EnterpriseDataService(
                sources, dataJobPort, dataJobLogPort, cannedPromptPort, promptToQueryPort,
                auditPort, appUserPort, usageTrackingPort,
                runner::execute,
                clock,
                props.getMaxConcurrentJobsPerAccount(),
                props.getMaxRowsPerJob(),
                props.getRetentionDays());
        log.debug("enterpriseDataService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    @Bean
    public ManageRemoteConnectionsUseCase remoteConnectionService(RemoteConnectionPort connectionPort) {
        log.debug("remoteConnectionService()");
        ManageRemoteConnectionsUseCase result = new RemoteConnectionService(connectionPort);
        log.debug("remoteConnectionService() | return={}", result.getClass().getSimpleName());
        return result;
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock enterpriseClock() {
        log.debug("enterpriseClock() | return=Clock.systemUTC()");
        return Clock.systemUTC();
    }

    private Path ensureDirectory(String relativePath) {
        try {
            Path dir = Path.of(relativePath).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create directory: " + relativePath, e);
        }
    }
}
