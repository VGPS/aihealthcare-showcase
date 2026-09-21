#!/usr/bin/env node
// Outreach CLI — mark touches, log replies, view status
//
// Usage:
//   node outreach_cli.js status
//   node outreach_cli.js sent   --name "San Oo"         --channel linkedin_note  --template cto-clinical-ai
//   node outreach_cli.js sent   --name "Zahid Mahmood"  --channel email          --template cto-payer-ai    --message "Hi Zahid..."
//   node outreach_cli.js reply  --name "San Oo"         --outcome connected
//   node outreach_cli.js reply  --name "Tyler Johnson"  --outcome replied_positive --text "Thanks Bill, looks useful..."
//   node outreach_cli.js list   --company "Tennr"
//   node outreach_cli.js list   --role CTO
//   node outreach_cli.js list   --outcome PENDING

const { execSync } = require('child_process');
const fs   = require('fs');
const path = require('path');
const os   = require('os');

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
if (!process.env.PGPASSWORD) { console.error('DB_PASSWORD not set in .env'); process.exit(1); }

const TMP = path.join(os.tmpdir(), '_prospect_query.sql');

function psql(query, flags = '') {
    fs.writeFileSync(TMP, query.replace(/\s+/g, ' ').trim() + '\n', 'utf8');
    return execSync(`${PSQL} ${CONN} ${flags} -f "${TMP}"`,
        { env: process.env, shell: true }).toString().trim();
}

