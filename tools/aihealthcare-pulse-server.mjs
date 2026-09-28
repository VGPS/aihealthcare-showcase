#!/usr/bin/env node
/**
 * aihealthcare-pulse — a local MCP server exposing AIHealthcare's working state.
 *
 * Four read-only tools, consumed by a published Claude Artifact via `host:`:
 *   repo_status     branch, working-tree dirt, commit cadence
 *   build_status    last Maven build + surefire test totals
 *   app_health      liveness of the deployed app
 *   slice_progress  spec docs in docs/ and module weight in src/
 *
 * Zero dependencies. Speaks MCP over stdio (newline-delimited JSON-RPC 2.0).
 *
 * Configuration, by environment variable:
 *   AIHC_REPO     absolute path to the AIHealthcare repo root   (required)
 *   AIHC_APP_URL  URL to probe for liveness                     (default https://app.bigskylabs.ai)
 *
 * Run `node aihealthcare-pulse-server.mjs --selftest` to print what each tool
 * returns, without any MCP client involved.
 */

import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { readFile, readdir, stat } from "node:fs/promises";
import path from "node:path";

const exec = promisify(execFile);

const REPO = process.env.AIHC_REPO || "";
const APP_URL = process.env.AIHC_APP_URL || "https://app.bigskylabs.ai";
const PROTOCOL_FALLBACK = "2024-11-05";

/* ------------------------------------------------------------------ */
/* helpers                                                             */
/* ------------------------------------------------------------------ */

async function git(args) {
  const { stdout } = await exec("git", args, {
    cwd: REPO,
    maxBuffer: 8 * 1024 * 1024,
    windowsHide: true,
  });
  return stdout.trim();
}

async function gitSafe(args, fallback = "") {
  try {
    return await git(args);
  } catch {
    return fallback;
  }
}

function lines(s) {
  return s ? s.split(/\r?\n/).filter(Boolean) : [];
}

async function exists(p) {
  try {
    await stat(p);
    return true;
  } catch {
    return false;
  }
}

function requireRepo() {
  if (!REPO) {
    throw new Error(
      "AIHC_REPO is not set. Point it at the AIHealthcare repo root in the server's env block."
    );
  }
}

/* ------------------------------------------------------------------ */
/* tool: repo_status                                                   */
/* ------------------------------------------------------------------ */

async function repoStatus() {
  requireRepo();
  if (!(await exists(path.join(REPO, ".git")))) {
    throw new Error(`No git repository at ${REPO}`);
  }

  const branch = await gitSafe(["rev-parse", "--abbrev-ref", "HEAD"], "unknown");
  const porcelain = lines(await gitSafe(["status", "--porcelain"]));

  const dirty = porcelain.map((l) => ({
    code: l.slice(0, 2).trim(),
    file: l.slice(3),
  }));

  // ahead/behind vs upstream, when an upstream exists
  let ahead = null;
  let behind = null;
  const counts = await gitSafe(["rev-list", "--left-right", "--count", "@{upstream}...HEAD"], "");
  if (counts) {
    const [b, a] = counts.split(/\s+/).map(Number);
    if (!Number.isNaN(a)) ahead = a;
    if (!Number.isNaN(b)) behind = b;
  }

  const since = (spec) =>
    gitSafe(["rev-list", "--count", "--since", spec, "HEAD"], "0").then((n) => Number(n) || 0);

  const [today, week] = await Promise.all([
    since("midnight"),
    since("7 days ago"),
  ]);

  const lastRaw = await gitSafe(["log", "-1", "--format=%H%x1f%s%x1f%aI%x1f%an"], "");
  let last = null;
  if (lastRaw) {
    const [hash, subject, when, author] = lastRaw.split("\x1f");
    last = { hash: hash.slice(0, 8), subject, when, author };
  }

  const recent = lines(
    await gitSafe(["log", "-8", "--format=%h%x1f%s%x1f%aI"], "")
  ).map((l) => {
    const [hash, subject, when] = l.split("\x1f");
    return { hash, subject, when };
  });

  return {
    repo: path.basename(REPO),
    branch,
    ahead,
    behind,
    dirtyCount: dirty.length,
    dirty: dirty.slice(0, 25),
    commitsToday: today,
    commitsThisWeek: week,
    last,
    recent,
    readAt: new Date().toISOString(),
  };
}

/* ------------------------------------------------------------------ */
/* tool: build_status                                                  */
/* ------------------------------------------------------------------ */

