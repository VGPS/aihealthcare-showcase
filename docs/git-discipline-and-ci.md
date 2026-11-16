# Git Discipline + CI/CD for AIHealthcare

**Repo:** `git@github.com:VGPS/AIHealthcare.git` (public, source-available)
**Project root:** `C:\workspaces\SpringAIClaude\AIHealthcare`
**Created:** 2026-09-12

---

## Part 0 — Explain it like I'm 5

### The two ideas

**Git discipline = one toy per box.**
Right now you build things and put them in Git whenever. That works until something
breaks and you have to figure out *which* change broke it. If each commit holds
exactly one feature, finding the bad toy takes seconds instead of an afternoon.
`git revert` on a one-feature commit is surgery. `git revert` on a ten-feature
commit is amputation.

**CI/CD = a robot doorman.**
Right now *you* are the only thing standing between a broken build and `master`.
The robot doorman stands at the door to `master`. Every time you knock (open a pull
request), it takes your code into a fresh, empty computer, builds it from scratch,
runs all 2,964 of your tests, and then either says "come in" or "nope, test
`MarketDigestServiceDedupTest` failed." You cannot argue with it. You cannot
forget to ask it. That's the whole point.

### "Webhooks" — you already have them, you just don't have the file yet

A webhook is "when X happens on GitHub, poke something." You do **not** need to go
create a webhook by hand. GitHub Actions *is* the webhook, pre-wired. You drop a
file at `.github/workflows/ci.yml` that starts with:

> when someone pushes, or opens a pull request, run these steps

...and GitHub does the poking for you, for free, forever. That's it. That's the
whole "set up CI/CD with webhooks" step — one YAML file.

### The word you need: "status check"

When your workflow finishes, it reports a green ✅ or red ❌ back to the pull
request. That report is called a **status check**. The second idea — "broken stuff
just doesn't merge" — is one checkbox on GitHub's website that says *"this status
check must be green before the merge button turns on."* Files can't do that part;
only a human clicking in Settings can. That's Part 2 below.

### The new loop, in five lines

1. Start a branch for one feature.
2. Build the feature. Commit it. One feature, one commit (or a few tidy ones).
3. Push the branch.
4. Open a pull request. The robot builds and tests it.
5. Green → merge into `master`. Red → fix it on the branch. `master` is never broken.

---

## Part 1 — What I found in your project (read this before you start)

I inspected the repo. Good news first:

| Thing | State |
|---|---|
| Build tool | Maven, single `pom.xml`, Java 17, Spring Boot 3.4.5 |
| Tests | 361 test classes under `application/src/test/java` (README says 2,964 tests) |
| Secrets | `.env`, `alpacaKeys.txt`, `session_cookies.txt` are **untracked** — good |
| Tracked junk | None at root. The logs, `.dump` files, and `.exe` installers are all untracked — good |
| Commit messages | Already one-feature-shaped ("Replace raw cron input with preset dropdown...") — you're 70% there already |
| Existing CI | **None.** No `.github/` directory at all |

Now the four landmines. These are the reason a naive "add GitHub Actions" attempt
would go red on the first run and stay red.

### 🧨 Landmine 1 — Your Tailwind build is a Windows `.exe`

`pom.xml` runs `tools/tailwindcss.exe` during `generate-resources`. GitHub's build
machines are Linux. That binary cannot run there, so **every** CI build would die
before it compiled a single Java file.

**Fix (your choice):** add a Maven profile `ci` that skips the Tailwind step. CSS
isn't what your tests check, so CI compiles and tests Java only. Your local build
is untouched.

### 🧨 Landmine 2 — 48 of your tests need a real PostgreSQL

`application/src/test/resources/application.yml` points at
`jdbc:postgresql://localhost:5432/aihealthcaredb_test` (user `admin`, password
`1454`), deliberately — you chose real Postgres over H2 to catch dialect issues.
48 test classes use `@DataJpaTest` against it.

A blank GitHub runner has no database, so those 48 would fail.

**Fix:** the workflow starts a throwaway Postgres container next to the build with
the exact same database name, user, and password. It lives for 4 minutes and dies.
Use the `pgvector/pgvector:pg16` image (plain Postgres plus the `vector`
extension) so nothing surprises you later.

