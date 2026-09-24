# ED-UX-1 — Enterprise Console UX Fixes + Framework Data Clarity

> Source: toDo-9-24  
> Created: 2026-09-24  
> Status: READY TO IMPLEMENT

---

## Issues Addressed

### Group A — Enterprise Data `/enterprise/data`

| # | User report | Root cause identified |
|---|-------------|----------------------|
| A1 | 'How to Use' instructions missing | Template gap — no help section exists |
| A2 | Submit Job button UX needs overhaul | Label wrong, spinner is text-only, no in-page progress, no auto-download |
| A3 | File saved to wrong path; UUID filename; Claude Desktop opens | Relative `artifactDirectory`; `SignedDownloadController` uses `artifactPath` (UUID, no extension) as download filename |
| A4 | Schedule banner: "was auto-deactivated" — actual state unclear | Banner doesn't surface `lastStatus` (failure reason) from `DataPushScheduleResponse` |
| A5 | Job Log: "Job not found" when valid job is selected | HTMX polls with `null` jobId on init; `x-show` keeps element in DOM; fix: `x-if` + re-trigger HTMX on select change |

### Group B — Framework Analysis `/dashboard/frameworks`

| # | User report | Root cause identified |
|---|-------------|----------------------|
| B1 | Data freshness unclear | `analyzedAt` is shown but not labeled "Last analyzed" |
| B2 | "50 articles" badge is misleading | Template text `${count} + ' articles'` gives no context; should say "Based on N articles" |

---

## Root Cause Detail

### A3 — UUID filename / wrong save path / Claude Desktop

**Wrong save path (`C:\workspaces\SpringAIClaude`):**  
`EnterpriseDataProperties.artifactDirectory` defaults to `"data-exports"` — a **relative path**. JVM resolves it relative to the working directory at launch. When started from `C:\workspaces\SpringAIClaude\AIHealthcare`, artifacts land there. The `application.yml` for dev does not override this to an absolute path.

**UUID filename / Claude Desktop:**  
`SignedDownloadController.java` line 96: `String fileName = job.artifactPath()` — `artifactPath` is the raw job UUID (no extension). The `Content-Disposition` header becomes `attachment; filename="<UUID-without-extension>"`. Windows cannot identify the file type → applies its default handler for unknown types → Claude Desktop is registered and opens.  
(Note: the authenticated endpoint `GET /api/v1/enterprise/data/jobs/{jobId}/artifact` already adds the correct extension via `jobId + "." + format.toLowerCase()`. Only the signed-link push-delivery path is broken.)

### A5 — Job Log "Job not found"

**Bug 1 — HTMX polls on init with jobId="null":**  
`x-data` initializes `selectedJob: null`. The `:hx-get` Alpine binding evaluates immediately to `'/enterprise/data/jobs/null/log-tail'`. Because `x-show` only sets `display:none` (does not remove the element from DOM), HTMX's `every 3s` trigger fires against this URL → `readLog("null", …)` finds no job → throws `IllegalArgumentException` → template shows "Job not found."

**Bug 2 — HTMX doesn't re-poll with updated URL after Alpine change:**  
When the user selects a real job, Alpine updates `hx-get` on the element, but the running HTMX polling cycle continues to use the URL it had at last `htmx.process()` call. The new URL is only picked up after HTMX explicitly re-processes the element.

**Bug 3 — Misleading error message:**  
Both "job truly not found" and "job exists but no log file" produce "Job not found." — unhelpful.

---

## Files Changed

| File | Reason |
|------|--------|
| `application/src/main/resources/templates/enterprise-data-console.html` | A1, A2, A4, A5 |
| `application/src/main/java/…/web/controller/SignedDownloadController.java` | A3 — filename with extension |
| `application/src/main/java/…/web/controller/EnterpriseDataConsoleController.java` | A5 — error message distinction |
| `application/src/main/resources/application.yml` | A3 — absolute artifact-directory for local dev |
| `application/src/main/resources/templates/framework-analysis.html` | B1, B2 |

