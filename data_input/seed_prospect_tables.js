#!/usr/bin/env node
// Seeds prospect_company and prospect_contact from our 17 high-confidence CTOs.
// Safe to re-run — uses ON CONFLICT DO NOTHING.

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

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

function sql(query) {
    return execSync(`${PSQL} ${CONN} -c "${query.replace(/"/g, '\\"')}"`,
        { env: process.env, shell: true }).toString().trim();
}

// ── 17 high-confidence CTOs from find_ctos.js run (2026-09-21) ───────────────
const CTOs = [
    { company: 'Abridge',                domain: 'abridge.com',        sector: 'Healthcare AI', subSector: 'Ambient clinical documentation AI',           name: 'San Oo',           title: 'CTO',                        linkedin: 'https://www.linkedin.com/in/san-oo' },
    { company: 'Aidoc',                  domain: 'aidoc.com',          sector: 'Healthcare AI', subSector: 'Medical imaging and workflow orchestration AI', name: 'Michael B Braginsky', title: 'CTO',                     linkedin: 'https://www.linkedin.com/in/michael-b-braginsky-72885616' },
    { company: 'Anterior',               domain: 'anterior.com',       sector: 'Healthcare AI', subSector: 'Prior authorization and payer workflow AI',     name: 'Zahid Mahmood',    title: 'Founder & CTO',              linkedin: null },
    { company: 'Assort Health',          domain: 'assorthealth.com',   sector: 'Healthcare AI', subSector: 'Patient access and clinical call-center AI',    name: 'Jeffery Liu',      title: 'Co-Founder, CTO',            linkedin: 'https://www.linkedin.com/in/jefferyliu300' },
    { company: 'Datavant',               domain: 'datavant.com',       sector: 'Healthcare AI', subSector: 'Healthcare data collaboration and linkage',      name: 'Josh Builder',     title: 'Chief Technology Officer',   linkedin: null },
    { company: 'Hippocratic AI',         domain: 'hippocraticai.com',  sector: 'Healthcare AI', subSector: 'Patient-facing generative AI agents',           name: 'Saad Godil',       title: 'Co-Founder & CTO',           linkedin: 'https://www.linkedin.com/in/saad-godil-9728353' },
    { company: 'K Health',               domain: 'khealth.com',        sector: 'Healthcare AI', subSector: 'Virtual primary care and clinical AI',           name: 'Zachary Siegel',   title: 'Chief Technology Officer',   linkedin: 'https://www.linkedin.com/in/zacsiegel' },
    { company: 'Komodo Health',          domain: 'komodohealth.com',   sector: 'Healthcare AI', subSector: 'Healthcare data intelligence and real-world data', name: 'Amit Sangani',   title: 'Chief Technology Officer',   linkedin: 'https://www.linkedin.com/in/amitsangani' },
    { company: 'Medplum',                domain: 'medplum.com',        sector: 'Healthcare AI', subSector: 'FHIR-native clinical platform and EHR',          name: 'Cody Ebberson',    title: 'Co-Founder and CTO',         linkedin: 'https://www.linkedin.com/in/codyebberson' },
    { company: 'Nabla',                  domain: 'nabla.com',          sector: 'Healthcare AI', subSector: 'Ambient clinical AI copilot',                    name: 'Martin Raison',    title: 'Co-founder & CTO',           linkedin: 'https://www.linkedin.com/in/martinraison' },
    { company: 'Recursion Pharmaceuticals', domain: 'recursion.com',  sector: 'Healthcare AI', subSector: 'AI-driven drug discovery',                       name: 'Ben Mabey',        title: 'Chief Technology Officer',   linkedin: 'https://www.linkedin.com/in/benmabey' },
    { company: 'Tempus',                 domain: 'tempus.com',         sector: 'Healthcare AI', subSector: 'AI and precision medicine',                      name: 'Shane Colley',     title: 'Chief Technology Officer',   linkedin: 'https://www.linkedin.com/in/shane-colley-2a47533' },
    { company: 'Tennr',                  domain: 'tennr.com',          sector: 'Healthcare AI', subSector: 'Healthcare operations automation',               name: 'Tyler Johnson',    title: 'Co-Founder and CTO',         linkedin: 'https://www.linkedin.com/in/tyler-johnson-482a42158' },
    { company: 'Wysa',                   domain: 'wysa.com',           sector: 'Healthcare AI', subSector: 'AI mental and behavioral health platform',       name: 'Shubhankar Sarda', title: 'Chief Technology Officer',   linkedin: 'https://www.linkedin.com/in/shubhankar-sarda-0115b628' },
    { company: 'Xaira Therapeutics',     domain: 'xaira.com',          sector: 'Healthcare AI', subSector: 'AI drug discovery and development',              name: 'Hetu Kamichetty',  title: 'Co-founder and CTO',         linkedin: 'https://www.linkedin.com/in/hetu-kamichetty' },
    { company: 'XpertDox',               domain: 'xpertdox.com',       sector: 'Healthcare AI', subSector: 'Autonomous medical coding and RCM',              name: 'Mateo Montoya',    title: 'Chief Technology Officer',   linkedin: 'https://www.linkedin.com/in/mateo-montoya-z' },
    { company: 'Philips',                domain: 'philips.com',        sector: 'Healthcare AI', subSector: 'Health technology and AI-enabled care platforms', name: 'Laura Matz',      title: 'Chief Innovation and Technology Officer', linkedin: 'https://www.linkedin.com/in/lauramatz' },
];