/** Never worth descending: VCS, dependencies, unpacked-jar spill, generated docs. */
const NOISE_DIRS = new Set([
  ".git", ".idea", ".mvn", ".claude", "node_modules", "target",
  "BOOT-INF", "META-INF", "org", "javadoc", "logs", "images", "doc_downloads",
]);

/** Inside a target/ directory, these hold compiled output, not test reports. */
const TARGET_NOISE = new Set([
  "classes", "test-classes", "generated-sources", "generated-test-sources",
  "maven-archiver", "maven-status",
]);

/**
 * Find every `src` directory with a main/java beneath it. Deliberately does NOT
 * key off pom.xml: a module's sources can sit beside a parent pom with no pom
 * of their own, which is exactly how this project is laid out.
 */
async function findSourceRoots(root, depth = 0, out = []) {
  if (depth > 3 || out.length >= 12) return out;
  let entries;
  try {
    entries = await readdir(root, { withFileTypes: true });
  } catch {
    return out;
  }
  for (const e of entries) {
    if (!e.isDirectory()) continue;
    if (e.name.startsWith(".") || NOISE_DIRS.has(e.name)) continue;
    const full = path.join(root, e.name);
    if (e.name === "src") {
      if (await exists(path.join(full, "main", "java"))) out.push(full);
      continue;
    }
    await findSourceRoots(full, depth + 1, out);
  }
  return out;
}

async function collectSurefire(dir, out, depth = 0) {
  if (depth > 3) return;
  let entries;
  try {
    entries = await readdir(dir, { withFileTypes: true });
  } catch {
    return;
  }
  const inReportDir = /(surefire|failsafe)-reports$/.test(path.basename(dir));

  for (const e of entries) {
    const full = path.join(dir, e.name);
    if (e.isDirectory()) {
      if (TARGET_NOISE.has(e.name)) continue;
      await collectSurefire(full, out, depth + 1);
      continue;
    }
    if (!inReportDir) continue;
    if (!/^TEST-.*\.xml$/.test(e.name)) continue;
    let xml;
    try {
      xml = await readFile(full, "utf8");
    } catch {
      continue;
    }
    const m = xml.match(/<testsuite\b[^>]*>/);
    if (!m) continue;
    const attr = (k) => {
      const r = new RegExp(k + '="([^"]*)"').exec(m[0]);
      return r ? Number(r[1]) || 0 : 0;
    };
    out.tests += attr("tests");
    out.failures += attr("failures");
    out.errors += attr("errors");
    out.skipped += attr("skipped");
    out.suites += 1;
    const st = await stat(full).catch(() => null);
    if (st && (!out.newest || st.mtimeMs > out.newest)) out.newest = st.mtimeMs;
  }
}

async function buildStatus() {
  requireRepo();

  const acc = {
    tests: 0,
    failures: 0,
    errors: 0,
    skipped: 0,
    suites: 0,
    newest: 0,
  };

  // Every module dir: the repo root plus the parent of each source root.
  const moduleDirs = new Set([REPO]);
  for (const src of await findSourceRoots(REPO)) moduleDirs.add(path.dirname(src));

  let lastBuildMs = 0;
  for (const dir of moduleDirs) {
    const target = path.join(dir, "target");
    if (!(await exists(target))) continue;
    await collectSurefire(target, acc);
    const st = await stat(target).catch(() => null);
    if (st && st.mtimeMs > lastBuildMs) lastBuildMs = st.mtimeMs;
  }

  const failed = acc.failures + acc.errors;
  return {
    hasReports: acc.suites > 0,
    suites: acc.suites,
    tests: acc.tests,
    failures: acc.failures,
    errors: acc.errors,
    skipped: acc.skipped,
    passed: Math.max(acc.tests - failed - acc.skipped, 0),
    verdict: acc.suites === 0 ? "unknown" : failed > 0 ? "failing" : "green",
    testsRanAt: acc.newest ? new Date(acc.newest).toISOString() : null,
    lastBuildAt: lastBuildMs ? new Date(lastBuildMs).toISOString() : null,
    readAt: new Date().toISOString(),
  };
}

/* ------------------------------------------------------------------ */
/* tool: app_health                                                    */
/* ------------------------------------------------------------------ */

async function appHealth() {
  const started = Date.now();
  try {
    const res = await fetch(APP_URL, {
      method: "GET",
      redirect: "follow",
      signal: AbortSignal.timeout(8000),
      headers: { "user-agent": "aihealthcare-pulse/1.0" },
    });
    return {
      url: APP_URL,
      reachable: true,
      status: res.status,
      ok: res.ok,
      latencyMs: Date.now() - started,
      checkedAt: new Date().toISOString(),
    };
  } catch (err) {
    return {
      url: APP_URL,
      reachable: false,
      status: null,
      ok: false,
      latencyMs: Date.now() - started,
      reason: err && err.name === "TimeoutError" ? "timed out after 8s" : String(err && err.message || err),
      checkedAt: new Date().toISOString(),
    };
  }
}

