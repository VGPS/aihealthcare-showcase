package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.*;
import com.wgblackmon.aihealthcare.domain.port.outbound.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EnterpriseDataService}.
 *
 * <p>All outbound ports are mocked. A fixed {@link Clock} pins the time to
 * 2026-09-08T12:00:00Z so quota year-month and expiry calculations are
 * deterministic.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
class EnterpriseDataServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-09-08T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private static final String YEAR_MONTH = "2026-09";
    private static final String OWNER = "enterprise@test.com";

    private EnterpriseDataSourcePort articlesSource;
    private DataJobPort dataJobPort;
    private DataJobLogPort dataJobLogPort;
    private CannedPromptPort cannedPromptPort;
    private PromptToQueryPort promptToQueryPort;
    private DataAccessAuditPort auditPort;
    private AppUserPort appUserPort;
    private UsageTrackingPort usageTrackingPort;
    @SuppressWarnings("unchecked")
    private Consumer<DataRequest> dispatcher = mock(Consumer.class);

    private EnterpriseDataService service;

    @BeforeEach
    void setUp() {
        articlesSource = mock(EnterpriseDataSourcePort.class);
        when(articlesSource.feedId()).thenReturn("articles");
        when(articlesSource.describe()).thenReturn(new DataFeed(
                "articles", "Articles", "Article corpus",
                DataSourceKind.INTERNAL_CORPUS,
                List.of(ExportFormat.CSV, ExportFormat.JSON),
                List.of(new DataParameter("keyword", "Keyword", "STRING", false, null, List.of())),
                100, 10000, "NONE", true));

        dataJobPort = mock(DataJobPort.class);
        dataJobLogPort = mock(DataJobLogPort.class);
        cannedPromptPort = mock(CannedPromptPort.class);
        promptToQueryPort = mock(PromptToQueryPort.class);
        auditPort = mock(DataAccessAuditPort.class);
        appUserPort = mock(AppUserPort.class);
        usageTrackingPort = mock(UsageTrackingPort.class);

        when(dataJobPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service = new EnterpriseDataService(
                List.of(articlesSource),
                dataJobPort, dataJobLogPort, cannedPromptPort, promptToQueryPort,
                auditPort, appUserPort, usageTrackingPort,
                dispatcher, FIXED_CLOCK, 2, 50000, 14);
    }

    // ── Tier denial ─────────────────────────────────────────────────────

    @Test
    void tierDenialAuditsAndRejects() {
        stubUser(SubscriptionTier.SUBSCRIBER);

        assertThatThrownBy(() -> service.submit(makeRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TIER_DENY");

        verify(auditPort).append(argThat(e ->
                e.action() == DataAccessAction.TIER_DENY && "DENY".equals(e.outcome())));
        verify(dataJobPort, never()).save(any());
        verify(dispatcher, never()).accept(any());
    }

    @Test
    void freeTierDenied() {
        stubUser(SubscriptionTier.FREE);

        assertThatThrownBy(() -> service.submit(makeRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TIER_DENY");
    }

    @Test
    void demoTierDenied() {
        stubUser(SubscriptionTier.DEMO);

        assertThatThrownBy(() -> service.submit(makeRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TIER_DENY");
    }

    // ── Quota denial ────────────────────────────────────────────────────

    @Test
    void quotaDenialAuditsAndRejects() {
        stubUser(SubscriptionTier.ENTERPRISE);
        when(usageTrackingPort.getOrCreateUsage(OWNER, YEAR_MONTH))
                .thenReturn(new UsageRecord(OWNER, YEAR_MONTH, 2000, 2000));

        assertThatThrownBy(() -> service.submit(makeRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("QUOTA_DENY");

        verify(auditPort).append(argThat(e ->
                e.action() == DataAccessAction.QUOTA_DENY));
        verify(dataJobPort, never()).save(any());
    }

    // ── Concurrency denial ──────────────────────────────────────────────

    @Test
    void concurrencyDenialAuditsAndRejects() {
        stubUser(SubscriptionTier.ENTERPRISE);
        stubQuotaRemaining();
        when(dataJobPort.countActiveByOwnerEmail(OWNER)).thenReturn(2);

        assertThatThrownBy(() -> service.submit(makeRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONCURRENCY_DENY");

        verify(auditPort).append(argThat(e ->
                e.action() == DataAccessAction.CONCURRENCY_DENY));
    }

    // ── Unknown feed ────────────────────────────────────────────────────

    @Test
    void unknownFeedThrows() {
        DataRequest req = new DataRequest(
                "job-1", OWNER, null, DataJobMode.PULL, "nonexistent",
                null, null, Map.of(), ExportFormat.CSV, 100, null, null, FIXED_NOW);

        assertThatThrownBy(() -> service.submit(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown feed");
    }

    // ── Canned prompt ───────────────────────────────────────────────────

    @Test
    void cannedPromptMissingRequiredParam() {
        stubUser(SubscriptionTier.ENTERPRISE);
        stubQuotaRemaining();
        stubNoConcurrency();

        CannedPrompt prompt = new CannedPrompt(
                "p-1", "Test Prompt", "desc", "articles",
                "template", List.of(new DataParameter("state", "State", "STRING", true, null, List.of())),
                SubscriptionTier.ENTERPRISE, true);
        when(cannedPromptPort.findById("p-1")).thenReturn(Optional.of(prompt));

        DataRequest req = makeRequestWithPromptId("p-1", Map.of());

        assertThatThrownBy(() -> service.submit(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Required parameter");

        verifyNoInteractions(promptToQueryPort);
    }

    @Test
    void cannedPromptEnumOutOfRange() {
        stubUser(SubscriptionTier.ENTERPRISE);
        stubQuotaRemaining();
        stubNoConcurrency();

        CannedPrompt prompt = new CannedPrompt(
                "p-1", "Test Prompt", "desc", "articles",
                "template", List.of(new DataParameter("tier", "Tier", "ENUM", true, null,
                        List.of("ACADEMIC", "INDUSTRY"))),
                SubscriptionTier.ENTERPRISE, true);
        when(cannedPromptPort.findById("p-1")).thenReturn(Optional.of(prompt));

        DataRequest req = makeRequestWithPromptId("p-1", Map.of("tier", "INVALID"));

        assertThatThrownBy(() -> service.submit(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in");
    }

    @Test
    void cannedPromptValidBuildsWithoutLlm() {
        stubUser(SubscriptionTier.ENTERPRISE);
        stubQuotaRemaining();
        stubNoConcurrency();

        CannedPrompt prompt = new CannedPrompt(
                "p-1", "Test Prompt", "desc", "articles",
                "template", List.of(new DataParameter("keyword", "Keyword", "STRING", false, null, List.of())),
                SubscriptionTier.ENTERPRISE, true);
        when(cannedPromptPort.findById("p-1")).thenReturn(Optional.of(prompt));

        DataRequest req = makeRequestWithPromptId("p-1", Map.of("keyword", "AI healthcare"));

        DataJob result = service.submit(req);

        assertThat(result.status()).isEqualTo(DataJobStatus.QUEUED);
        verifyNoInteractions(promptToQueryPort);
        verify(dispatcher).accept(any());
    }

    // ── Free text ───────────────────────────────────────────────────────

    @Test
    void freeTextWrongFeedIdRejected() {
        stubUser(SubscriptionTier.ENTERPRISE);
        stubQuotaRemaining();
        stubNoConcurrency();

        DataQueryPlan badPlan = new DataQueryPlan("wrong-feed", List.of(), null, null, List.of(), List.of(), null, 100);
        when(promptToQueryPort.resolve(any(), any())).thenReturn(badPlan);

        DataRequest req = makeRequestWithPromptText("Find recent articles");

        assertThatThrownBy(() -> service.submit(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UNRESOLVABLE_PROMPT");
    }

    @Test
    void freeTextOverLimitClamped() {
        stubUser(SubscriptionTier.ENTERPRISE);
        stubQuotaRemaining();
        stubNoConcurrency();

        DataQueryPlan plan = new DataQueryPlan("articles", List.of("AI"), null, null, List.of(), List.of(), null, 999999);
        when(promptToQueryPort.resolve(any(), any())).thenReturn(plan);

        DataRequest req = makeRequestWithPromptText("Find articles");

        DataJob result = service.submit(req);

        assertThat(result).isNotNull();
        verify(dispatcher).accept(argThat(r -> r.rowLimit() <= 10000));
    }

    // ── Other owner → 404 ───────────────────────────────────────────────

    @Test
    void getJobOtherOwnerReturns404() {
        when(dataJobPort.findByJobIdAndOwnerEmail("job-1", OWNER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJob("job-1", OWNER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // ── Happy path ──────────────────────────────────────────────────────

    @Test
    void happyPathQueuesAuditsAndDispatches() {
        stubUser(SubscriptionTier.ENTERPRISE);
        stubQuotaRemaining();
        stubNoConcurrency();

        DataJob result = service.submit(makeRequest());

        assertThat(result.status()).isEqualTo(DataJobStatus.QUEUED);
        assertThat(result.ownerEmail()).isEqualTo(OWNER);
        assertThat(result.feedId()).isEqualTo("articles");

        verify(auditPort).append(argThat(e ->
                e.action() == DataAccessAction.SUBMIT && "ALLOW".equals(e.outcome())));
        verify(dispatcher, times(1)).accept(any(DataRequest.class));
        verify(usageTrackingPort).incrementAndGet(OWNER, YEAR_MONTH);
    }

    @Test
    void rowLimitClampedToFeedMax() {
        stubUser(SubscriptionTier.ENTERPRISE);
        stubQuotaRemaining();
        stubNoConcurrency();

        DataRequest bigReq = new DataRequest(
                "job-1", OWNER, null, DataJobMode.PULL, "articles",
                null, null, Map.of(), ExportFormat.CSV, 999999, null, null, FIXED_NOW);

        service.submit(bigReq);

        verify(dispatcher).accept(argThat(r -> r.rowLimit() <= 10000));
    }

    // ── Cancel ──────────────────────────────────────────────────────────

    @Test
    void cancelRunningJobSucceeds() {
        DataJob running = new DataJob(
                "job-1", OWNER, null, DataJobMode.PULL, "articles", null,
                ExportFormat.CSV, DataJobStatus.RUNNING,
                null, null, null, null, "job-1.log", null, null,
                FIXED_NOW, FIXED_NOW, null, null, null, null);
        when(dataJobPort.findByJobIdAndOwnerEmail("job-1", OWNER))
                .thenReturn(Optional.of(running));

        service.cancel("job-1", OWNER);

        verify(dataJobPort).updateStatus(eq("job-1"), eq(DataJobStatus.CANCELLED),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any());
        verify(auditPort).append(argThat(e ->
                e.action() == DataAccessAction.CANCEL));
    }

    @Test
    void cancelTerminalJobFails() {
        DataJob succeeded = new DataJob(
                "job-1", OWNER, null, DataJobMode.PULL, "articles", null,
                ExportFormat.CSV, DataJobStatus.SUCCEEDED,
                100, 5000L, "abc", "job-1.csv", "job-1.log", null, null,
                FIXED_NOW, FIXED_NOW, FIXED_NOW, null, null, null);
        when(dataJobPort.findByJobIdAndOwnerEmail("job-1", OWNER))
                .thenReturn(Optional.of(succeeded));

        assertThatThrownBy(() -> service.cancel("job-1", OWNER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal");
    }

    // ── List feeds ──────────────────────────────────────────────────────

    @Test
    void listFeedsReturnsActiveOnly() {
        List<DataFeed> result = service.listFeeds(OWNER);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).feedId()).isEqualTo("articles");
    }

    // ── Read log ────────────────────────────────────────────────────────

    @Test
    void readLogVerifiesOwnership() {
        when(dataJobPort.findByJobIdAndOwnerEmail("job-1", OWNER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.readLog("job-1", OWNER, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");

        verify(dataJobLogPort, never()).read(any(), anyLong());
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private void stubUser(SubscriptionTier tier) {
        when(appUserPort.findByEmail(OWNER))
                .thenReturn(Optional.of(new AppUser(
                        OWNER, "$2a$10$hash", "Test User", "USER", true, tier, null)));
    }

    private void stubQuotaRemaining() {
        when(usageTrackingPort.getOrCreateUsage(OWNER, YEAR_MONTH))
                .thenReturn(new UsageRecord(OWNER, YEAR_MONTH, 10, 2000));
    }

    private void stubNoConcurrency() {
        when(dataJobPort.countActiveByOwnerEmail(OWNER)).thenReturn(0);
    }

    private DataRequest makeRequest() {
        return new DataRequest(
                "job-1", OWNER, null, DataJobMode.PULL, "articles",
                null, null, Map.of(), ExportFormat.CSV, 100, null, null, FIXED_NOW);
    }

    private DataRequest makeRequestWithPromptId(String promptId, Map<String, String> params) {
        return new DataRequest(
                "job-1", OWNER, null, DataJobMode.PULL, "articles",
                promptId, null, params, ExportFormat.CSV, 100, null, null, FIXED_NOW);
    }

    private DataRequest makeRequestWithPromptText(String text) {
        return new DataRequest(
                "job-1", OWNER, null, DataJobMode.PULL, "articles",
                null, text, Map.of(), ExportFormat.CSV, 100, null, null, FIXED_NOW);
    }
}