function esc(v) {
    if (v === null || v === undefined) return 'NULL';
    return "'" + String(v).replace(/'/g, "''") + "'";
}

console.log('Creating schema...');
execSync(`${PSQL} ${CONN} -f "C:/workspaces/SpringAIClaude/AIHealthcare/data_input/prospect_schema.sql"`,
    { env: process.env, stdio: 'inherit', shell: true });

console.log('\nSeeding prospect_company...');
let companiesInserted = 0;
for (const c of CTOs) {
    // Look up hac_id from healthcare_ai_companies
    const hacResult = execSync(
        `${PSQL} ${CONN} -t -c "SELECT company_id FROM healthcare_ai_companies WHERE domain = ${esc(c.domain)} LIMIT 1"`,
        { env: process.env }
    ).toString().trim();
    const hacId = hacResult || null;

    const q = `
        INSERT INTO prospect_company (hac_id, name, domain, sector, sub_sector)
        VALUES (${esc(hacId)}, ${esc(c.company)}, ${esc(c.domain)}, ${esc(c.sector)}, ${esc(c.subSector)})
        ON CONFLICT (domain) DO UPDATE SET
            name       = EXCLUDED.name,
            sector     = EXCLUDED.sector,
            sub_sector = EXCLUDED.sub_sector;
    `.replace(/\s+/g, ' ').trim();

    execSync(`${PSQL} ${CONN} -c "${q.replace(/"/g, '\\"')}"`, { env: process.env });
    process.stdout.write('.');
    companiesInserted++;
}
console.log(`\n${companiesInserted} companies seeded.`);

console.log('\nSeeding prospect_contact (CTO role)...');
let contactsInserted = 0;
for (const c of CTOs) {
    // Get company id
    const compIdResult = execSync(
        `${PSQL} ${CONN} -t -c "SELECT id FROM prospect_company WHERE domain = ${esc(c.domain)}"`,
        { env: process.env }
    ).toString().trim();

    if (!compIdResult) { console.warn(`  WARN: no company found for ${c.domain}`); continue; }

    // K Health already has a confirmed current_company email from original dataset
    const emailStatus = c.domain === 'khealth.com' ? 'current_company' : 'no_email';
    const email = c.domain === 'khealth.com' ? 'zachary.siegel@khealth.com' : null;

    const q = `
        INSERT INTO prospect_contact
            (company_id, full_name, title, role_category, linkedin_url, email, email_status, confidence)
        VALUES
            (${compIdResult}, ${esc(c.name)}, ${esc(c.title)}, 'CTO',
             ${esc(c.linkedin)}, ${esc(email)}, '${emailStatus}', 'high')
        ON CONFLICT (full_name, company_id) DO UPDATE SET
            title        = EXCLUDED.title,
            linkedin_url = EXCLUDED.linkedin_url,
            email        = COALESCE(EXCLUDED.email, prospect_contact.email),
            email_status = EXCLUDED.email_status;
    `.replace(/\s+/g, ' ').trim();

    execSync(`${PSQL} ${CONN} -c "${q.replace(/"/g, '\\"')}"`, { env: process.env });
    process.stdout.write('.');
    contactsInserted++;
}
console.log(`\n${contactsInserted} contacts seeded.`);
console.log('\nDone. Run: node outreach_cli.js status');