### 🧨 Landmine 3 — Your remote is not called `origin`

```
AIHealthcare_Origin  git@github.com:VGPS/AIHealthcare.git
showcase             git@github.com:VGPS/aihealthcare-showcase.git
```

Every tutorial (and every AI, including Claude Code on a bad day) will type
`git push origin`. That will fail, or worse, land somewhere you didn't mean.
**Always `git push AIHealthcare_Origin <branch>`.** Prompt 3 below writes this into
`CLAUDE.md` so Claude Code stops guessing.

### 🧨 Landmine 4 — You commit straight to `master`, and there are no other branches

`git branch -a` shows only `master`. Pull requests are the thing the robot doorman
inspects, and you can't open a pull request from `master` into `master`. So the
habit change is: **never commit on `master` again.** Always branch first.

### Two small notes, not blockers

- `application/src/test/resources/application.yml` has a committed DB password
  (`1454`) in a **public** repo. It's a localhost dev credential so the blast radius
  is your own machine — but if that password is reused anywhere real, change it.
- 1,337 of your 2,813 tracked files are generated Javadoc. That's a lot of diff
  noise per commit. Consider gitignoring `javadoc/` and regenerating it on demand.
  Not urgent, and not part of this setup.

---

## Part 2 — Instructions for Claude Code (copy-paste, in order)

Run these in Claude Code inside IntelliJ, from the project root. **One prompt at a
time — verify each before moving on.** That's the same discipline we're installing.

### Prompt 1 — Make the build CI-safe

```text
I'm adding GitHub Actions CI to this project. Before the workflow can work, the
Maven build has to be runnable on a Linux machine.

Problem: pom.xml runs tools/tailwindcss.exe via exec-maven-plugin during
generate-resources. That's a Windows binary and it will fail on GitHub's Ubuntu
runners.

Do this:
1. Add a property <tailwind.skip>false</tailwind.skip> to the <properties> block.
2. On the exec-maven-plugin "tailwind-build" execution, add
   <skip>${tailwind.skip}</skip> inside its <configuration>.
3. Add a Maven profile with <id>ci</id> that sets <tailwind.skip>true</tailwind.skip>.
4. Do NOT change anything else in pom.xml.

Then verify both paths still work:
- `mvn -Pci help:active-profiles` shows the ci profile active
- `mvn -Pci generate-resources` completes WITHOUT invoking tailwindcss.exe
- `mvn generate-resources` (no -P flag) DOES still invoke tailwindcss.exe

Report what you changed and the output of both commands.
```

### Prompt 2 — Create the CI workflow

```text
Create .github/workflows/ci.yml for this project. Requirements:

Triggers: pull requests targeting master, and pushes to master.
Runner: ubuntu-latest.
Java: 17, Temurin distribution, with Maven dependency caching.

Service container — the tests need a real PostgreSQL matching
application/src/test/resources/application.yml exactly:
  image: pgvector/pgvector:pg16
  database: aihealthcaredb_test
  user: admin
  password: 1454
  port 5432 mapped to the host
  with a pg_isready health check so the build waits for it

Steps:
1. Checkout (actions/checkout@v7)
2. Set up Java 17 (actions/setup-java@v5, cache: maven)
3. Enable the pgvector extension on the test database via psql
4. Run: mvn -B -ntp -Pci verify
5. Always upload application/target/surefire-reports as a build artifact,
   even when the tests fail, so I can read what broke

Name the job exactly "Compile + Unit Tests" — I need that exact string when I
configure branch protection on GitHub.

Do not add coverage gates, dependency scanning, linting, or deployment steps.
Tests and compilation only, on purpose. Do not add a deploy job.

Show me the finished file and explain each block in one sentence so I understand
what I'm committing.
```

### Prompt 3 — Write the rules down so you (and Claude) stop guessing

