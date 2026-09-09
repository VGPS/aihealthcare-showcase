package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.*;
import com.wgblackmon.aihealthcare.domain.port.inbound.RequestEnterpriseDataUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.*;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Domain service orchestrating enterprise data PULL jobs.
 *
 * <p>Implements the 8-step submit flow: feed resolution, tier check, quota
 * check, concurrency check, row-limit clamp, plan resolution (canned prompt
 * / free text / raw parameters), job persistence, and async dispatch.
 *
 * <p>Every denial is audited before the exception is thrown. Every read
 * method resolves ownership in the query — another owner's data is invisible,
 * not forbidden.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
@Slf4j
public class EnterpriseDataService implements RequestEnterpriseDataUseCase {

    private final Map<String, EnterpriseDataSourcePort> feedRegistry;
    private final DataJobPort dataJobPort;
    private final DataJobLogPort dataJobLogPort;
    private final CannedPromptPort cannedPromptPort;
    private final PromptToQueryPort promptToQueryPort;
    private final DataAccessAuditPort auditPort;
    private final AppUserPort appUserPort;
    private final UsageTrackingPort usageTrackingPort;
    private final Consumer<DataRequest> jobDispatcher;
    private final Clock clock;
    private final int maxConcurrentJobsPerAccount;
    private final int maxRowsPerJob;
    private final int retentionDays;

    public EnterpriseDataService(List<EnterpriseDataSourcePort> sources,
                                 DataJobPort dataJobPort,
                                 DataJobLogPort dataJobLogPort,
                                 CannedPromptPort cannedPromptPort,
                                 PromptToQueryPort promptToQueryPort,
                                 DataAccessAuditPort auditPort,
                                 AppUserPort appUserPort,
                                 UsageTrackingPort usageTrackingPort,
                                 Consumer<DataRequest> jobDispatcher,
                                 Clock clock,
                                 int maxConcurrentJobsPerAccount,
                                 int maxRowsPerJob,
                                 int retentionDays) {
        log.debug("EnterpriseDataService() | sources={}, maxConcurrent={}, maxRows={}, retention={}",
                sources.size(), maxConcurrentJobsPerAccount, maxRowsPerJob, retentionDays);
        this.feedRegistry = sources.stream()
                .collect(Collectors.toMap(EnterpriseDataSourcePort::feedId, Function.identity()));
        this.dataJobPort = dataJobPort;
        this.dataJobLogPort = dataJobLogPort;
        this.cannedPromptPort = cannedPromptPort;
        this.promptToQueryPort = promptToQueryPort;
        this.auditPort = auditPort;
        this.appUserPort = appUserPort;
        this.usageTrackingPort = usageTrackingPort;
        this.jobDispatcher = jobDispatcher;
        this.clock = clock;
        this.maxConcurrentJobsPerAccount = maxConcurrentJobsPerAccount;
        this.maxRowsPerJob = maxRowsPerJob;
        this.retentionDays = retentionDays;
    }