/* ------------------------------------------------------------------ */
/* tool: slice_progress                                                */
/* ------------------------------------------------------------------ */

/** Count .java files under a directory, at any depth. */
async function countJavaIn(dir) {
  let n = 0;
  const stack = [[dir, 0]];
  while (stack.length) {
    const [d, depth] = stack.pop();
    if (depth > 10) continue;
    let entries;
    try {
      entries = await readdir(d, { withFileTypes: true });
    } catch {
      continue;
    }
    for (const e of entries) {
      if (e.isDirectory()) stack.push([path.join(d, e.name), depth + 1]);
      else if (e.name.endsWith(".java")) n += 1;
    }
  }
  return n;
}

/**
 * Descend the single-child package chain (com/wgblackmon/aihealthcare/claude)
 * to the directory where the codebase actually branches into layers.
 */
async function packageRootOf(srcDir) {
  let cur = srcDir;
  for (let i = 0; i < 12; i++) {
    let entries;
    try {
      entries = await readdir(cur, { withFileTypes: true });
    } catch {
      return null;
    }
    const dirs = entries.filter((e) => e.isDirectory());
    const javas = entries.filter((e) => e.isFile() && e.name.endsWith(".java"));
    if (dirs.length === 1 && javas.length === 0) {
      cur = path.join(cur, dirs[0].name);
      continue;
    }
    return cur;
  }
  return cur;
}

/** Per-layer .java counts under one source root. */
async function layersUnder(srcDir) {
  const root = await packageRootOf(srcDir);
  if (!root) return { root: null, counts: new Map(), loose: 0 };
  let entries = [];
  try {
    entries = await readdir(root, { withFileTypes: true });
  } catch {
    /* absent */
  }
  const counts = new Map();
  let loose = 0;
  for (const e of entries) {
    if (e.isDirectory()) counts.set(e.name, await countJavaIn(path.join(root, e.name)));
    else if (e.name.endsWith(".java")) loose += 1;
  }
  return { root, counts, loose };
}