```text
Add a "Git and CI discipline" section to CLAUDE.md so every future session follows
these rules. Content:

- NEVER commit directly to master. Always create a feature branch first.
- Branch naming: feat/<short-name>, fix/<short-name>, slice/<slice-id>,
  chore/<short-name>, docs/<short-name>
- One feature per commit. If a change does two things, make two commits.
- Commit message format: imperative subject under 72 chars, describing the one
  thing this commit does. Body only when the "why" isn't obvious.
- The git remote is named AIHealthcare_Origin, NOT origin.
  Push with: git push AIHealthcare_Origin <branch>
  (The "showcase" remote is a different repository — never push feature work there.)
- master is protected. Work reaches it only through a pull request whose
  "Compile + Unit Tests" check is green.
- Before pushing, run: mvn -Pci verify  (or the /test-run command)
- Never commit .env, *.key, *.pem, dump files, or logs.

Also create a pull request template at .github/pull_request_template.md with:
- What this changes (one sentence)
- Which slice / feature it belongs to
- How I verified it (test names or manual steps)
- A checklist: [ ] one feature only  [ ] tests pass locally  [ ] no secrets added

Keep both files concise. Show me the diff.
```

### Prompt 4 — A slash command so the loop is one keystroke

```text
Create a Claude Code slash command at .claude/commands/ship-slice.md that walks
the feature-branch workflow end to end. It should:

1. Confirm the working tree is clean and I'm on master, then pull the latest
   master from AIHealthcare_Origin.
2. Ask me for a branch name and the one-sentence description of the single
   feature, then create the branch.
3. After I've made the change, stage only the files belonging to that feature
   (show me the list and ask before staging anything unexpected), and commit with
   a message in our house format.
4. Run `mvn -Pci verify` and refuse to continue if it fails.
5. Push the branch to AIHealthcare_Origin and print the GitHub URL for opening
   the pull request.
6. Remind me not to merge until the "Compile + Unit Tests" check is green.

Follow the style of the existing commands in .claude/commands/.
```

### Prompt 5 — First real commit, using the new rules on itself

```text
Commit the CI setup work using the discipline we just wrote down.

Create a branch chore/add-ci-pipeline, then make SEPARATE commits:
  1. pom.xml — add ci profile that skips the Windows Tailwind binary
  2. .github/workflows/ci.yml — add CI: compile and test on pull requests
  3. .github/pull_request_template.md + CLAUDE.md — document git and CI discipline
  4. .claude/commands/ship-slice.md — add /ship-slice command

Then push to AIHealthcare_Origin and give me the pull request URL.

This first pull request is also the test of the pipeline itself: I expect the
workflow to run and I expect it might fail on the first try. If it does, we fix it
on this branch before merging.
```

---

## Part 3 — The GitHub website part (only you can do this)

Claude Code can write files. It cannot click "require this check." Three minutes of
clicking, once.

### 3a. Let the workflow run once first