---

## Implementation Steps

### Step 1 — Fix SignedDownloadController filename (A3)

**File:** `SignedDownloadController.java`

Replace line 96 (`String fileName = job.artifactPath();`):

```java
String dateStr = job.submittedAt() != null
    ? DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(job.submittedAt())
    : "export";
String ext = (job.format() != null)
    ? job.format().name().toLowerCase()
    : "csv";
String fileName = "data-" + job.feedId() + "-" + dateStr + "." + ext;
```

Result: download shows `data-ai-synthesis-2026-09-24.csv` instead of bare UUID.  
Windows identifies `.csv` → Excel opens it (not Claude Desktop).

---

### Step 2 — Fix artifact directory for local dev (A3)

**File:** `application/src/main/resources/application.yml`

Under `aihealthcare.enterprise.data`:
```yaml
aihealthcare:
  enterprise:
    data:
      artifact-directory: ${user.home}/aihealthcare/data-exports
      log-directory: ${user.home}/aihealthcare/data-exports/logs
```

Artifacts land in `C:\Users\Administrator\aihealthcare\data-exports` on dev, not the project directory.

Also verify the EC2 profile (`application-aws.yml`) already sets `/var/lib/aihealthcare/data-exports`; add if missing.

---

### Step 3 — Fix Job Log HTMX/Alpine bug (A5)

**File:** `enterprise-data-console.html` — Log tab section (lines 461–491)

Replace with an Alpine `x-if` + HTMX `htmx.process()` pattern:

```html
<!-- ═══ Log Tab ═══ -->
<div x-show="tab === 'log'" x-cloak>
    <div class="bg-white rounded-lg shadow-card p-6">
        <h2 class="text-lg font-semibold text-gray-800 mb-4">Job Log</h2>
        <div th:if="${#lists.isEmpty(jobs)}" class="text-sm text-gray-500">
            No jobs yet. Submit a job from the Run tab to see its log output here.
        </div>
        <div th:unless="${#lists.isEmpty(jobs)}">
            <div class="mb-4">
                <label class="block text-sm font-medium text-gray-700 mb-1">Select Job</label>
                <select x-model="selectedJob"
                        @change="selectedJob && $nextTick(() => htmx.process(document.getElementById('log-tail-container')))"
                        class="w-full rounded-md border-gray-300 shadow-sm text-sm px-3 py-2 border">
                    <option value="">Choose a job...</option>
                    <option th:each="job : ${jobs}"
                            th:value="${job.jobId()}"
                            th:text="${job.jobId() + ' — ' + job.feedId() + ' (' + job.status() + ')'}">
                    </option>
                </select>
            </div>
            <!-- x-if removes element from DOM when no job selected; HTMX never polls a null jobId -->
            <template x-if="selectedJob">
                <div id="log-tail-container"
                     :hx-get="'/enterprise/data/jobs/' + selectedJob + '/log-tail'"
                     hx-trigger="load, every 5s"
                     hx-swap="innerHTML">
                    <p class="text-sm text-gray-400">Loading log...</p>
                </div>
            </template>
        </div>
    </div>
</div>
```

Key changes:
- `<template x-if="selectedJob">` — completely removes element from DOM when false; HTMX cannot poll
- `@change` calls `htmx.process()` after Alpine renders the new element, ensuring HTMX uses the current URL
- `hx-trigger="load, every 5s"` — fires immediately on DOM insertion (`load`) then polls every 5s

---

### Step 4 — Improve log-tail error message (A5)

**File:** `EnterpriseDataConsoleController.java` — `logTail()` catch block (lines 102–106)

Distinguish "no log file" from "job not found":

```java
catch (IllegalArgumentException e) {
    String msg = (e.getMessage() != null && e.getMessage().toLowerCase().contains("log"))
            ? "No log file available for this job."
            : "Job not found.";
    model.addAttribute("logContent", msg);
    model.addAttribute("jobId", jobId);
    model.addAttribute("nextOffset", from);
}
```

