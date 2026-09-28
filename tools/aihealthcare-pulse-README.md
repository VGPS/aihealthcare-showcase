# aihealthcare-pulse

A local MCP server that reports AIHealthcare's working state. Built to back a live
Claude Artifact dashboard, but useful on its own — Claude Code can call it too.

Zero dependencies, single file, stdio JSON-RPC. Node 18+.

- Server: `tools/aihealthcare-pulse-server.mjs`
- Registered in: `%APPDATA%\Claude\claude_desktop_config.json`

---

## Why it exists

A published Claude Artifact cannot read your machine. It can only call
claude.ai connectors, or an MCP server running locally, reached through the
desktop app as `host:<name>`. There is no GitHub connector in the registry, and
an artifact is blocked from making arbitrary network calls — so a local MCP
server is the only route to a dashboard that reads this repo live.

This is also a working instance of the pattern in
`docs/ed-5-mcp-product-surface.md`: an MCP server as a product surface. Here it
points inward at the repo instead of outward at customers, but the shape is the
same — a small, read-only tool surface over data that already exists.

---

## Configuration

Two environment variables, set in the config block:

| Variable | Required | Meaning |
| --- | --- | --- |
| `AIHC_REPO` | yes | Absolute path to the repo root |
| `AIHC_APP_URL` | no | URL to probe (default `https://app.bigskylabs.ai`) |

```json
{
  "mcpServers": {
    "aihealthcare-pulse": {
      "command": "node",
      "args": ["C:\\workspaces\\SpringAIClaude\\AIHealthcare\\tools\\aihealthcare-pulse-server.mjs"],
      "env": {
        "AIHC_REPO": "C:\\workspaces\\SpringAIClaude\\AIHealthcare",
        "AIHC_APP_URL": "https://app.bigskylabs.ai"
      }
    }
  }
}
```

Restart the desktop app after editing. Backslashes are JSON-escaped — a single
backslash silently invalidates the file and every MCP server disappears at once.

---

## The four tools

All are annotated `readOnlyHint: true`, which is what allows an artifact to
*watch* them (cached, auto-refreshing) rather than poll. All take no arguments.

### `repo_status`
Branch, ahead/behind upstream, uncommitted files (first 25), commits today,
commits this week, last commit, and the last 8 commits.

### `build_status`
Walks `target/` in the repo root and in each module, parses
`surefire-reports/TEST-*.xml` and `failsafe-reports`, and sums
`tests` / `failures` / `errors` / `skipped`. Reports `verdict` as
`green`, `failing`, or `unknown` (no reports found), plus when the tests last
ran and when `target/` last changed.

Reads the *last* build. It does not run Maven.

### `app_health`
HTTP GET against `AIHC_APP_URL`, 8-second timeout. Returns status code,
`ok`, latency, and on failure a `reason`. Never throws — an unreachable app is
data, not an error.

### `slice_progress`
Spec documents in `docs/` (name, first `#` heading, size, last modified, newest
first), plus per-layer Java file counts.

Layer detection descends the single-child package chain to where the code
actually branches — for this repo, `com.wgblackmon.aihealthcare` — then counts
`.java` under each child. Source roots are found by locating `src/*/main/java`
directly, **not** by looking for `pom.xml`: in this repo the sources live under
`application/src` with no pom of their own, beside the parent pom at the root.

---

## Verifying it works

Without any MCP client, from the repo root:

```bash
node tools/aihealthcare-pulse-server.mjs --selftest
```

Prints what all four tools return, as JSON. If this works, the server is fine
and any remaining problem is configuration.

To check the MCP handshake itself:

```bash
printf '%s\n%s\n' \
 '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{}}}' \
 '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' \
 | node tools/aihealthcare-pulse-server.mjs
```

Two JSON lines back means the protocol layer is healthy.

---

## When it doesn't appear

Symptoms are identical whichever cause it is, so work down the list.

1. **`node` not on PATH.** The config runs bare `node`, resolved against the
   Windows PATH — not the desktop app's bundled Node. If `node --version`
   doesn't answer in a fresh terminal, replace `"command": "node"` with the
   absolute path to `node.exe`. This is the most likely cause.
2. **Malformed JSON in the config.** A single unescaped backslash drops the
   whole file, and *every* MCP server vanishes, not just this one. Paste the
   file into a JSON validator.
3. **App not restarted.** The server list is read at startup.
4. **`AIHC_REPO` wrong or unset.** The server starts fine and every tool
   returns an error string explaining what's missing — so tools appearing but
   erroring points here, not at the config.

---

## Extending it

Add an entry to the `TOOLS` array: `name`, `description`, `inputSchema`,
`annotations`, and an async `handler` returning a plain object. Everything
else — protocol, framing, error wrapping, selftest — picks it up automatically.

Keep handlers read-only. A tool annotated `readOnlyHint: false` cannot be
watched by an artifact, only called explicitly.

Candidates worth considering: open TODO counts from the `toDo-*.txt` files,
`docs/Steps.md` checklist progress, EC2 deploy timestamp, or a pgvector row
count for the harvested-article corpus.
