#!/usr/bin/env node
// Batch CTO lookup via Perplexity Sonar API
// Reads hac_companies.csv, queries ~20 companies per call, writes cto_results.jsonl

const fs = require('fs');
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
const OUTPUT  = 'C:/workspaces/SpringAIClaude/AIHealthcare/data_input/cto_results.jsonl';
const BATCH   = 20;   // companies per Perplexity call
const DELAY   = 3000; // ms between calls

// ── Parse CSV ──────────────────────────────────────────────────────────────
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

// ── Call Perplexity ────────────────────────────────────────────────────────
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
                try {
                    const parsed = JSON.parse(data);
                    resolve(parsed.choices?.[0]?.message?.content || '');
                } catch (e) {
                    reject(new Error('Bad JSON from API: ' + data.slice(0, 200)));
                }
            });
        });
        req.on('error', reject);
        req.write(body);
        req.end();
    });
}

// ── Extract JSON array from response (handles stray markdown) ──────────────
function extractJson(text) {
    const match = text.match(/\[[\s\S]*\]/);
    if (!match) return null;
    try { return JSON.parse(match[0]); } catch { return null; }
}

// ── Sleep ──────────────────────────────────────────────────────────────────
const sleep = ms => new Promise(r => setTimeout(r, ms));

// ── Main ───────────────────────────────────────────────────────────────────
async function main() {
    const companies = parseCsv(fs.readFileSync(INPUT, 'utf8'));
    console.log(`Loaded ${companies.length} companies. Running ${Math.ceil(companies.length / BATCH)} batches.`);

    const out = fs.createWriteStream(OUTPUT, { flags: 'w' });
    let total = 0;

    for (let i = 0; i < companies.length; i += BATCH) {
        const batch = companies.slice(i, i + BATCH);
        const batchNum = Math.floor(i / BATCH) + 1;
        const totalBatches = Math.ceil(companies.length / BATCH);
        console.log(`\nBatch ${batchNum}/${totalBatches}: ${batch.map(c => c.name).join(', ')}`);

        const companyList = batch.map(c =>
            `- ${c.name} (${c.domain})${c.sub_sector ? ' — ' + c.sub_sector : ''}`
        ).join('\n');

        const prompt = `For each AI healthcare company below, find the current CTO or equivalent senior technical leader (VP Engineering, Co-founder/CTO, Head of Engineering, or CTO). Return a JSON array where each element has these exact keys:
{
  "company": "exact company name from the list",
  "domain": "company domain",
  "cto_name": "full name or null if not found",
  "title": "exact current title or null",
  "linkedin_url": "full LinkedIn URL or null",
  "email": "work email if findable or null",
  "email_confidence": "current_company | likely_stale | personal | no_email",
  "ai_focus": "core | applied | none | unclear",
  "confidence": "high | medium | low",
  "what_they_do": "one sentence describing the company"
}

Return ONLY the JSON array. No markdown, no explanation. If a company has no findable CTO, include the row with nulls.

Companies:
${companyList}`;

        try {
            const response = await callPerplexity(prompt);
            const records = extractJson(response);

            if (!records) {
                console.warn(`  WARNING: Could not parse JSON for batch ${batchNum}. Raw: ${response.slice(0, 300)}`);
                continue;
            }

            console.log(`  Got ${records.length} records`);
            for (const rec of records) {
                out.write(JSON.stringify(rec) + '\n');
                if (rec.cto_name) total++;
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
    console.log(`\nFinished. ${total} CTOs found. Results in cto_results.jsonl`);
}

main().catch(console.error);
