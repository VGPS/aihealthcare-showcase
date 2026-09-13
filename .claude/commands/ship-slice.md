# Ship Slice

Walk the feature-branch workflow end to end: branch, commit, test, push, PR.

## Steps — execute in order, stop on any failure

### 1. Ensure clean master

Confirm the working tree has no uncommitted changes that belong to the feature.
Checkout `master` and pull latest:

```bash
git checkout master
git pull AIHealthcare_Origin master
```

If there are uncommitted changes, ask the user what to do before proceeding.

### 2. Create the feature branch

Ask the user for:
- **Branch name** (use convention: `feat/`, `fix/`, `slice/`, `chore/`, `docs/`)
- **One-sentence description** of the single feature

Then create and switch to the branch:

```bash
git checkout -b <branch-name>
```

### 3. Stage the feature

After the user has made their changes, show `git status` and list the changed files.
Ask the user to confirm which files belong to this feature before staging.
Do NOT stage `.env`, `*.key`, `*.pem`, dump files, logs, or IDE files.

```bash
git add <only the feature files>
```

### 4. Commit

Write an imperative commit message under 72 chars describing the one thing this
commit does. Add a body only when the "why" isn't obvious.

```bash
git commit -m "<message>"
```

### 5. Run CI-equivalent tests

```bash
mvn -B -ntp -Pci verify
```

If tests fail, **STOP** and report the failures. Do not push a red build.

### 6. Push and print PR URL

Push to BOTH remotes:

```bash
git push AIHealthcare_Origin <branch-name>
git push showcase <branch-name>
```

Print the GitHub URL for opening the pull request on the primary repo.

### 7. Remind

Tell the user:
- Do not merge until the "Compile + Unit Tests" check is green
- After merge: `git checkout master && git pull AIHealthcare_Origin master && git branch -d <branch-name>`
