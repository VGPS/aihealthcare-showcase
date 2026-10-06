"""
generate-manual.py
Converts docs/user-manual.md into a self-contained, styled docs/user-manual.html.

Run from any directory:
    python docs/generate-manual.py

No external dependencies beyond the standard Python 'markdown' library (pip install markdown).
"""

import re
import sys
from pathlib import Path

try:
    import markdown
    from markdown.extensions.tables import TableExtension
    from markdown.extensions.fenced_code import FencedCodeExtension
except ImportError:
    print("ERROR: 'markdown' library not installed. Run: pip install markdown", file=sys.stderr)
    sys.exit(1)

ROOT = Path(__file__).parent.parent
SRC  = ROOT / "docs" / "user-manual.md"
DEST = ROOT / "docs" / "user-manual.html"

# ── Read source ────────────────────────────────────────────────────────────────
text = SRC.read_text(encoding="utf-8")

# ── Convert to HTML body ───────────────────────────────────────────────────────
md = markdown.Markdown(
    extensions=[
        TableExtension(),
        FencedCodeExtension(),
        "toc",        # adds id="" to headings
        "nl2br",      # newline in paragraph = <br>
        "sane_lists",
    ],
    extension_configs={
        "toc": {
            "permalink": False,
            "toc_depth": "2-3",
        }
    },
)
body_html = md.convert(text)

# ── Build sidebar nav from heading structure ───────────────────────────────────
# We need to reflect the exact nav groups from the app's navigation.
# Build this statically so it matches the menu exactly.

SIDEBAR_GROUPS = [
    ("Overview", [
        ("Table of Contents", "table-of-contents"),
        ("Subscription Tiers", "subscription-tiers-quick-reference"),
    ]),
    ("Dashboard", [
        ("Dashboard", "dashboard"),
        ("Tour", "tour"),
    ]),
    ("Content", [
        ("Claim Tracker", "claim-tracker"),
        ("Deal Signals", "deal-signals"),
        ("Market Digest", "market-digest"),
        ("Market Enrichment", "market-enrichment"),
        ("Market History", "market-history"),
        ("News Listing", "news-listing"),
        ("Trend History", "trend-history"),
        ("Trends", "trends"),
    ]),
    ("Research", [
        ("AI Search", "ai-search"),
        ("Find Articles", "find-articles"),
        ("Framework Analysis", "framework-analysis"),
        ("Intel Reports", "intel-reports"),
        ("Research Runs", "research-runs"),
        ("Vendor Compare", "vendor-compare"),
    ]),
    ("Legal", [
        ("Legal Timeline", "legal-timeline"),
        ("Legal Trends", "legal-trends"),
        ("Regulatory", "regulatory"),
        ("Legislation", "legislation"),
    ]),
    ("Reference", [
        ("Clinical Trials", "clinical-trials"),
        ("Companies", "companies"),
        ("Company Directory", "company-directory"),
        ("Relationships", "relationships"),
        ("Sentiment &amp; Risk", "sentiment-risk"),
        ("Wiki", "wiki"),
    ]),
    ("Account", [
        ("Social Posts", "social-posts"),
        ("Post Drafts", "post-drafts"),
        ("Weekly Roundup", "weekly-roundup"),
        ("Watchlist", "watchlist"),
        ("Profile", "profile"),
        ("Webhooks", "webhooks"),
        ("Pricing", "pricing"),
        ("Developer", "developer"),
        ("Pipelines", "pipelines"),
        ("Admin", "admin"),
        ("Newsletter Runs", "newsletter-runs"),
        ("Document Library", "document-library"),
        ("Wiki Gaps", "wiki-gaps"),
        ("SSO Providers", "sso-providers"),
        ("Outreach CRM", "outreach-crm"),
    ]),
    ("Enterprise", [
        ("Data Console", "data-console"),
        ("Intelligence Console", "intelligence-console"),
    ]),
    ("Login &amp; Registration", [
        ("Login", "login"),
        ("Register", "register"),
        ("Privacy Policy", "privacy-policy"),
    ]),
]

ADMIN_LINKS = {
    "pipelines", "admin", "newsletter-runs",
    "document-library", "wiki-gaps", "sso-providers", "outreach-crm",
}

