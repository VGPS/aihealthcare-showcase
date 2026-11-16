# MCP-1: Spring AI as an MCP Client (+ the Claude/IntelliJ Bridge, Honestly Assessed)

**Created:** 2026-09-28
**Updated:** 2026-09-28 — added Finding 3: the JetBrains MCP plugin gap has a real fix
(a self-built JDWP bridge), proven live in this session, not just theorized.
**Status:** PLANNED
**Learning goal:** Understand the *client* half of MCP — a Spring AI agent consuming another
server's tools, not just exposing its own — and get a factual (not hoped-for) answer to
"how does Claude interact with IntelliJ."

---

## Context

Two things existed before this slice:

1. **SOC-1** — `SocialPostAgentAdapter` is an MCP-*shaped* agent but not actually MCP: its
   `@Tool` methods are local Java methods, registered directly with `ChatClient` via
   `.tools(this)`. No protocol, no process boundary.
2. **The pulse server** (`tools/aihealthcare-pulse-server.mjs`) is a real MCP *server* —
   stdio JSON-RPC, tools discoverable via `tools/list`, callable via `tools/call`. Claude
   Desktop is its only client today.

What was missing, and what this session's two questions were really asking:

- **Is there a client on the Java side?** No — nothing in this codebase has ever consumed
  an MCP server. `SocialPostAgentAdapter` calls its own methods directly; it has never
  spoken the protocol to anything external.
- **Does Claude Code (this CLI session) have any channel into IntelliJ?** No — today it
  reads/writes the same filesystem and runs the same shell commands IntelliJ also watches.
  There is no live connection, no way to see breakpoint hits, no way to inspect a paused
  stack frame.

Both are answered below with static evidence from this machine, not assumption.

---

## Finding 1 — What the JetBrains MCP Server plugin actually gives Claude

IntelliJ IDEA 2026.1 Ultimate bundles a plugin (`com.intellij.mcpServer`, "MCP Server") that
turns IDE actions into MCP tools any client can call — including Claude Code, if configured
to connect to it (`Settings → Tools → MCP Server` exposes a local port; add it to Claude
Code's MCP config the same way you'd add any other server).

Inspected directly from `mcpserver.jar`'s class listing (not from documentation, which
oversells this — see below). Nine toolsets are registered:

| Toolset | What it actually exposes |
|---|---|
| `ExecutionToolset` | list run configurations, `execute_run_configuration` (run one, capture output/exit code) |
| `AnalysisToolset` | `get_file_problems`, `build_project`, module/dependency info |
| `FileToolset` | list/open files, directory tree, create file |
| `ReadToolset` | read file contents |
| `TextToolset` | `get_file_text_by_path`, `replace_text_in_file`, `search_in_files` |
| `SearchToolset` | `search_symbol` — IntelliJ-index-backed, not text grep |
| `CodeInsightToolset` | `get_symbol_info` |
| `FormattingToolset` | `reformat_file` |
| `RefactoringToolset` | `rename_refactoring` |
| `TerminalToolset` (optional) | `execute_terminal_command` — runs in IntelliJ's terminal |
| `VcsToolset` (optional) | VCS roots (Git4Idea) |

**The plugin's own description claims "Setting breakpoints" as a feature. It is not there.**
I grepped the full class listing for `Breakpoint`, `step`, `evaluate`, `StackFrame`,
`suspend` — zero matches in this build (`261.22158.277`). Whatever that description refers
to either shipped in a different build or hasn't shipped yet. **Do not plan around it.**

### What this means concretely

If you wire this up, Claude Code gains the ability to trigger a run configuration and read
back its output, ask IntelliJ for live compiler diagnostics instead of running `mvn compile`,
search by symbol instead of by grep, and a few other conveniences. **It does not gain the
ability to watch a live debugging session, inspect a paused frame, or single-step** — that
specific plugin doesn't expose it, full stop.

