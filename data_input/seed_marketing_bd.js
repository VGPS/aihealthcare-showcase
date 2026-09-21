#!/usr/bin/env node
// Loads marketing_results.jsonl + bd_results.jsonl into prospect_company / prospect_contact.
// Upserts companies (links to healthcare_ai_companies where domain matches).
// Inserts all contacts regardless of confidence — confidence field lets you filter later.
// Safe to re-run.

const fs   = require('fs');
const path = require('path');
const os   = require('os');
const { execSync } = require('child_process');

// Load .env from repo root
try {
    const lines = fs.readFileSync(path.join(__dirname, '..', '.env'), 'utf8').split('\n');
    for (const l of lines) {
        const m = l.match(/^([^#=\s]+)=(.+)$/);
        if (m && !process.env[m[1]]) process.env[m[1]] = m[2].trim();
    }
} catch {}

const PSQL = '"C:\\Program Files\\PostgreSQL\\16\\bin\\psql.exe"';
const CONN = '-U admin -d aihealthcaredb -h localhost';
process.env.PGPASSWORD = process.env.DB_PASSWORD || process.env.PGPASSWORD;
process.env.PGCLIENTENCODING = 'UTF8';
if (!process.env.PGPASSWORD) { console.error('DB_PASSWORD not set in .env'); process.exit(1); }

const TMP = path.join(os.tmpdir(), '_prospect_query.sql');

const FILES = [
    'C:/workspaces/SpringAIClaude/AIHealthcare/data_input/marketing_results.jsonl',
    'C:/workspaces/SpringAIClaude/AIHealthcare/data_input/bd_results.jsonl',
];

function psql(query) {
    fs.writeFileSync(TMP, query.replace(/\s+/g, ' ').trim() + '\n', 'utf8');
    return execSync(`${PSQL} ${CONN} -t -f "${TMP}"`,
        { env: process.env, shell: true }).toString().trim();
}

function esc(v) {
    if (v === null || v === undefined) return 'NULL';
    return "'" + String(v).replace(/'/g, "''") + "'";
}

function escEnum(v, fallback) {
    const val = (v || '').toString().toLowerCase();
    return esc(val || fallback);
}

// Read + deduplicate contacts: key = domain + contact_name + role_category
const allContacts = [];
const seen = new Set();

for (const file of FILES) {
    if (!fs.existsSync(file)) { console.warn('Missing:', file); continue; }
    const lines = fs.readFileSync(file, 'utf8').trim().split('\n');
    for (const line of lines) {
        let rec;
        try { rec = JSON.parse(line); } catch { continue; }
        if (!rec.contact_name || !rec.domain) continue;
        const key = `${rec.domain}||${rec.contact_name}||${rec.role_category}`;
        if (seen.has(key)) continue;
        seen.add(key);
        allContacts.push(rec);
    }
}

console.log(`\nLoaded ${allContacts.length} unique contacts across both roles.\n`);

// ── Step 1: Upsert prospect_company for every unique domain ──────────────────
const domains = [...new Set(allContacts.map(r => r.domain))];
console.log(`Upserting ${domains.length} companies...`);
let companiesNew = 0;

for (const domain of domains) {
    // Look up in healthcare_ai_companies
    const hacRow = psql(
        `SELECT company_id, name, sector, sub_sector FROM healthcare_ai_companies WHERE domain = ${esc(domain)} LIMIT 1`
    );

    let hacId = null, name = domain, sector = null, subSector = null;
    if (hacRow && hacRow !== '') {
        const parts = hacRow.split('|').map(s => s.trim());
        hacId = parts[0] || null;
        name  = parts[1] || domain;
        sector    = parts[2] || null;
        subSector = parts[3] || null;
    } else {
        // Fall back to name from the first contact record for this domain
        const sample = allContacts.find(r => r.domain === domain);
        name = sample?.company || domain;
    }

    const existing = psql(`SELECT id FROM prospect_company WHERE domain = ${esc(domain)}`);
    if (!existing) {
        psql(`
            INSERT INTO prospect_company (hac_id, name, domain, sector, sub_sector)
            VALUES (${esc(hacId)}, ${esc(name)}, ${esc(domain)}, ${esc(sector)}, ${esc(subSector)})
            ON CONFLICT (domain) DO NOTHING
        `);
        companiesNew++;
    }
    process.stdout.write('.');
}
console.log(`\n${companiesNew} new companies inserted.\n`);

// ── Step 2: Insert contacts ───────────────────────────────────────────────────
const VALID_EMAIL_STATUS = ['current_company','likely_stale','personal','no_email'];
const VALID_CONFIDENCE   = ['high','medium','low'];
const VALID_ROLE         = ['CTO','MARKETING','BUSINESS_DEV','SALES','CLINICAL','OTHER'];

console.log(`Inserting ${allContacts.length} contacts...`);
let inserted = 0, skipped = 0;

for (const rec of allContacts) {
    const compId = psql(`SELECT id FROM prospect_company WHERE domain = ${esc(rec.domain)}`);
    if (!compId) { console.warn(`  SKIP: no company for domain ${rec.domain}`); skipped++; continue; }

    const emailStatus = VALID_EMAIL_STATUS.includes(rec.email_confidence) ? rec.email_confidence : 'no_email';
    const confidence  = VALID_CONFIDENCE.includes(rec.confidence) ? rec.confidence : 'low';
    const role        = VALID_ROLE.includes(rec.role_category) ? rec.role_category : 'OTHER';
    const email       = rec.email || null;

    try {
        psql(`
            INSERT INTO prospect_contact
                (company_id, full_name, title, role_category, linkedin_url,
                 email, email_status, confidence)
            VALUES
                (${compId}, ${esc(rec.contact_name)}, ${esc(rec.title)}, '${role}',
                 ${esc(rec.linkedin_url)}, ${esc(email)}, '${emailStatus}', '${confidence}')
            ON CONFLICT (full_name, company_id) DO UPDATE SET
                title        = EXCLUDED.title,
                role_category = EXCLUDED.role_category,
                linkedin_url = EXCLUDED.linkedin_url,
                email        = COALESCE(EXCLUDED.email, prospect_contact.email),
                email_status = EXCLUDED.email_status,
                confidence   = EXCLUDED.confidence
        `);
        inserted++;
    } catch (e) {
        console.warn(`  SKIP (constraint): ${rec.contact_name} @ ${rec.domain} — ${e.message.slice(0,80)}`);
        skipped++;
    }
    process.stdout.write('.');
}

console.log(`\n${inserted} contacts inserted/updated, ${skipped} skipped.\n`);

// ── Step 3: Summary ───────────────────────────────────────────────────────────
const totals = psql(`
    SELECT role_category, confidence, COUNT(*) as n
    FROM prospect_contact
    GROUP BY role_category, confidence
    ORDER BY role_category, confidence
`).split('\n').filter(Boolean);

console.log('prospect_contact breakdown:');
console.log('  Role             | Confidence | Count');
console.log('  -----------------+------------+------');
totals.forEach(row => {
    const [role, conf, n] = row.split('|').map(s => s.trim());
    console.log(`  ${(role||'').padEnd(16)} | ${(conf||'').padEnd(10)} | ${n}`);
});

const grandTotal = psql('SELECT COUNT(*) FROM prospect_contact');
console.log(`\nTotal contacts in dataset: ${grandTotal}`);