def build_sidebar():
    parts = ['<nav class="sidebar">',
             '  <div class="sidebar-header">',
             '    <div class="sidebar-appname">AI in Healthcare</div>',
             '    <div class="sidebar-title">User Manual</div>',
             '    <div class="sidebar-version">v1.0 &middot; October 2026</div>',
             '  </div>',
             '  <div class="sidebar-search">',
             '    <input type="text" id="sidebarSearch" placeholder="&#128269; Filter pages&hellip;" autocomplete="off">',
             '  </div>',
             '  <div id="sidebarNav">']
    for group_name, pages in SIDEBAR_GROUPS:
        open_attr = ' open' if group_name in ("Overview", "Dashboard", "Content") else ''
        parts.append(f'    <details class="nav-group"{open_attr}>')
        parts.append(f'      <summary>{group_name}</summary>')
        parts.append('      <ul>')
        for label, anchor in pages:
            admin_cls = ' class="admin-link"' if anchor in ADMIN_LINKS else ''
            parts.append(f'        <li><a href="#{anchor}"{admin_cls}>{label}</a></li>')
        parts.append('      </ul>')
        parts.append('    </details>')
    parts.append('  </div>')
    parts.append('</nav>')
    return "\n".join(parts)


# ── Post-process body: add tier badge spans, note styling, code styling ────────
def add_tier_badge(html):
    """Wrap tier keywords in colored badge spans — only inside <td> cells."""
    tiers = [
        ('PUBLIC',     'tier t-public'),
        ('FREE',       'tier t-free'),
        ('DEMO',       'tier t-demo'),
        ('SUBSCRIBER', 'tier t-subscriber'),
        ('ENTERPRISE', 'tier t-enterprise'),
        ('ADMIN',      'tier t-admin'),
    ]
    def process_cell(m):
        cell = m.group(0)
        for label, css in tiers:
            cell = re.sub(
                r'(?<![a-zA-Z-])' + label + r'(?![a-zA-Z-/])',
                f'<span class="{css}">{label}</span>',
                cell,
            )
        return cell
    return re.sub(r'<td>.*?</td>', process_cell, html, flags=re.DOTALL)


def style_notes(html):
    """Wrap blockquotes that contain Note/Important/Warning in a styled div."""
    html = re.sub(
        r'<blockquote>\s*<p><strong>(Note|Important|Warning|Tip):</strong>(.*?)</p>\s*</blockquote>',
        lambda m: (
            f'<div class="callout callout-{m.group(1).lower()}">'
            f'<span class="callout-icon">&#9432;</span>'
            f'<div><strong>{m.group(1)}:</strong>{m.group(2)}</div></div>'
        ),
        html,
        flags=re.DOTALL,
    )
    return html


def add_section_ids(html):
    """Ensure h2/h3 headings have the anchor IDs the sidebar links expect."""
    # python-markdown's toc extension already adds id="" — we just clean up
    # the generated IDs to match what the sidebar references.
    return html


body_html = style_notes(body_html)
body_html = add_tier_badge(body_html)