function esc(v) {
    if (v === null || v === undefined) return 'NULL';
    return "'" + String(v).replace(/'/g, "''") + "'";
}

function parseArgs() {
    const args = process.argv.slice(3);
    const result = {};
    for (let i = 0; i < args.length; i++) {
        if (args[i].startsWith('--')) {
            result[args[i].slice(2)] = args[i + 1] || true;
            i++;
        }
    }
    return result;
}

const VALID_CHANNELS  = ['LINKEDIN_NOTE', 'LINKEDIN_INMAIL', 'EMAIL', 'OTHER'];
const VALID_OUTCOMES  = ['PENDING', 'CONNECTED', 'REPLIED_POSITIVE', 'REPLIED_NEGATIVE', 'BOUNCED', 'NO_RESPONSE'];
const VALID_ROLES     = ['CTO', 'MARKETING', 'BUSINESS_DEV', 'SALES', 'CLINICAL', 'OTHER'];

// ── Commands ──────────────────────────────────────────────────────────────────

function cmdStatus() {
    console.log('\n── Prospect Outreach Status ───────────────────────────────────────────────\n');
    const rows = psql(`
        SELECT company, full_name, role_category, email_status,
               COALESCE(touch_count::text, '0')    AS touches,
               COALESCE(to_char(last_contacted_at, 'YYYY-MM-DD'), 'not yet') AS last_contact,
               COALESCE(last_channel::text, '-')   AS channel,
               COALESCE(last_outcome::text, '-')   AS outcome
        FROM prospect_status
        ORDER BY last_contacted_at DESC NULLS LAST, company, role_category
    `, '-t --no-align --field-separator "|"').split('\n').filter(Boolean);

    const header = ['Company', 'Name', 'Role', 'Email', 'Touches', 'Last Contact', 'Channel', 'Outcome'];
    const widths = header.map(h => h.length);
    const data   = rows.map(r => {
        const cols = r.split('|');
        cols.forEach((c, i) => { if (c.length > widths[i]) widths[i] = c.length; });
        return cols;
    });

    const line = widths.map(w => '─'.repeat(w + 2)).join('┼');
    const fmt  = row => '│ ' + row.map((c, i) => c.padEnd(widths[i])).join(' │ ') + ' │';

    console.log('┌' + widths.map(w => '─'.repeat(w + 2)).join('┬') + '┐');
    console.log(fmt(header));
    console.log('├' + line + '┤');
    data.forEach(row => console.log(fmt(row)));
    console.log('└' + widths.map(w => '─'.repeat(w + 2)).join('┴') + '┘');
    console.log(`\n${data.length} contacts\n`);
}

function cmdSent(args) {
    const name     = args.name;
    const channel  = (args.channel || '').toUpperCase();
    const template = args.template || null;
    const message  = args.message  || null;

    if (!name)    { console.error('--name required'); process.exit(1); }
    if (!channel) { console.error('--channel required (' + VALID_CHANNELS.join('|') + ')'); process.exit(1); }
    if (!VALID_CHANNELS.includes(channel)) {
        console.error('Invalid channel. Use: ' + VALID_CHANNELS.join(', '));
        process.exit(1);
    }

    const contactId = psql(
        `SELECT id FROM prospect_contact WHERE full_name ILIKE ${esc('%' + name + '%')} LIMIT 1`, '-t'
    ).trim();

    if (!contactId) { console.error('Contact not found: ' + name); process.exit(1); }

    psql(`
        INSERT INTO prospect_outreach (contact_id, channel, template_name, message_text)
        VALUES (${contactId}, '${channel}', ${esc(template)}, ${esc(message)})
    `);

    const who = psql(`
        SELECT c.full_name || ' @ ' || pc.name FROM prospect_contact c
        JOIN prospect_company pc ON pc.id = c.company_id WHERE c.id = ${contactId}
    `, '-t').trim();

    console.log(`✓ Marked SENT: ${who} via ${channel}${template ? ' [' + template + ']' : ''}`);
}

function cmdReply(args) {
    const name    = args.name;
    const outcome = (args.outcome || '').toUpperCase();
    const text    = args.text || null;

    if (!name)    { console.error('--name required'); process.exit(1); }
    if (!outcome) { console.error('--outcome required (' + VALID_OUTCOMES.join('|') + ')'); process.exit(1); }
    if (!VALID_OUTCOMES.includes(outcome)) {
        console.error('Invalid outcome. Use: ' + VALID_OUTCOMES.join(', '));
        process.exit(1);
    }

    const contactId = psql(
        `SELECT id FROM prospect_contact WHERE full_name ILIKE ${esc('%' + name + '%')} LIMIT 1`, '-t'
    ).trim();

    if (!contactId) { console.error('Contact not found: ' + name); process.exit(1); }

    // Update the most recent PENDING outreach for this contact
    const updated = psql(`
        UPDATE prospect_outreach SET
            outcome           = '${outcome}',
            reply_received_at = now(),
            reply_text        = ${esc(text)}
        WHERE id = (
            SELECT id FROM prospect_outreach
            WHERE contact_id = ${contactId}
            ORDER BY sent_at DESC LIMIT 1
        )
    `);

    const who = psql(`
        SELECT c.full_name || ' @ ' || pc.name FROM prospect_contact c
        JOIN prospect_company pc ON pc.id = c.company_id WHERE c.id = ${contactId}
    `, '-t').trim();

    console.log(`✓ Marked REPLY: ${who} → ${outcome}${text ? '\n  "' + text.slice(0, 80) + (text.length > 80 ? '..."' : '"') : ''}`);
}

function cmdList(args) {
    const conditions = [];
    if (args.company) conditions.push(`pc.name ILIKE ${esc('%' + args.company + '%')}`);
    if (args.role)    conditions.push(`c.role_category = '${args.role.toUpperCase()}'`);
    if (args.outcome) conditions.push(`(SELECT outcome FROM prospect_outreach WHERE contact_id = c.id ORDER BY sent_at DESC LIMIT 1) = '${args.outcome.toUpperCase()}'`);

    const where = conditions.length ? 'WHERE ' + conditions.join(' AND ') : '';

    const rows = psql(`
        SELECT pc.name AS company, c.full_name, c.title, c.role_category,
               c.linkedin_url, c.email, c.email_status
        FROM   prospect_contact c
        JOIN   prospect_company pc ON pc.id = c.company_id
        ${where}
        ORDER  BY pc.name, c.role_category
    `, '-t --no-align --field-separator "|"').split('\n').filter(Boolean);

    rows.forEach(r => {
        const [company, name, title, role, linkedin, email, emailStatus] = r.split('|');
        console.log(`${company.padEnd(25)} ${name.padEnd(22)} ${role.padEnd(12)} ${emailStatus.padEnd(16)} ${email || '—'}`);
        if (linkedin && linkedin !== '') console.log('  └ ' + linkedin);
    });
    console.log(`\n${rows.length} contacts`);
}

// ── Dispatch ──────────────────────────────────────────────────────────────────

const command = process.argv[2];
const args    = parseArgs();

switch (command) {
    case 'status': cmdStatus(); break;
    case 'sent':   cmdSent(args); break;
    case 'reply':  cmdReply(args); break;
    case 'list':   cmdList(args); break;
    default:
        console.log(`
Outreach CLI

Commands:
  status                               Show all contacts and their outreach status
  list   [--company X] [--role Y]      Filter contacts
         [--outcome Z]
  sent   --name "Full Name"            Record an outreach touch
         --channel LINKEDIN_NOTE|LINKEDIN_INMAIL|EMAIL|OTHER
         [--template template-name]
         [--message "full text sent"]
  reply  --name "Full Name"            Record a reply/outcome on latest touch
         --outcome CONNECTED|REPLIED_POSITIVE|REPLIED_NEGATIVE|BOUNCED|NO_RESPONSE
         [--text "their reply text"]

Examples:
  node outreach_cli.js status
  node outreach_cli.js sent  --name "San Oo"       --channel linkedin_note --template cto-clinical-ai
  node outreach_cli.js sent  --name "Zahid Mahmood" --channel email        --template cto-payer-ai
  node outreach_cli.js reply --name "San Oo"        --outcome connected
  node outreach_cli.js reply --name "Tyler Johnson"  --outcome replied_positive --text "Thanks Bill..."
  node outreach_cli.js list  --role CTO
  node outreach_cli.js list  --outcome PENDING
        `);
}
