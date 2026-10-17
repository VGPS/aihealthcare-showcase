-- seed_acquisition_outreach.sql
--
-- Seeds the 9 curated acquisition-target companies from
-- acquisition-outreach-targets.md into the app's company_outreach and
-- company_contacts tables with purpose = ACQUISITION.
--
-- These are NOT AI-healthcare companies in the healthcare_ai_companies
-- directory (they're research firms / VCs), so no corresponding
-- company_profiles row is expected or required — the outreach detail page
-- handles companyFound=false gracefully.
--
-- REQUIREMENTS: app must have been restarted at least once after the
-- ACQUISITION enum value was deployed (so no stale schema assumptions).
-- Run against production RDS from the EC2 box (same network as the app):
--   psql -h "$DB_HOST" -U "$DB_USERNAME" -d aihealthcaredb -f seed_acquisition_outreach.sql
--
-- IDEMPOTENT: company_outreach uses ON CONFLICT (slug, purpose) DO NOTHING.
-- company_contacts has no unique constraint, so dedup is via NOT EXISTS on
-- (slug, full_name).

-- ── 1. Outreach rows ──────────────────────────────────────────────────────────

INSERT INTO company_outreach (slug, purpose, status, notes, created_at, updated_at) VALUES
('chilmark-research', 'ACQUISITION', 'NOT_STARTED',
 'Boutique health IT market research/advisory firm; publishes AI-in-healthcare reports. HIGH confidence — lead with the historical archive, not features.', NOW(), NOW()),
('galen-growth', 'ACQUISITION', 'NOT_STARTED',
 'Digital health market intelligence (HealthTech Alpha platform) — tracks funding/clinical evidence/vendor landscape. HIGH confidence — pitch as a data layer or acquisition for their AI-in-healthcare vertical.', NOW(), NOW()),
('trilliant-health', 'ACQUISITION', 'NOT_STARTED',
 'Healthcare market intelligence for health systems/payers. MEDIUM-HIGH confidence — focus outreach on the regulatory tracker + state legislation registry.', NOW(), NOW()),
('klas-research', 'ACQUISITION', 'NOT_STARTED',
 'Vendor performance ratings for health IT. MEDIUM confidence — frame sentiment/framework analysis as augmenting their analysts, not replacing their process (conservative, family-owned culture).', NOW(), NOW()),
('definitive-healthcare', 'ACQUISITION', 'NOT_STARTED',
 'Public company (Nasdaq: DH), healthcare commercial intelligence. MEDIUM as license / LOW as acquisition — public company needs corp dev approval; more likely outcome is a data licensing discussion. Find VP Product/Data Partnerships as internal champion.', NOW(), NOW()),
('greenlight-guru', 'ACQUISITION', 'NOT_STARTED',
 'QMS SaaS for medtech with a regulatory compliance layer. MEDIUM confidence — FDA 510(k)/De Novo tracker is a natural "regulatory intelligence" add-on for their customer base. PE-backed (Battery Ventures), has capital for tuck-ins.', NOW(), NOW()),
('compliance-ai-tr', 'ACQUISITION', 'NOT_STARTED',
 'Compliance.ai, now part of Thomson Reuters — closest direct comp to the regulatory tracker. LOW as acquisition / MEDIUM as licensing. Pitch "data feed partnership," not acquisition.', NOW(), NOW()),
('rock-health', 'ACQUISITION', 'NOT_STARTED',
 'Digital health VC; publishes widely-cited annual market research reports. LOW as acquisition / MEDIUM as intro path — frame as augmenting their annual funding report, goal is a research license or warm intro to a portfolio company.', NOW(), NOW()),
('flare-capital-partners', 'ACQUISITION', 'NOT_STARTED',
 'Healthcare-focused VC; portfolio includes health data/analytics companies. LOW as acquisition / MEDIUM as intro — ask whether portfolio companies track AI-healthcare regulatory events rather than pitching a sale.', NOW(), NOW())
ON CONFLICT (slug, purpose) DO NOTHING;

-- ── 2. Contacts ───────────────────────────────────────────────────────────────
-- email/linkedin_url left NULL pending the FIND/VERIFY research pass.
-- source=OTHER (doc said "MANUAL", not a valid ContactSource value).
-- status=IDENTIFIED (doc said "NOT_CONTACTED", not a valid ContactStatus value).

INSERT INTO company_contacts (slug, full_name, job_title, email, linkedin_url, source, status, notes, created_at, updated_at)
SELECT v.slug, v.full_name, v.job_title, NULL, NULL, 'OTHER', 'IDENTIFIED', v.notes, NOW(), NOW()
FROM (VALUES
    ('chilmark-research', 'John Moore', 'Founder / Managing Director',
     'Founder; semi-retired but still active. Direct decision-maker for any acquisition. Also consider Brian Eastwood as a warm intro path.'),
    ('chilmark-research', 'Brian Eastwood', 'Research Analyst',
     'Analyst; understands the domain deeply; better cold-intro path than going direct to John Moore as a first contact.'),
    ('galen-growth', 'Yoann Sapanel', 'CEO / Co-founder',
     'Founder-CEO; direct decision-maker. Singapore-based but US-market focused.'),
    ('trilliant-health', 'Hal Andrews', 'CEO',
     'CEO; Tennessee-based. Would make the call himself for a $100K deal, but likely needs a product/data champion first — look for a VP Product or Head of Data as a secondary contact.'),
    ('klas-research', 'Adam Gale', 'CEO',
     'CEO; Utah-based family-owned company. Conservative culture — frame as a tool that helps their analysts, not a replacement.'),
    ('definitive-healthcare', 'Jason Krantz', 'Founder / CEO',
     'Founder-CEO but company is now public/PE-backed — won''t make a $100K call unilaterally anymore. Better path: find VP of Product or Head of Data Partnerships as internal champion first.'),
    ('greenlight-guru', 'David DeRam', 'CEO',
     'Founder-CEO; Indianapolis-based. More likely to propose a data partnership than an outright acquisition, but PE-backed so has capital for small tuck-ins.'),
    ('compliance-ai-tr', 'Kayvan Alikhani', 'VP / GM (Thomson Reuters, ex-Compliance.ai CEO)',
     'Led Compliance.ai before acquisition; now inside Thomson Reuters. Best path to a licensing conversation within TR; don''t pitch "acquisition" — pitch "data feed partnership."'),
    ('rock-health', 'Bill Evans', 'Managing Director / Head of Research',
     'Heads their market research output. Frame as data that would augment their annual funding report — not a sale pitch. Goal is a research license or a warm intro to a portfolio company.'),
    ('flare-capital-partners', 'Michael Greeley', 'Co-Founder / General Partner',
     'GP; Boston-based. Don''t pitch a sale — ask whether any portfolio companies track AI-healthcare regulatory events.')
) AS v(slug, full_name, job_title, notes)
WHERE NOT EXISTS (
    SELECT 1 FROM company_contacts cc
    WHERE cc.slug = v.slug AND cc.full_name = v.full_name
);

-- ── 3. Verify ─────────────────────────────────────────────────────────────────
SELECT 'company_outreach ACQUISITION rows' AS table_name, COUNT(*) AS total
FROM company_outreach WHERE purpose = 'ACQUISITION'
UNION ALL
SELECT 'company_contacts rows for acquisition targets', COUNT(*)
FROM company_contacts WHERE slug IN (
    'chilmark-research','galen-growth','trilliant-health','klas-research',
    'definitive-healthcare','greenlight-guru','compliance-ai-tr','rock-health','flare-capital-partners'
);