Verify what message `RequestEnterpriseDataUseCase.readLog()` throws in each case and adjust the condition to match.

---

### Step 5 — Submit Job: relabel + SVG spinner (A2a, A2b, A2c)

**File:** `enterprise-data-console.html` — Run tab form

a. Change label (line ~80): `"Query (optional)"` → `"Prompt"`  
b. Change placeholder (line ~82): `"Describe what data you need..."` → `"Describe the data you want, or leave blank to fetch all records."`  
c. Replace button content:

```html
<button type="submit" :disabled="submitting || !jobForm.feedId"
        class="inline-flex items-center gap-2 bg-primary-600 text-white px-6 py-2 rounded-lg text-sm font-medium hover:bg-primary-700 transition-colors shadow-sm disabled:opacity-50">
    <svg x-show="submitting" x-cloak
         class="animate-spin h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
    </svg>
    <span x-show="!submitting">Submit Job</span>
    <span x-show="submitting" x-cloak>Running...</span>
</button>
```

---

### Step 6 — Submit Job: in-page progress + auto-download (A2d, A2e — "Rethink the process")

**Current flow:** Submit → 202 → redirect to Jobs tab → user manually watches + manually clicks download.

**New flow:** Submit → stay on Run tab → inline progress card → browser Save As dialog appears automatically when SUCCEEDED.

**Why this answers "Ask user where to save the file":** The `Content-Disposition: attachment` header on the artifact endpoint triggers the browser's native Save As dialog. No server-side path is involved.

Extend `jobFormManager()` in the `<script>` block:

```javascript
function jobFormManager() {
    return {
        jobForm: { feedId: '', promptText: '', format: 'CSV', rowLimit: 100 },
        submitting: false,
        jobError: '',
        activeJobId: null,
        activeJobStatus: null,
        pollHandle: null,

        async submitJob() {
            this.submitting = true;
            this.jobError = '';
            this.activeJobId = null;
            this.activeJobStatus = null;
            try {
                const resp = await fetch('/api/v1/enterprise/data/jobs', {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: { 'Content-Type': 'application/json' },
                    redirect: 'error',
                    body: JSON.stringify(this.jobForm)
                });
                if (resp.ok || resp.status === 201 || resp.status === 202) {
                    const job = await resp.json();
                    this.activeJobId = job.jobId;
                    this.activeJobStatus = job.status || 'QUEUED';
                    this.jobForm = { feedId: '', promptText: '', format: 'CSV', rowLimit: 100 };
                    this.startPolling();
                } else {
                    const text = await resp.text();
                    this.jobError = 'Error ' + resp.status + ': ' + (text || resp.statusText);
                }
            } catch (e) {
                this.jobError = 'Submit failed — session may have expired. Try refreshing.';
            } finally {
                this.submitting = false;
            }
        },

        startPolling() {
            this.stopPolling();
            this.pollHandle = setInterval(async () => {
                if (!this.activeJobId) { this.stopPolling(); return; }
                try {
                    const resp = await fetch('/api/v1/enterprise/data/jobs/' + this.activeJobId,
                        { credentials: 'same-origin' });
                    if (!resp.ok) return;
                    const job = await resp.json();
                    this.activeJobStatus = job.status;
                    if (job.status === 'SUCCEEDED') {
                        this.stopPolling();
                        this.triggerDownload(this.activeJobId);
                    } else if (job.status === 'FAILED' || job.status === 'CANCELLED') {
                        this.stopPolling();
                        this.jobError = 'Job ' + job.status.toLowerCase()
                            + (job.errorMessage ? ': ' + job.errorMessage : '');
                    }
                } catch (e) { /* ignore transient poll errors */ }
            }, 4000);
        },

        stopPolling() {
            if (this.pollHandle) { clearInterval(this.pollHandle); this.pollHandle = null; }
        },

        triggerDownload(jobId) {
            // Content-Disposition: attachment on the endpoint triggers browser Save As
            window.location.href = '/api/v1/enterprise/data/jobs/' + jobId + '/artifact';
        }
    };
}
```