    @Override
    public DataJob submit(DataRequest request) {
        log.debug("submit() | jobId={}, ownerEmail=[REDACTED], feedId={}, promptId={}, format={}",
                request.jobId(), request.feedId(), request.promptId(), request.format());

        // Step 1: Resolve feed
        EnterpriseDataSourcePort source = feedRegistry.get(request.feedId());
        if (source == null) {
            throw new IllegalArgumentException("Unknown feed: " + request.feedId());
        }
        DataFeed feed = source.describe();

        // Step 2: Tier check — ENTERPRISE required
        AppUser user = appUserPort.findByEmail(request.ownerEmail())
                .orElseThrow(() -> new IllegalArgumentException("Unknown user: " + request.ownerEmail()));
        SubscriptionTier userTier = user.tier();
        if (userTier == null || userTier.ordinal() < SubscriptionTier.ENTERPRISE.ordinal()) {
            audit(request, DataAccessAction.TIER_DENY, "DENY",
                    "Tier " + userTier + " is below ENTERPRISE");
            throw new IllegalStateException("TIER_DENY: Enterprise tier required");
        }

        // Step 3: Monthly quota
        String yearMonth = YearMonth.now(clock).toString();
        UsageRecord usage = usageTrackingPort.getOrCreateUsage(request.ownerEmail(), yearMonth);
        if (!usage.hasRemaining()) {
            audit(request, DataAccessAction.QUOTA_DENY, "DENY",
                    "Monthly quota exceeded: " + usage.queryCount() + "/" + usage.queryLimit());
            throw new IllegalStateException("QUOTA_DENY: Monthly quota exceeded");
        }

        // Step 4: Concurrency check
        int activeJobs = dataJobPort.countActiveByOwnerEmail(request.ownerEmail());
        if (activeJobs >= maxConcurrentJobsPerAccount) {
            audit(request, DataAccessAction.CONCURRENCY_DENY, "DENY",
                    "Concurrent job limit: " + activeJobs + "/" + maxConcurrentJobsPerAccount);
            throw new IllegalStateException("CONCURRENCY_DENY: Too many active jobs");
        }

        // Step 5: Clamp rowLimit
        int clampedLimit = Math.min(request.rowLimit(),
                Math.min(feed.maxRowLimit(), maxRowsPerJob));

        // Step 6: Resolve plan
        DataQueryPlan plan = resolvePlan(request, feed, userTier, clampedLimit);

        // Step 7: Persist QUEUED job
        Instant now = Instant.now(clock);
        DataJob job = new DataJob(
                request.jobId(), request.ownerEmail(), request.teamId(),
                request.mode(), request.feedId(), request.promptId(),
                request.format(), DataJobStatus.QUEUED,
                null, null, null, null,
                request.jobId() + ".log",
                null, null,
                now, null, null, null,
                now.plus(Duration.ofDays(retentionDays)),
                null);
        DataJob saved = dataJobPort.save(job);

        audit(request, DataAccessAction.SUBMIT, "ALLOW",
                "feedId=" + request.feedId() + " format=" + request.format());

        usageTrackingPort.incrementAndGet(request.ownerEmail(), yearMonth);

        // Build resolved request with plan and clamped limit
        DataRequest resolved = new DataRequest(
                request.jobId(), request.ownerEmail(), request.teamId(),
                request.mode(), request.feedId(), request.promptId(), request.promptText(),
                request.parameters(), request.format(), clampedLimit,
                request.connectionId(), plan, request.requestedAt());

        // Step 8: Dispatch to async executor
        jobDispatcher.accept(resolved);

        log.debug("submit() | return={}", saved.jobId());
        return saved;
    }

    @Override
    public DataJob getJob(String jobId, String ownerEmail) {
        log.debug("getJob() | jobId={}, ownerEmail=[REDACTED]", jobId);
        DataJob result = dataJobPort.findByJobIdAndOwnerEmail(jobId, ownerEmail)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        log.debug("getJob() | return={}", result.jobId());
        return result;
    }

    @Override
    public List<DataJob> listJobs(String ownerEmail, int page, int size) {
        log.debug("listJobs() | ownerEmail=[REDACTED], page={}, size={}", page, size);
        List<DataJob> result = dataJobPort.findByOwnerEmail(ownerEmail, page, size);
        log.debug("listJobs() | return={} jobs", result.size());
        return result;
    }

    @Override
    public void cancel(String jobId, String ownerEmail) {
        log.debug("cancel() | jobId={}, ownerEmail=[REDACTED]", jobId);
        DataJob job = dataJobPort.findByJobIdAndOwnerEmail(jobId, ownerEmail)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        if (job.status().isTerminal()) {
            throw new IllegalStateException("Job is already terminal: " + job.status());
        }
        Instant now = Instant.now(clock);
        dataJobPort.updateStatus(jobId, DataJobStatus.CANCELLED,
                null, null, null, null, null, null, now);
        auditPort.append(new DataAccessAuditEntry(
                now, ownerEmail, jobId, null,
                DataAccessAction.CANCEL, "SUCCESS", "Job cancelled", null, null));
        log.debug("cancel() | return=void");
    }

    @Override
    public List<DataFeed> listFeeds(String ownerEmail) {
        log.debug("listFeeds() | ownerEmail=[REDACTED]");
        List<DataFeed> result = feedRegistry.values().stream()
                .map(EnterpriseDataSourcePort::describe)
                .filter(DataFeed::active)
                .toList();
        log.debug("listFeeds() | return={} feeds", result.size());
        return result;
    }

    @Override
    public List<CannedPrompt> listPrompts(String ownerEmail, String feedId) {
        log.debug("listPrompts() | ownerEmail=[REDACTED], feedId={}", feedId);
        List<CannedPrompt> result;
        if (feedId != null && !feedId.isBlank()) {
            result = cannedPromptPort.findByFeedId(feedId);
        } else {
            result = cannedPromptPort.findAllActive();
        }
        log.debug("listPrompts() | return={} prompts", result.size());
        return result;
    }

    @Override
    public String readLog(String jobId, String ownerEmail, long fromByteOffset) {
        log.debug("readLog() | jobId={}, ownerEmail=[REDACTED], offset={}", jobId, fromByteOffset);
        dataJobPort.findByJobIdAndOwnerEmail(jobId, ownerEmail)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        String result = dataJobLogPort.read(jobId, fromByteOffset);
        log.debug("readLog() | return={} chars", result != null ? result.length() : 0);
        return result;
    }

