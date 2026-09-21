#!/usr/bin/env node
// Generalized contact finder via Perplexity Sonar API
// Usage:
//   node find_contacts.js --role marketing   → marketing_results.jsonl
//   node find_contacts.js --role bd          → bd_results.jsonl
//
// Reads hac_companies.csv, queries 20 companies per call, writes JSONL output.

const fs   = require('fs');
const https = require('https');

function loadEnv() {
    try {
        const lines = require('fs').readFileSync(
            require('path').join(__dirname, '..', '.env'), 'utf8').split('\n');
        for (const l of lines) {
            const m = l.match(/^([^#=\s]+)=(.+)$/);
            if (m && !process.env[m[1]]) process.env[m[1]] = m[2].trim();
        }
    } catch {}
}
loadEnv();

const API_KEY = process.env.PERPLEXITY_API_KEY;
if (!API_KEY) { console.error('PERPLEXITY_API_KEY not set — source .env first'); process.exit(1); }
const INPUT   = 'C:/workspaces/SpringAIClaude/AIHealthcare/data_input/hac_companies.csv';
const BATCH   = 20;
const DELAY   = 3000;

// ── Role definitions ──────────────────────────────────────────────────────────

const ROLES = {
    marketing: {
        label:    'Marketing',
        category: 'MARKETING',
        output:   'C:/workspaces/SpringAIClaude/AIHealthcare/data_input/marketing_results.jsonl',
        roleDesc: 'CMO, VP Marketing, Head of Marketing, Head of Growth, VP Growth, ' +
                  'Head of Product Marketing, Head of Demand Generation, Head of Content, ' +
                  'or equivalent senior marketing leader',
        promptExtra: 'Focus on the most senior marketing or growth person. ' +
                     'For very small startups a co-founder may double as head of marketing — include them if no dedicated marketing leader exists.',
    },
    bd: {
        label:    'Business Development',
        category: 'BUSINESS_DEV',
        output:   'C:/workspaces/SpringAIClaude/AIHealthcare/data_input/bd_results.jsonl',
        roleDesc: 'CRO (Chief Revenue Officer), VP Business Development, VP Partnerships, ' +
                  'Head of Partnerships, VP Sales, Head of Enterprise Sales, VP Commercial, ' +
                  'or equivalent senior BD/sales/partnerships leader',
        promptExtra: 'Focus on the most senior revenue or BD person. ' +
                     'For early-stage startups a co-founder may lead BD — include them if no dedicated BD leader exists.',
    },
    market_research: {
        label:    'Market Research',
        category: 'MARKET_RESEARCH',
        output:   'C:/workspaces/SpringAIClaude/AIHealthcare/data_input/market_research_results.jsonl',
        roleDesc: 'VP Market Research, Director of Market Intelligence, Head of Competitive Intelligence, ' +
                  'Market Research Manager, Director of Market Strategy, Head of Market Insights, ' +
                  'VP Strategy, or equivalent senior market research/intelligence leader',
        promptExtra: 'Focus on the most senior person owning market research, competitive intelligence, ' +
                     'or market strategy. At smaller companies a strategy, product, or growth leader may fill this role — ' +
                     'include them if no dedicated market research title exists.',
    },
};

// ── Parse CSV ──────────────────────────────────────────────────────────────────
function parseCsv(text) {
    const lines = text.trim().split('\n');
    const headers = lines[0].split(',');
    return lines.slice(1).map(line => {
        const vals = [];
        let cur = '', inQ = false;
        for (const ch of line) {
            if (ch === '"') { inQ = !inQ; }
            else if (ch === ',' && !inQ) { vals.push(cur); cur = ''; }
            else { cur += ch; }
        }
        vals.push(cur);
        return Object.fromEntries(headers.map((h, i) => [h, (vals[i] || '').trim()]));
    });
}

// ── Perplexity call ────────────────────────────────────────────────────────────
function callPerplexity(prompt) {
    return new Promise((resolve, reject) => {
        const body = JSON.stringify({
            model: 'sonar',
            messages: [
                { role: 'system', content: 'You are a research assistant. Return only valid JSON arrays, no markdown fences, no explanation.' },
                { role: 'user', content: prompt }
            ],
            max_tokens: 4000,
            temperature: 0.1
        });
        const req = https.request({
            hostname: 'api.perplexity.ai',
            path: '/chat/completions',
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${API_KEY}`,
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(body)
            }
        }, res => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try { resolve(JSON.parse(data).choices?.[0]?.message?.content || ''); }
                catch (e) { reject(new Error('Bad API JSON: ' + data.slice(0, 200))); }
            });
        });
        req.on('error', reject);
        req.write(body);
        req.end();
    });
}

function extractJson(text) {
    const match = text.match(/\[[\s\S]*\]/);
    if (!match) return null;
    try { return JSON.parse(match[0]); } catch { return null; }
}

const sleep = ms => new Promise(r => setTimeout(r, ms));

// ── Main ───────────────────────────────────────────────────────────────────────
async function main() {
    // Parse --role argument
    const roleArg = (process.argv.find(a => a.startsWith('--role=')) || '').replace('--role=', '')
                 || process.argv[process.argv.indexOf('--role') + 1];
    const role = ROLES[roleArg?.toLowerCase()];
    if (!role) {
        console.error('Usage: node find_contacts.js --role marketing|bd');
        process.exit(1);
    }

    const companies = parseCsv(fs.readFileSync(INPUT, 'utf8'));
    const totalBatches = Math.ceil(companies.length / BATCH);
    console.log(`Role: ${role.label} | ${companies.length} companies | ${totalBatches} batches`);
    console.log(`Output: ${role.output}\n`);

    const out = fs.createWriteStream(role.output, { flags: 'w' });
    let found = 0;

    for (let i = 0; i < companies.length; i += BATCH) {
        const batch = companies.slice(i, i + BATCH);
        const batchNum = Math.floor(i / BATCH) + 1;
        console.log(`Batch ${batchNum}/${totalBatches}: ${batch.map(c => c.name).join(', ')}`);

        const companyList = batch.map(c =>
            `- ${c.name} (${c.domain})${c.sub_sector ? ' — ' + c.sub_sector : ''}`
        ).join('\n');

        const prompt = `For each AI healthcare company below, find the current ${role.roleDesc}.
${role.promptExtra}

Return a JSON array where each element has EXACTLY these keys:
{
  "company": "exact company name from the list",
  "domain": "company domain",
  "contact_name": "full name or null if not found",
  "title": "exact current title or null",
  "linkedin_url": "full LinkedIn URL or null",
  "email": "verified work email if publicly findable, otherwise null — do NOT guess",
  "email_confidence": "current_company | likely_stale | personal | no_email",
  "confidence": "high | medium | low",
  "what_they_do": "one sentence describing what the company does"
}

Return ONLY the JSON array. No markdown, no explanation. If no ${role.label} leader is findable for a company, include the row with contact_name null.

Companies:
${companyList}`;

        try {
            const response = await callPerplexity(prompt);
            const records  = extractJson(response);
            if (!records) {
                console.warn(`  WARNING: could not parse JSON for batch ${batchNum}. Raw: ${response.slice(0, 200)}`);
                continue;
            }
            const withContact = records.filter(r => r.contact_name);
            console.log(`  ${withContact.length}/${records.length} contacts found`);
            for (const rec of records) {
                rec.role_category = role.category;
                out.write(JSON.stringify(rec) + '\n');
                if (rec.contact_name) found++;
            }
        } catch (err) {
            console.error(`  ERROR batch ${batchNum}: ${err.message}`);
        }

        if (i + BATCH < companies.length) {
            process.stdout.write(`  Waiting ${DELAY / 1000}s...`);
            await sleep(DELAY);
            console.log(' done');
        }
    }

    out.end();
    console.log(`\nFinished: ${found} ${role.label} contacts found → ${role.output}`);
}

main().catch(console.error);