Add a progress card below the form button row:

```html
<!-- In-page job progress (shown while job is running) -->
<template x-if="activeJobId">
    <div class="mt-4 bg-blue-50 border border-blue-200 rounded-lg p-4">
        <div class="flex items-center gap-3">
            <svg x-show="activeJobStatus === 'QUEUED' || activeJobStatus === 'RUNNING'"
                 class="animate-spin h-5 w-5 text-blue-600 shrink-0"
                 xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
            </svg>
            <span class="text-sm font-medium text-blue-800"
                  x-text="activeJobStatus === 'SUCCEEDED' ? 'Complete — download starting...' :
                           activeJobStatus === 'RUNNING'   ? 'Running...' :
                           activeJobStatus === 'FAILED'    ? 'Job failed' : 'Queued...'"></span>
        </div>
        <p class="text-xs text-blue-600 mt-1 font-mono" x-text="'Job ID: ' + activeJobId"></p>
        <p class="text-xs text-blue-500 mt-0.5">
            Your browser will prompt you to save the file when complete.
        </p>
    </div>
</template>
```

---

### Step 7 — How to Use instructions (A1)

**File:** `enterprise-data-console.html` — add at top of Run tab (inside `x-show="tab === 'run'"`, before the form card)

```html
<!-- How to Use -->
<div x-data="{ open: false }" class="bg-blue-50 border border-blue-200 rounded-lg p-4 mb-6">
    <button @click="open = !open"
            class="flex items-center gap-2 text-sm font-medium text-blue-700 w-full text-left">
        <svg :class="open ? 'rotate-90' : ''" class="h-4 w-4 transition-transform"
             fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"></path>
        </svg>
        How to Use the Enterprise Data Console
    </button>
    <div x-show="open" x-cloak class="mt-3 text-sm text-blue-800 space-y-2">
        <p><strong>1. Select a Data Feed</strong> — choose what type of data to export
           (Articles, Market Digest, Regulatory Events, etc.).</p>
        <p><strong>2. Add a Prompt (optional)</strong> — describe the specific data you want.
           For AI Synthesis feeds this shapes the analysis. Leave blank to fetch all available records.</p>
        <p><strong>3. Choose format and row limit</strong> — CSV opens in Excel/Sheets;
           JSON is for programmatic use. Row limit controls how many records are returned (max 50,000).</p>
        <p><strong>4. Click Submit Job</strong> — the job runs in the background. When complete your
           browser will automatically prompt you to save the file. The job also appears in the
           <em>Jobs</em> tab for reference.</p>
        <p class="text-xs text-blue-600 mt-2">Artifacts are retained for 14 days. Use the
           <em>Schedules</em> tab to set up recurring automated exports delivered to your inbox.</p>
    </div>
</div>
```

---

### Step 8 — Schedule deactivation banner: add context (A4)

**File:** `enterprise-data-console.html` — Schedules tab (lines 160–171)

First verify `DataPushScheduleResponse` exposes `lastStatus` and `lastRunAt` — if missing, add them from `DataPushSchedule.lastStatus()` and `DataPushSchedule.lastRunAt()`.

Replace the banner `<template>` block:

```html
<template x-for="s in deactivated" :key="s.scheduleId">
    <div class="bg-amber-50 border border-amber-300 rounded-lg p-4 mb-4">
        <div class="flex items-start justify-between gap-4">
            <div>
                <p class="font-medium text-amber-800"
                   x-text="s.label + ' was auto-deactivated after consecutive failures.'"></p>
                <p class="text-sm text-amber-700 mt-0.5" x-show="s.lastStatus">
                    Last failure reason: <span class="font-mono text-xs bg-amber-100 px-1 rounded"
                                               x-text="s.lastStatus"></span>
                </p>
                <p class="text-xs text-amber-600 mt-0.5" x-show="s.lastRunAt">
                    Last attempted: <span x-text="s.lastRunAt ? new Date(s.lastRunAt).toLocaleString() : ''"></span>
                </p>
                <p class="text-xs text-amber-500 mt-1 italic">
                    Investigate the failure in the Job Log tab before reactivating.
                </p>
            </div>
            <button @click="reactivate(s.scheduleId)"
                    class="shrink-0 bg-amber-600 text-white text-xs px-3 py-1.5 rounded hover:bg-amber-700 transition-colors">
                Reactivate
            </button>
        </div>
    </div>
</template>
```