    // ── Plan resolution ─────────────────────────────────────────────────

    private DataQueryPlan resolvePlan(DataRequest request, DataFeed feed,
                                     SubscriptionTier userTier, int clampedLimit) {
        if (request.promptId() != null && !request.promptId().isBlank()) {
            return resolveFromCannedPrompt(request, feed, userTier, clampedLimit);
        }
        if (request.promptText() != null && !request.promptText().isBlank()) {
            return resolveFromFreeText(request, feed, clampedLimit);
        }
        return buildPlanFromParameters(request.parameters(), feed.feedId(), clampedLimit);
    }

    private DataQueryPlan resolveFromCannedPrompt(DataRequest request, DataFeed feed,
                                                  SubscriptionTier userTier, int clampedLimit) {
        CannedPrompt prompt = cannedPromptPort.findById(request.promptId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown prompt: " + request.promptId()));
        if (!prompt.feedId().equals(request.feedId())) {
            throw new IllegalArgumentException(
                    "Prompt " + request.promptId() + " targets feed "
                    + prompt.feedId() + ", not " + request.feedId());
        }
        if (userTier.ordinal() < prompt.minTier().ordinal()) {
            throw new IllegalStateException(
                    "TIER_DENY: Prompt requires " + prompt.minTier());
        }
        validateParameters(request.parameters(), prompt.parameters());
        return buildPlanFromParameters(request.parameters(), feed.feedId(), clampedLimit);
    }

    private DataQueryPlan resolveFromFreeText(DataRequest request, DataFeed feed,
                                              int clampedLimit) {
        if (promptToQueryPort == null) {
            throw new IllegalStateException(
                    "UNRESOLVABLE_PROMPT: Free-text resolution not available");
        }
        DataQueryPlan plan = promptToQueryPort.resolve(request.promptText(), feed);
        if (plan == null) {
            throw new IllegalStateException(
                    "UNRESOLVABLE_PROMPT: Could not resolve prompt to a query plan");
        }
        if (!feed.feedId().equals(plan.feedId())) {
            throw new IllegalStateException(
                    "UNRESOLVABLE_PROMPT: Plan references wrong feed: " + plan.feedId());
        }
        int safeLim = Math.min(plan.limit(), clampedLimit);
        return new DataQueryPlan(
                plan.feedId(), plan.keywords(), plan.dateFrom(), plan.dateTo(),
                plan.states(), plan.categories(), plan.sortBy(), safeLim);
    }

    private void validateParameters(Map<String, String> supplied, List<DataParameter> schema) {
        for (DataParameter param : schema) {
            String value = supplied.get(param.name());
            if (param.required() && (value == null || value.isBlank())) {
                throw new IllegalArgumentException(
                        "Required parameter missing: " + param.name());
            }
            if (value != null && !value.isBlank()) {
                switch (param.type()) {
                    case "INTEGER":
                        try {
                            Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException(
                                    "Parameter " + param.name() + " must be an integer");
                        }
                        break;
                    case "DATE":
                        try {
                            LocalDate.parse(value);
                        } catch (DateTimeParseException e) {
                            throw new IllegalArgumentException(
                                    "Parameter " + param.name() + " must be ISO date (yyyy-MM-dd)");
                        }
                        break;
                    case "ENUM":
                        if (!param.allowedValues().contains(value)) {
                            throw new IllegalArgumentException(
                                    "Parameter " + param.name() + " value '"
                                    + value + "' not in " + param.allowedValues());
                        }
                        break;
                    default:
                        break;
                }
            }
        }
    }

    private DataQueryPlan buildPlanFromParameters(Map<String, String> params,
                                                  String feedId, int limit) {
        return new DataQueryPlan(
                feedId,
                splitOrEmpty(params.get("keywords")),
                parseDate(params.get("dateFrom")),
                parseDate(params.get("dateTo")),
                splitOrEmpty(params.get("states")),
                splitOrEmpty(params.get("categories")),
                params.get("sortBy"),
                limit);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void audit(DataRequest request, DataAccessAction action,
                       String outcome, String detail) {
        auditPort.append(new DataAccessAuditEntry(
                Instant.now(clock), request.ownerEmail(), request.jobId(),
                null, action, outcome, detail, null, null));
    }

    private static List<String> splitOrEmpty(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return List.of(csv.split(","));
    }

    private static LocalDate parseDate(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        return LocalDate.parse(iso);
    }
}