That is not, however, the end of the road for Claude-driven debugging — see **Finding 3**
below. The underlying capability (breakpoints, stepping, live variable inspection) is a
standard, JDK-shipped protocol (JDWP) sitting one layer below IntelliJ's own debugger UI.
IntelliJ's debugger is a client of that protocol; it is not the only possible one.

### Recommendation — enable it, but filtered

`ExecutionToolset` and `TerminalToolset` give an MCP client a second, less-visible path to
run arbitrary commands or launch run configurations. Claude Code already has Bash, with its
own permission prompts you already trust. A second, less-visible path to the same capability
is added risk without added capability. IntelliJ exposes a registry key for exactly this:

```
mcp.server.tools.filter = -*,+com.intellij.mcpserver.toolsets.general.*,-com.intellij.mcpserver.toolsets.general.ExecutionToolset.*,-com.intellij.mcpserver.toolsets.terminal.*
```

Set via `Settings → Tools → MCP Server` (there's a UI for this, the registry key is the
underlying mechanism) or `Help → Find Action → Registry`. Read-only toolsets only.

---

## Finding 2 — Spring AI *can* be an MCP client, and the dependency is already on disk

`~/.m2/repository/org/springframework/ai/` already contains `spring-ai-starter-mcp-client`,
`spring-ai-autoconfigure-mcp-client`, and `spring-ai-mcp` — resolved at some point, never
added to `pom.xml`. Confirmed at `spring-ai.version=1.0.0`, matching this project's pinned
version exactly (`pom.xml:22`), so there's no version-compatibility risk and no network
dependency to add this for real.

The mechanism: configure a stdio connection (same shape as `claude_desktop_config.json`
already uses for the pulse server), and Spring AI's autoconfiguration produces a
`SyncMcpToolCallbackProvider` bean — a `ToolCallback[]` built from whatever tools the target
MCP server advertises. Pass it to `ChatClient.prompt().toolCallbacks(...)`, the same seat
`.tools(this)` occupies in `SocialPostAgentAdapter`.

```yaml
spring:
  ai:
    mcp:
      client:
        stdio:
          connections:
            aihealthcare-pulse:
              command: node
              args: ["tools/aihealthcare-pulse-server.mjs"]
              env:
                AIHC_REPO: ${user.dir}
```

## Finding 3 — JDWP: a working, *proven* alternative to the missing breakpoint tool

IntelliJ's debugger, `jdb` (the JDK's own command-line debugger), and every other Java
debugging front end all speak the same wire protocol underneath: **JDWP** (Java Debug Wire
Protocol), accessed programmatically via **JDI** (`com.sun.jdi`), a first-class part of every
JDK. The JVM doesn't care who's on the other end of that socket. This was proven live in this
session, not asserted:

1. Launched a throwaway `Demo.java` with `-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=PORT`.
2. Attached `jdb -connect com.sun.jdi.SocketAttach:hostname=localhost,port=PORT` from Bash.
3. `stop at Demo:13` → `cont` → **real breakpoint hit**: `Breakpoint hit: "thread=main", Demo.accumulate(), line=13`.
4. `locals` → **real live values**: `running = 0`, `next = 1`, `result = 1`.
5. `step` → `cont` → breakpoint re-hit on the next loop iteration, confirming the full
   pause/inspect/resume cycle works end to end, driven entirely from outside the IDE.

### The pitfall, and why the fix matters

The first attempt used a `mkfifo`-created named pipe as `jdb`'s stdin, piped from Bash. That
**failed** — `java.io.IOException: The handle is invalid` — a known Windows/Git-Bash (MSYS)
incompatibility: a native Windows Java process can't reliably read a Win32-named-pipe-backed
FIFO the way a POSIX process would. Routing the same command stream through a real pipe
(`tail -f file | jdb ...`) worked cleanly. **This is a Bash/Windows artifact, not a JDWP
limitation** — worth recording precisely because it would otherwise look like the whole
approach is unreliable when only the shell-scripting mechanism was.

### The actual fix: don't shell-script it — wrap it in Node, the same way the pulse server does