Also update the `init()` method to pass through the new fields:
```javascript
init() {
    const schedules = /*[[${schedules}]]*/ [];
    this.deactivated = schedules.filter(s => !s.active && s.consecutiveFailures > 0);
},
```
(No change needed here if `schedules` already includes `lastStatus` and `lastRunAt`.)

---

### Step 9 — Framework data clarity (B1, B2)

**File:** `framework-analysis.html`

**B2 — article count chip** (line 111):
```html
<!-- Before -->
th:text="${analysis.articleCount()} + ' articles'"

<!-- After -->
th:text="'Based on ' + ${analysis.articleCount()} + ' articles'"
```

**B1 — "Last analyzed" label** (line ~62):
```html
<!-- Before -->
<div class="text-xs text-gray-400" th:text="${analyzedDates[analysis.companySlug()]}">Aug 3, 2026</div>

<!-- After -->
<div class="text-xs text-gray-400">
    Last analyzed: <span th:text="${analyzedDates[analysis.companySlug()]}">Aug 3, 2026</span>
</div>
```

---

## Tests

| Test class | New test(s) |
|------------|-------------|
| `EnterpriseDataConsoleControllerTest` | `logTail_noLogFile_returnsNoLogMessage` — mock `readLog()` to throw with "log" in message, assert model contains "No log file available" not "Job not found" |
| `SignedDownloadControllerTest` | `download_succeededJob_contentDispositionIncludesExtension` — assert filename ends with `.csv` not bare UUID |

Run: `mvn test -Dtest="EnterpriseDataConsoleControllerTest,SignedDownloadControllerTest"`

---

## Open Questions (not in scope for this slice)

1. **"AI Brief" schedule failure cause — investigate before reactivating.** Do NOT click Reactivate until the failure reason is known. Check EC2 logs:
   ```bash
   journalctl -u aihealthcare --since "2026-09-18" | grep -E "scheduleId|PUSH_FAIL|handleFailure|Auto-deactivated"
   ```
   Likely causes: SES sandbox rejecting email to unverified recipients; LLM API error in the AI Synthesis feed; expired API key. Diagnose first.

2. **Framework article listing** — "Activate Link and display all 50 articles?" is a new feature requiring a REST query + new template. `FrameworkAnalysisService` tracks `articleCount` but does not persist which specific articles were used. Defer to a separate slice.

3. **Framework analysis freshness cadence** — analysis runs at startup or on manual trigger. A weekly scheduled re-analysis would keep data current automatically. Defer to a cron slice.

---

## Verification Checklist

After deploying to EC2:

- [ ] `/enterprise/data` Run tab shows collapsible "How to Use" section
- [ ] Textarea label says "Prompt" not "Query (optional)"
- [ ] Submit Job shows SVG spinner + "Running..." text
- [ ] Progress card appears below form with job ID and status
- [ ] When job SUCCEEDS, browser Save As dialog appears automatically
- [ ] Downloaded file named `data-{feedId}-{date}.csv`, not UUID
- [ ] Downloaded `.csv` opens in Excel, not Claude Desktop
- [ ] Artifacts stored in `~/aihealthcare/data-exports` on dev (not project root)
- [ ] Log tab: selecting a job loads log content without "Job not found" error
- [ ] Log tab: jobs with no log show "No log file available for this job."
- [ ] Schedule deactivation banner shows failure reason + last-attempted time
- [ ] `/dashboard/frameworks` chips say "Based on N articles"
- [ ] Framework cards show "Last analyzed: [date]"