# ── Full HTML template ─────────────────────────────────────────────────────────
CSS = """
:root {
  --purple:      #7c3aed;
  --purple-lt:   #f5f3ff;
  --purple-acc:  #a78bfa;
  --sidebar-bg:  #1e1e2e;
  --sidebar-txt: #cdd6f4;
  --sidebar-mut: #7c7d8e;
  --sidebar-act: #a78bfa;
  --sidebar-w:   272px;
  --body:        #1f2937;
  --muted:       #6b7280;
  --border:      #e5e7eb;
  --code-bg:     #f3f4f6;
  --stripe:      #f9fafb;
}

*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

html { scroll-behavior: smooth; }

body {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto,
               "Helvetica Neue", Arial, sans-serif;
  color: var(--body);
  background: #f4f5f7;
  font-size: 15px;
  line-height: 1.6;
}

/* ── Layout ── */
.layout { display: flex; min-height: 100vh; }

/* ── Sidebar ── */
.sidebar {
  width: var(--sidebar-w);
  background: var(--sidebar-bg);
  color: var(--sidebar-txt);
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}

.sidebar::-webkit-scrollbar { width: 5px; }
.sidebar::-webkit-scrollbar-track { background: transparent; }
.sidebar::-webkit-scrollbar-thumb { background: #3d3d52; border-radius: 3px; }

.sidebar-header {
  padding: 20px 16px 14px;
  border-bottom: 1px solid #2a2a3e;
  flex-shrink: 0;
}
.sidebar-appname  { font-size: 11px; color: var(--sidebar-mut); text-transform: uppercase; letter-spacing: .07em; }
.sidebar-title    { font-size: 15px; font-weight: 700; color: var(--sidebar-txt); margin-top: 3px; }
.sidebar-version  { font-size: 11px; color: var(--sidebar-mut); margin-top: 2px; }

.sidebar-search { padding: 10px 12px; flex-shrink: 0; }
.sidebar-search input {
  width: 100%;
  padding: 6px 10px;
  border-radius: 6px;
  border: 1px solid #3d3d52;
  background: #2a2a3e;
  color: var(--sidebar-txt);
  font-size: 13px;
  outline: none;
}
.sidebar-search input:focus { border-color: var(--purple-acc); }
.sidebar-search input::placeholder { color: var(--sidebar-mut); }

#sidebarNav { flex: 1; overflow-y: auto; }

.nav-group { border-bottom: 1px solid #25253a; }
.nav-group > summary {
  padding: 9px 16px;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: .08em;
  color: var(--sidebar-mut);
  cursor: pointer;
  list-style: none;
  display: flex;
  align-items: center;
  gap: 6px;
  user-select: none;
}
.nav-group > summary:hover { color: var(--sidebar-txt); }
.nav-group > summary::before { content: "▸"; font-size: 9px; transition: transform .2s; }
.nav-group[open] > summary::before { transform: rotate(90deg); }
.nav-group::-webkit-details-marker { display: none; }

.nav-group ul { list-style: none; padding-bottom: 6px; }
.nav-group ul li a {
  display: block;
  padding: 5px 16px 5px 28px;
  font-size: 13px;
  color: var(--sidebar-txt);
  text-decoration: none;
  border-left: 3px solid transparent;
  transition: background .15s, border-color .15s, color .15s;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.nav-group ul li a:hover  { background: #2a2a3e; border-left-color: #3d3d52; color: var(--purple-acc); }
.nav-group ul li a.active { background: #2a2a3e; border-left-color: var(--sidebar-act); color: var(--sidebar-act); font-weight: 600; }
.nav-group ul li a.admin-link { color: #818cf8; font-style: italic; }

/* ── Main content ── */
.content { flex: 1; min-width: 0; }

/* ── Hero ── */
.hero {
  background: linear-gradient(135deg, #3b0764 0%, #7c3aed 60%, #4f46e5 100%);
  color: #fff;
  padding: 40px 52px;
}
.hero h1   { font-size: 30px; font-weight: 800; margin-bottom: 6px; }
.hero p    { font-size: 15px; opacity: .8; max-width: 700px; line-height: 1.7; }
.hero-meta { margin-top: 18px; display: flex; gap: 24px; font-size: 12px; opacity: .65; flex-wrap: wrap; }
.hero-meta span::before { margin-right: 4px; }

/* ── Content body ── */
.content-body { max-width: 960px; padding: 44px 52px 80px; }

/* headings */
.content-body h1 { font-size: 26px; font-weight: 800; color: var(--purple); margin: 40px 0 10px; border-bottom: 2px solid var(--purple); padding-bottom: 8px; }
.content-body h2 { font-size: 21px; font-weight: 700; color: #111827; margin: 36px 0 8px; padding-bottom: 6px; border-bottom: 1px solid var(--border); }
.content-body h3 { font-size: 17px; font-weight: 700; color: #1e3a5f; margin: 28px 0 10px; }
.content-body h4 { font-size: 14px; font-weight: 700; color: var(--muted); text-transform: uppercase; letter-spacing: .05em; margin: 20px 0 8px; }

.content-body p  { margin-bottom: 12px; line-height: 1.75; }
.content-body ul, .content-body ol { padding-left: 22px; margin-bottom: 14px; }
.content-body li { margin-bottom: 4px; line-height: 1.7; }
.content-body hr { border: none; border-top: 1px solid var(--border); margin: 36px 0; }

/* tables */
.content-body table {
  width: 100%;
  border-collapse: collapse;
  margin: 14px 0 20px;
  font-size: 14px;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 8px;
  overflow: hidden;
}
.content-body th {
  background: #f1f5f9;
  padding: 9px 14px;
  text-align: left;
  font-weight: 600;
  color: #374151;
  border-bottom: 2px solid var(--border);
  white-space: nowrap;
}
.content-body td {
  padding: 8px 14px;
  border-bottom: 1px solid #f0f0f0;
  vertical-align: top;
}
.content-body tr:last-child td { border-bottom: none; }
.content-body tr:hover td { background: var(--stripe); }
/* First-column info tables (th width 150px) */
.content-body tr > th:first-child:only-of-type { width: 150px; }

/* code */
.content-body code {
  background: var(--code-bg);
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 13px;
  font-family: "Cascadia Code", "Fira Code", "Source Code Pro", monospace;
  color: #4b5563;
}
.content-body pre {
  background: #1e1e2e;
  color: #cdd6f4;
  padding: 16px 20px;
  border-radius: 8px;
  overflow-x: auto;
  font-size: 13px;
  margin: 14px 0;
}
.content-body pre code {
  background: none;
  color: inherit;
  padding: 0;
  font-size: inherit;
}

/* blockquote → callout */
.content-body blockquote {
  border-left: 4px solid var(--purple-acc);
  background: var(--purple-lt);
  padding: 12px 16px;
  margin: 14px 0;
  border-radius: 0 8px 8px 0;
  font-size: 14px;
  color: #4c1d95;
}
.content-body blockquote p { margin-bottom: 0; }

.callout {
  display: flex;
  gap: 10px;
  padding: 12px 16px;
  border-radius: 8px;
  margin: 14px 0;
  font-size: 14px;
}
.callout-icon { font-size: 16px; flex-shrink: 0; margin-top: 1px; }
.callout-note      { background: #eff6ff; border-left: 4px solid #3b82f6; color: #1e40af; }
.callout-important { background: #fef3c7; border-left: 4px solid #f59e0b; color: #78350f; }
.callout-warning   { background: #fff1f2; border-left: 4px solid #f43f5e; color: #881337; }
.callout-tip       { background: #f0fdf4; border-left: 4px solid #22c55e; color: #14532d; }

/* ── Tier badges ── */
.tier {
  display: inline-block;
  padding: 2px 9px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
  vertical-align: middle;
  line-height: 1.6;
}
.t-public     { background: #f3f4f6; color: #374151; }
.t-free       { background: #d1fae5; color: #065f46; }
.t-demo       { background: #fef3c7; color: #92400e; }
.t-subscriber { background: #dbeafe; color: #1e40af; }
.t-enterprise { background: #ede9fe; color: #5b21b6; }
.t-admin      { background: #fee2e2; color: #991b1b; }

/* print */
@media print {
  .sidebar { display: none !important; }
  .content-body { max-width: 100%; padding: 20px; }
  .hero { background: var(--purple) !important; }
  .content-body table { font-size: 12px; }
  a[href]::after { content: ""; }
}
"""