Bash-piping `jdb` was the fastest way to *prove* JDWP is reachable, not something to depend
on. A Node.js child process avoids the FIFO problem entirely — `child_process.spawn('jdb',
[...]).stdin.write(...)` talks to a subprocess's real stdin stream directly, no pipe files,
no Windows quirk. This is exactly the architecture `tools/aihealthcare-pulse-server.mjs`
already uses (zero dependencies, stdio MCP, a Node child process under the hood) — a debugger
bridge is the same shape of program pointed at a JDWP port instead of `git`/`mvn`.

**Proposed component: `tools/aihealthcare-jdwp-bridge-server.mjs`** — a new local MCP server,
sibling to the pulse server, exposing the tools the JetBrains plugin doesn't:

| Tool | Wraps |
|---|---|
| `attach(port)` | spawn `jdb -connect com.sun.jdi.SocketAttach:...`, keep the child alive |
| `set_breakpoint(file, line)` | `stop at Class:line` |
| `continue()` | `cont` |
| `step_over()` / `step_into()` | `next` / `step` |
| `locals()` | `locals` — real variable values, not a template |
| `evaluate(expr)` | `print expr` |
| `stack_trace()` | `where` |
| `detach()` | clean process teardown |

Every call is a request/response over stdio — same MCP shape as the pulse server. **This does
not remove the fundamental request/response constraint** (§ Finding 1) — a tool call still
can't "watch" indefinitely — but it makes each individual inspection (breakpoint hit, read
locals, step, repeat) a real tool call instead of an impossible one. A Claude session (Code or
Desktop) could genuinely drive a debugging loop: call `set_breakpoint`, call `continue`, call
`locals`, decide what to do next, call `step` or `continue` again — turn by turn, exactly what
was just demonstrated by hand in Bash, but reliable and reusable instead of ad hoc.

---

## Decision — do not wire this into SocialPostAgentAdapter

The pulse server's four tools (`repo_status`, `build_status`, `app_health`,
`slice_progress`) answer *"what's the state of my own codebase and deployment"* — nothing
to do with market news or LinkedIn copy. Adding them to `SocialPostAgentAdapter`'s tool
surface would:

- Contradict §6 Principle 1 of `docs/ed-5-mcp-product-surface.md`, which this project
  already wrote and which explicitly warns against loading unrelated tools into one agent's
  context — a model choosing among more tools than it needs chooses worse, not better.
- Give the social-post agent zero actual use for those tools — it would never have a reason
  to call `build_status` while drafting a post, so the only effect is wasted context per
  conversation.