Branch protection can only require a check it has *seen before*. So push the
`chore/add-ci-pipeline` branch and open the pull request **first**, and let the
workflow finish (green or red, doesn't matter — it just has to have run). Then come
back here.

### 3b. Turn on the doorman

1. Go to **https://github.com/VGPS/AIHealthcare/settings/rules**
   (or: repo → **Settings** → **Rules** → **Rulesets**)
2. **New ruleset** → **New branch ruleset**
3. **Ruleset Name:** `protect-master`
4. **Enforcement status:** **Active**
5. **Bypass list:** leave it **empty**.
   ⚠️ You are the repo admin. If you add yourself here, you can merge red builds,
   and the doorman becomes a decoration. Leave it empty.
6. **Target branches** → **Add target** → **Include default branch**
7. Under **Rules**, check these four:

   | Check this | What it means in plain words |
   |---|---|
   | ☑ **Require a pull request before merging** | No more commits landing straight on `master`. Set **Required approvals: 0** — you're solo, and 0 approvals still forces the pull request |
   | ☑ **Require status checks to pass** | Then click **Add checks**, type `Compile + Unit Tests`, and select it. **This is the "broken stuff doesn't merge" rule.** |
   | ☑ **Require branches to be up to date before merging** | (Sub-option of the above.) Stops "it passed on my branch but master moved" surprises |
   | ☑ **Block force pushes** | Nobody, including you at 11pm, rewrites `master`'s history |

8. **Create**

### 3c. Prove it works

On your pull request, the merge button should now read something like *"Required
statuses must pass before merging"* and be greyed out until the check is green.
If you want to be certain, temporarily break a test on the branch, push, and watch
the merge button refuse you. Then fix it. That's the moment the discipline becomes
real instead of theoretical.

### 3d. One optional nicety

Settings → **General** → **Pull Requests** → check **Automatically delete head
branches**. Keeps your branch list from turning into a graveyard.

---

## Part 4 — IntelliJ setup and the daily loop

### 4a. One-time settings

**Settings → Version Control → Git**

- **Update method: Rebase** — keeps history a straight line instead of a merge
  spaghetti. (Your pull requests will be much easier to read in the course.)
- ☑ **Auto-update after push/rejection** — off is fine; rebase covers it.

**Settings → Version Control → GitHub**

- **Add account** → log in with your token or browser. Without this, IntelliJ can't
  show you pull requests or CI status.

**Settings → Version Control → Commit**

- ☑ **Use non-modal commit interface** (if not already) — gives you the commit panel
  where you can pick individual files, which is exactly what "one feature per
  commit" requires.
- Leave **Run tests** and **Analyze code** *unchecked* at commit time. With 2,964
  tests, a commit-time test run will make you hate this whole system by Wednesday.
  Run tests before *pushing*, not before every commit.

### 4b. The daily loop in IntelliJ

| Step | How | Shortcut |
|---|---|---|
| 1. Get latest `master` | Git widget (bottom-right) → `master` → **Checkout**, then **Update Project** | `Ctrl+T` |
| 2. New branch for ONE feature | Git widget → **New Branch** → `feat/reversal-watch-ui` | `Ctrl+Shift+\`` |
| 3. Build the feature | (Claude Code, or by hand) | |
| 4. Run tests | Terminal: `mvn -Pci verify` — or your `/test-run` command | |
| 5. Commit | Commit panel — **tick only this feature's files**, write an imperative subject | `Ctrl+K` |
| 6. Push | Push dialog → confirm remote reads **AIHealthcare_Origin** | `Ctrl+Shift+K` |
| 7. Open pull request | **Git → GitHub → Create Pull Request**, or the **Pull Requests** tab in the Git tool window | `Alt+9` |
| 8. Watch the robot | The Pull Requests tab shows the check running, then ✅ or ❌ | |
| 9. Merge when green | Merge in IntelliJ's pull request view, or on GitHub. Then checkout `master`, Update Project | |

### 4c. When the robot says no

Don't fight it, and don't merge around it.

1. Open the pull request's **Checks** tab → click the failed step → read the Maven
   output. The failing test name is right there.
2. Fix it on the **same branch**. Commit. Push. The workflow reruns itself
   automatically — that's the webhook doing its thing.
3. If the failure looks like an infrastructure problem rather than your code
   (database didn't start, dependency download timed out), hand the log to Claude
   Code:

```text
My GitHub Actions workflow .github/workflows/ci.yml failed. Here's the log:

[paste the failing step's output]

Diagnose whether this is a problem with my code or with the workflow
configuration, and fix the right one. Don't disable or skip tests to make it pass.
```

That last sentence matters. The fastest way to a green build is always to delete
the test, and that is exactly the vibe discipline you're replacing.

---

## Part 5 — What "one feature at a time" actually means for your slices

Your slices (ED-1, ED-2, the LLM Wiki work) are bigger than one commit. That's
fine — the rule isn't "one commit per slice," it's **one idea per commit**.

A good ED-1 branch might look like:

```
slice/ed-1-enterprise-pull
├─ Add DataExportRequest and DataJobStatus domain records
├─ Add EnterpriseDataPullPort and PullEnterpriseDataUseCase
├─ Implement AsyncDataPullService with job status transitions
├─ Add JPA adapter and schema for data_export_request
├─ Add console page for triggering free-text and preset pulls
└─ Add 14 unit tests for pull job lifecycle
```

Six commits, one pull request, one green check, one merge. Each line is a sentence
someone could revert on its own. Compare that to `git commit -am "ED-1 work"` and
ask yourself which one you'd rather debug in November.

**Rule of thumb:** if your commit message needs the word "and," it's two commits.

---

## Part 6 — Later upgrades (don't do these yet)

Get green and boring first. Then, in roughly this order:

1. **Maven wrapper** — `mvn -N wrapper:wrapper`, commit `mvnw`/`mvnw.cmd`/`.mvn/`,
   and switch CI to `./mvnw`. Now your machine and the robot use the *same* Maven
   version. Real reproducibility.
2. **Dependabot** — a one-file `.github/dependabot.yml` that opens pull requests for
   outdated dependencies. Your CI tests them for you before you accept.
3. **Secret scanning + push protection** — Settings → Code security. Free on public
   repos. Stops an API key from ever reaching a commit.
4. **JaCoCo coverage floor** — fails the build under, say, 70%. Powerful, and also
   the fastest way to make yourself miserable if you turn it on too early.
5. **CD (the second D)** — a deploy job that pushes to your EC2/Lightsail instance
   only after a merge to `master` goes green. You already have `deploy/` and
   `run.sh`, so this is mostly wiring plus GitHub Secrets for the SSH key.

---

## Appendix A — Target `.github/workflows/ci.yml`

Claude Code should produce something close to this. Use it to check its work.

```yaml
name: CI

on:
  pull_request:
    branches: [ master ]
  push:
    branches: [ master ]

jobs:
  build-and-test:
    name: Compile + Unit Tests
    runs-on: ubuntu-latest

    services:
      postgres:
        image: pgvector/pgvector:pg16
        env:
          POSTGRES_DB: aihealthcaredb_test
          POSTGRES_USER: admin
          POSTGRES_PASSWORD: "1454"
        ports:
          - 5432:5432
        options: >-
          --health-cmd "pg_isready -U admin -d aihealthcaredb_test"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 10

    steps:
      - name: Check out the code
        uses: actions/checkout@v7

      - name: Set up Java 17
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '17'
          cache: maven

      - name: Enable pgvector extension
        env:
          PGPASSWORD: "1454"
        run: |
          psql -h localhost -U admin -d aihealthcaredb_test \
               -c "CREATE EXTENSION IF NOT EXISTS vector;"

      - name: Compile and run tests
        run: mvn -B -ntp -Pci verify

      - name: Upload test reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: surefire-reports
          path: application/target/surefire-reports/
          if-no-files-found: warn
```

Line by line:

- `on:` — the webhook. Pull requests into `master`, and pushes to `master`.
- `services: postgres:` — the throwaway database, created and destroyed per run.
- `options: --health-cmd` — makes the build *wait* until Postgres answers, instead
  of racing it and failing randomly.
- `cache: maven` — remembers your `~/.m2` between runs, so build two onward is
  minutes faster.
- `-Pci` — activates the profile that skips `tailwindcss.exe`.
- `-B -ntp` — batch mode, no transfer progress. Readable logs instead of 4,000
  lines of download bars.
- `if: always()` — upload the test reports *especially* when the build failed. That's
  when you need them.

## Appendix B — Target `pom.xml` changes

```xml
<properties>
    <!-- ... existing properties ... -->
    <tailwind.skip>false</tailwind.skip>
</properties>
```

On the existing Tailwind execution:

```xml
<execution>
    <id>tailwind-build</id>
    <phase>generate-resources</phase>
    <goals><goal>exec</goal></goals>
    <configuration>
        <skip>${tailwind.skip}</skip>
        <executable>${project.basedir}/tools/tailwindcss.exe</executable>
        <!-- ... existing arguments unchanged ... -->
    </configuration>
</execution>
```

And a new profile alongside the existing `spring-milestones` / `spring-snapshots`
repository config:

```xml
<profiles>
    <profile>
        <id>ci</id>
        <properties>
            <!-- tools/tailwindcss.exe is a Windows binary; CI runs on Linux -->
            <tailwind.skip>true</tailwind.skip>
        </properties>
    </profile>
</profiles>
```

## Appendix C — Commands you'll actually type

```bash
# start a feature
git checkout master
git pull AIHealthcare_Origin master
git checkout -b feat/reversal-watch-ui

# ... build one feature ...

# check it the way CI will
mvn -B -ntp -Pci verify

# commit just this feature
git add <only the files for this feature>
git commit -m "Add Reversal Watch panel to newsletter preview"

# push and open the pull request
git push AIHealthcare_Origin feat/reversal-watch-ui
# -> GitHub prints the pull request URL in the push output

# after the check is green and you've merged
git checkout master
git pull AIHealthcare_Origin master
git branch -d feat/reversal-watch-ui
```

---

*Setup notes for AIHealthcare. Everything in Part 2 is meant to be pasted into
Claude Code verbatim, one prompt at a time.*