JS = """
(function () {
  // ── Sidebar search ──────────────────────────────────────────────────────────
  var input = document.getElementById('sidebarSearch');
  if (input) {
    input.addEventListener('input', function () {
      var q = this.value.trim().toLowerCase();
      document.querySelectorAll('.nav-group').forEach(function (group) {
        var matched = false;
        group.querySelectorAll('li').forEach(function (li) {
          var text = li.textContent.toLowerCase();
          var show = !q || text.indexOf(q) !== -1;
          li.style.display = show ? '' : 'none';
          if (show) matched = true;
        });
        group.style.display = matched || !q ? '' : 'none';
        if (q && matched) group.open = true;
      });
    });
  }

  // ── Active-link tracking via IntersectionObserver ───────────────────────────
  var allLinks = Array.from(document.querySelectorAll('#sidebarNav a[href^="#"]'));

  function setActive(id) {
    allLinks.forEach(function (a) {
      var href = a.getAttribute('href').slice(1);
      if (href === id) {
        a.classList.add('active');
        // open parent details if closed
        var det = a.closest('details');
        if (det) det.open = true;
      } else {
        a.classList.remove('active');
      }
    });
  }

  var sections = Array.from(document.querySelectorAll('.content-body h2[id], .content-body h3[id]'));

  if ('IntersectionObserver' in window) {
    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) setActive(entry.target.id);
      });
    }, { rootMargin: '-10% 0px -70% 0px', threshold: 0 });
    sections.forEach(function (s) { observer.observe(s); });
  }
})();
"""


def build_html(sidebar_html, body_content):
    return f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>AI in Healthcare &mdash; User Manual</title>
  <style>
{CSS}
  </style>
</head>
<body>
<div class="layout">

{sidebar_html}

  <main class="content">
    <header class="hero">
      <h1>AI in Healthcare &mdash; User Manual</h1>
      <p>A complete reference for every page in the application, organized
         by the navigation menu. Each entry shows the menu label, URL,
         access level, and what you can do there.</p>
      <div class="hero-meta">
        <span>v1.0</span>
        <span>October 2026</span>
        <span>bigskylabs.ai</span>
      </div>
    </header>
    <div class="content-body">
{body_content}
    </div>
  </main>

</div>
<script>
{JS}
</script>
</body>
</html>"""


sidebar_html = build_sidebar()
full_html    = build_html(sidebar_html, body_html)

DEST.write_text(full_html, encoding="utf-8")
size_kb = DEST.stat().st_size // 1024
print(f"Generated: {DEST}  ({size_kb} KB)")