async function sliceProgress() {
  requireRepo();

  const docsDir = path.join(REPO, "docs");
  const specs = [];
  let entries = [];
  try {
    entries = await readdir(docsDir, { withFileTypes: true });
  } catch {
    /* no docs dir */
  }
  for (const e of entries) {
    if (!e.isFile() || !e.name.endsWith(".md")) continue;
    const full = path.join(docsDir, e.name);
    const st = await stat(full).catch(() => null);
    let headline = "";
    try {
      const head = (await readFile(full, "utf8")).slice(0, 2000);
      const h = head.match(/^#\s+(.+)$/m);
      if (h) headline = h[1].trim();
    } catch {
      /* unreadable */
    }
    specs.push({
      name: e.name,
      headline,
      bytes: st ? st.size : null,
      updatedAt: st ? new Date(st.mtimeMs).toISOString() : null,
    });
  }
  specs.sort((a, b) => (b.updatedAt || "").localeCompare(a.updatedAt || ""));

  // Layer weight, resolved from wherever the sources actually are.
  const srcRoots = await findSourceRoots(REPO);

  const layerMap = new Map();
  let basePackage = null;
  let mainTotal = 0;
  let testTotal = 0;

  for (const src of srcRoots) {
    const mainJava = path.join(src, "main", "java");
    const main = await layersUnder(mainJava);
    const test = await layersUnder(path.join(src, "test", "java"));

    if (!basePackage && main.root) {
      const rel = path.relative(mainJava, main.root);
      basePackage = rel ? rel.split(path.sep).join(".") : null;
    }

    for (const [layer, n] of main.counts) {
      const row = layerMap.get(layer) || { layer, mainFiles: 0, testFiles: 0 };
      row.mainFiles += n;
      layerMap.set(layer, row);
      mainTotal += n;
    }
    mainTotal += main.loose;

    for (const [layer, n] of test.counts) {
      const row = layerMap.get(layer) || { layer, mainFiles: 0, testFiles: 0 };
      row.testFiles += n;
      layerMap.set(layer, row);
      testTotal += n;
    }
    testTotal += test.loose;
  }

  const layers = [...layerMap.values()].sort((a, b) => b.mainFiles - a.mainFiles);

  return {
    basePackage,
    modules: srcRoots.length,
    layers,
    mainFiles: mainTotal,
    testFiles: testTotal,
    specCount: specs.length,
    specs: specs.slice(0, 12),
    readAt: new Date().toISOString(),
  };
}

/* ------------------------------------------------------------------ */
/* MCP plumbing                                                        */
/* ------------------------------------------------------------------ */

const TOOLS = [
  {
    name: "repo_status",
    description:
      "Current git state of the AIHealthcare repo: branch, ahead/behind, uncommitted files, commits today and this week, and the last few commits.",
    inputSchema: { type: "object", properties: {}, additionalProperties: false },
    annotations: { readOnlyHint: true },
    handler: repoStatus,
  },
  {
    name: "build_status",
    description:
      "Result of the last Maven build: surefire/failsafe test totals, pass and failure counts, and when the build last produced output.",
    inputSchema: { type: "object", properties: {}, additionalProperties: false },
    annotations: { readOnlyHint: true },
    handler: buildStatus,
  },
  {
    name: "app_health",
    description:
      "Liveness probe of the deployed AIHealthcare app: HTTP status, latency, and whether it answered at all.",
    inputSchema: { type: "object", properties: {}, additionalProperties: false },
    annotations: { readOnlyHint: true },
    handler: appHealth,
  },
  {
    name: "slice_progress",
    description:
      "Spec documents under docs/ with their last-updated times, plus per-module Java source and test file counts.",
    inputSchema: { type: "object", properties: {}, additionalProperties: false },
    annotations: { readOnlyHint: true },
    handler: sliceProgress,
  },
];

function send(msg) {
  process.stdout.write(JSON.stringify(msg) + "\n");
}

function reply(id, result) {
  send({ jsonrpc: "2.0", id, result });
}

function replyError(id, code, message) {
  send({ jsonrpc: "2.0", id, error: { code, message } });
}

async function handle(msg) {
  const { id, method, params } = msg;

  if (method === "initialize") {
    reply(id, {
      protocolVersion: (params && params.protocolVersion) || PROTOCOL_FALLBACK,
      capabilities: { tools: { listChanged: false } },
      serverInfo: { name: "aihealthcare-pulse", version: "1.0.0" },
    });
    return;
  }

  if (method === "notifications/initialized" || method === "initialized") return;

  if (method === "ping") {
    reply(id, {});
    return;
  }

  if (method === "tools/list") {
    reply(id, {
      tools: TOOLS.map(({ name, description, inputSchema, annotations }) => ({
        name,
        description,
        inputSchema,
        annotations,
      })),
    });
    return;
  }

  if (method === "tools/call") {
    const tool = TOOLS.find((t) => t.name === (params && params.name));
    if (!tool) {
      replyError(id, -32602, `Unknown tool: ${params && params.name}`);
      return;
    }
    try {
      const payload = await tool.handler();
      reply(id, {
        content: [{ type: "text", text: JSON.stringify(payload, null, 2) }],
        structuredContent: payload,
      });
    } catch (err) {
      reply(id, {
        content: [{ type: "text", text: String((err && err.message) || err) }],
        isError: true,
      });
    }
    return;
  }

  if (id !== undefined) replyError(id, -32601, `Method not found: ${method}`);
}

/* ------------------------------------------------------------------ */
/* entry                                                               */
/* ------------------------------------------------------------------ */

if (process.argv.includes("--selftest")) {
  const out = {};
  for (const t of TOOLS) {
    try {
      out[t.name] = await t.handler();
    } catch (err) {
      out[t.name] = { error: String((err && err.message) || err) };
    }
  }
  console.log(JSON.stringify(out, null, 2));
} else {
  let buf = "";
  const inFlight = new Set();

  process.stdin.setEncoding("utf8");
  process.stdin.on("data", (chunk) => {
    buf += chunk;
    let nl;
    while ((nl = buf.indexOf("\n")) >= 0) {
      const line = buf.slice(0, nl).trim();
      buf = buf.slice(nl + 1);
      if (!line) continue;
      let msg;
      try {
        msg = JSON.parse(line);
      } catch {
        continue;
      }
      const p = handle(msg)
        .catch((err) => {
          if (msg && msg.id !== undefined) {
            replyError(msg.id, -32603, String((err && err.message) || err));
          }
        })
        .finally(() => inFlight.delete(p));
      inFlight.add(p);
    }
  });

  // Drain in-flight calls before exiting, so a close during a tool call
  // still gets its reply out.
  process.stdin.on("end", async () => {
    while (inFlight.size) await Promise.allSettled([...inFlight]);
    process.exit(0);
  });
}