**Better fit: a new, isolated, non-deployed component** — the MCP-client equivalent of
what the pulse server already does on the server side (`docs/ed-5-mcp-product-surface.md`
§5: "Keep it out of `application/` — a separate small module or a `dev`-profile-only
`@Configuration` — so it can never be packaged into a deployed artifact"). Same discipline
applies here, symmetrically, on the client side.

---

## What You Will Learn

| Concept | Where it shows up |
|---|---|
| MCP client autoconfiguration | `spring-ai-starter-mcp-client` + `spring.ai.mcp.client.stdio.connections.*` |
| Tool callback adaptation | `SyncMcpToolCallbackProvider` turning an external server's advertised tools into Spring AI `ToolCallback[]` |
| Client vs. server MCP roles, side by side | This new component (client of the pulse server) vs. `SocialPostAgentAdapter` (client of its own local `@Tool`s) vs. `aihealthcare-pulse-server.mjs` (server) — three different roles in the same protocol, all in one repo |
| The real boundary of IDE↔LLM integration | What the JetBrains MCP Server plugin can and cannot do, verified by reading its actual classes rather than its marketing copy |
| JDWP/JDI as a debugging substrate | IntelliJ's debugger, `jdb`, and a self-built MCP bridge are all just clients of the same protocol — proven live via `jdb` attach, breakpoint, `locals`, `step`, `cont` |
| Process I/O portability | Why a Bash-piped named pipe (`mkfifo`) breaks Java stdin on Windows, and why spawning the same subprocess from Node sidesteps it entirely |

---

## Architecture

```
(new) McpClientDemoRunner            [dev-profile-only — NOT under application/'s deployed path]
  - Spring AI ChatClient + MCP-sourced ToolCallback[] (from aihealthcare-pulse-server.mjs)
  - Ask a natural-language question ("what's my repo's current test status?")
  - Claude autonomously calls repo_status / build_status / app_health / slice_progress
    over the SAME stdio protocol Claude Desktop already uses against this server
  - No controller, no persistence, no product surface — a learning harness only
```

---

## New Files

```
application/src/main/resources/application-mcp-demo.yml
  spring.ai.mcp.client.stdio.connections.aihealthcare-pulse.{command,args,env}
  Profile-gated (mcp-demo) — never active in prod/dev/aws profiles

infrastructure/mcp/McpClientDemoRunner.java   (package: infrastructure.mcp, new package)
  @Profile("mcp-demo")
  @Component implementing CommandLineRunner (or a plain manual-run smoke test, matching
  the existing ai-integration convention in NewsletterServiceTest — whichever reads more
  naturally once Step 1 is underway; decide in-flight, not worth pre-deciding here)
  - constructor: ChatClient.Builder, ToolCallbackProvider (autoconfigured from the MCP
    client starter)
  - run(): prompts Claude with a question that requires at least one pulse-server tool,
    prints the tool-call trace and final answer to stdout
```

No changes to `SocialPostAgentAdapter`, `AppConfig`'s existing beans, or any controller.

---

## Build Order

```
Step 1 — Add spring-ai-starter-mcp-client to pom.xml
  Already resolved in ~/.m2 at the correct version — offline-safe.
  Verify: mvn dependency:tree | grep mcp-client

Step 2 — application-mcp-demo.yml
  stdio connection pointing at tools/aihealthcare-pulse-server.mjs, AIHC_REPO env resolved
  from the working directory (not hardcoded, so it isn't machine-specific)

Step 3 — McpClientDemoRunner (main learning step)
  Wire ChatClient + autoconfigured ToolCallbackProvider
  Ask a question that can ONLY be answered via a tool call (e.g. "how many uncommitted
  files are in my repo right now, and what's the current branch?") — forces at least
  one round trip, makes the tool-call trace unambiguous in the logs

Step 4 — IntelliJ debug session (closes the loop with last session's setup)
  New run config: "Debug MCP Client (Spring Boot)", ACTIVE_PROFILES=mcp-demo
  Breakpoint inside McpClientDemoRunner.run(), step through the MCP handshake
  (initialize → tools/list → tools/call) the same way SOC-1's breakpoints watched the
  Spring AI tool-calling loop

Step 5 — (config-only, no code) Wire the IntelliJ MCP Server plugin into Claude Code
  Settings → Tools → MCP Server: note the local port
  Filter to read-only toolsets per Finding 1's recommendation
  Add to Claude Code's own MCP config
  Verify with one low-risk call (get_file_problems or search_symbol), not execute_run_configuration

Step 6 — Tests
  McpClientDemoRunnerTest — mock ToolCallbackProvider/ChatClient, verify the prompt is
  built correctly; this profile is never exercised by CI (same ai-integration-style
  exclusion as every other live-AI-call component in this project)

--- Track B: the JDWP bridge (Finding 3) — independent of Steps 1-6, can be built first ---

Step 7 — tools/aihealthcare-jdwp-bridge-server.mjs
  Same shape as the pulse server: zero deps, stdio MCP, spawn a child process (jdb),
  write commands to child.stdin directly (no FIFO, no pipe files — the Windows fix)
  Tools: attach, set_breakpoint, continue, step_over, step_into, locals, evaluate,
  stack_trace, detach

Step 8 — Prove it against Demo.java first, not the real app
  Same throwaway target this session used — faster iteration, no Postgres/Spring Boot
  startup cost while debugging the bridge itself

Step 9 — Point it at AiHealthcareApplication
  Launch via the "Debug Agent (Spring Boot)" run config's underlying JVM (or a plain
  `java -agentlib:jdwp=...` launch matching that config's env), attach the bridge,
  set a breakpoint in SocialPostAgentAdapter, drive the same session SOC-1's plan
  described by hand — but now as reusable MCP tool calls

Step 10 — Register it (Claude Desktop and/or Claude Code)
  Same registration shape as the pulse server in claude_desktop_config.json
  Only one debugger can attach to a given JDWP port at a time — if IntelliJ's own
  debugger is attached to a run, the bridge attaches to a DIFFERENT launch (or vice
  versa), never both to the same JVM simultaneously
```

---

## What NOT to Do

- Do not add the pulse server's tools to `SocialPostAgentAdapter` — wrong domain, dilutes
  an already-shipped agent's tool surface for no benefit (see Decision above).
- Do not enable `ExecutionToolset` or `TerminalToolset` for Claude Code's IntelliJ
  connection — redundant with Bash, strictly more risk.
- Do not build the ED-5c remote/OAuth enterprise MCP surface as part of this slice — still
  gated on ED-1/ED-2 usage data per `docs/ed-5-mcp-product-surface.md` §11, unrelated to
  what's being learned here.
- Do not expect breakpoint-level debugger control from the JetBrains MCP plugin specifically
  — verified absent in that build. Use the JDWP bridge (Finding 3 / Step 7) for that instead.
- Do not package `McpClientDemoRunner` or `application-mcp-demo.yml` into a deployed
  profile (`dev`, `prod`, `aws`) — this is a learning harness, not a product feature.
- Do not drive `jdb` via Bash-piped named pipes (`mkfifo`) on Windows — proven broken
  (`IOException: The handle is invalid`). Use a real subprocess with direct stdin writes
  (Node's `child_process`, or an anonymous pipe via `tail -f realfile | jdb`) instead.
- Do not attach two debuggers (IntelliJ's and the JDWP bridge) to the same JVM/port at
  once — JDWP accepts one client connection per target.

---

## Test Verification

```bash
mvn dependency:tree | grep mcp-client        # confirm Step 1
mvn test -Dtest="McpClientDemoRunnerTest"    # confirm Step 6, no live AI or MCP calls
```

Manual (not CI): run with `-Dspring-boot.run.profiles=mcp-demo`, confirm the pulse
server's tool-call trace appears in DEBUG logs, same as SOC-1's verification step.

Track B manual verification (already proven once by hand this session, repeat via the
built bridge to confirm the wrapped version matches):
```
node tools/aihealthcare-jdwp-bridge-server.mjs --selftest-against Demo.java
```
Expect: breakpoint hit, real `locals` values, step, re-hit on next loop iteration —
same sequence already demonstrated live in Finding 3.

---

## Definition of Done

**Track A — Spring AI MCP client:**
- [ ] `spring-ai-starter-mcp-client` resolves offline from the local `.m2` cache
- [ ] `McpClientDemoRunner` successfully calls at least one pulse-server tool through a
      real MCP stdio round trip (not mocked) at least once, observed in DEBUG logs
- [ ] IntelliJ debug session steps through the MCP handshake live (Step 4)
- [ ] `McpClientDemoRunnerTest` passing, no live AI/MCP calls in the default test run
- [ ] IntelliJ MCP Server plugin connected to Claude Code (optional — Step 5 is config-only
      and can be skipped without blocking the rest of this slice)

**Track B — JDWP bridge:**
- [ ] `tools/aihealthcare-jdwp-bridge-server.mjs` attaches to a JVM, sets a breakpoint, hits
      it, reads real locals, steps, and resumes — via MCP tool calls, not raw Bash/jdb
- [ ] Proven first against throwaway `Demo.java` (fast iteration), then against
      `AiHealthcareApplication` with a breakpoint in `SocialPostAgentAdapter`
- [ ] No `mkfifo`/named-pipe usage anywhere in the implementation (Windows-broken, proven
      this session)
- [ ] Registered in `claude_desktop_config.json` (or Claude Code's MCP config) alongside
      the pulse server
- [ ] Plan status updated to COMPLETE
