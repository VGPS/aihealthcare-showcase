-- update_acquisition_contacts_tier3.sql
--
-- Tier-2/3 contact research pass continued (web search, 2026-10-18).
--
-- MAJOR CORRECTION: Compliance.ai was acquired by Archer (GRC/risk
-- management software, formerly RSA Archer), NOT Thomson Reuters. Kayvan
-- Alikhani has no Thomson Reuters connection at all. The original slug
-- "compliance-ai-tr" is a misnomer predating this correction and would
-- display as the literal company name in the outreach UI (company isn't
-- in the directory), so it's renamed to "compliance-ai-archer".
--
-- CORRECTION: Bill Evans's actual current title is Founder & General
-- Partner, Rock Health Capital (not "Head of Research" as originally
-- recorded) — he ran Rock Health itself as CEO & Managing Director from
-- 2016-2020 before spinning up the venture fund.

-- ── Compliance.ai / Archer rename ──────────────────────────────────────────
DELETE FROM company_contacts WHERE slug = 'compliance-ai-tr';
DELETE FROM company_outreach WHERE slug = 'compliance-ai-tr' AND purpose = 'ACQUISITION';

INSERT INTO company_outreach (slug, purpose, status, notes, created_at, updated_at) VALUES
('compliance-ai-archer', 'ACQUISITION', 'NOT_STARTED',
 'CORRECTED 2026-10-18: Compliance.ai was acquired by Archer (GRC/risk management software, formerly RSA Archer) on 2024-02-20, NOT Thomson Reuters as originally recorded. Archer absorbed 65 clients + 40 staff; Kayvan Alikhani continues leading Compliance.ai plus a new Archer AI practice. LOW as acquisition / MEDIUM as licensing — closest direct comp to the regulatory tracker. Pitch "data feed partnership," not acquisition.',
 NOW(), NOW())
ON CONFLICT (slug, purpose) DO NOTHING;

INSERT INTO company_contacts (slug, full_name, job_title, email, linkedin_url, source, status, notes, created_at, updated_at) VALUES
('compliance-ai-archer', 'Kayvan Alikhani', 'Co-Founder & Chief Product/Strategy Officer, Compliance.ai; Global Head of Engineering - Emerging Solutions, Archer',
 NULL, 'https://www.linkedin.com/in/kayvanalikhani/', 'OTHER', 'IDENTIFIED',
 'Led Compliance.ai before and after the Archer acquisition; now also leads Archer''s new AI practice. Best path to a licensing conversation within Archer; don''t pitch "acquisition" — pitch "data feed partnership." Verified via web search 2026-10-18.',
 NOW(), NOW());

-- ── Rock Health correction ──────────────────────────────────────────────────
UPDATE company_contacts
SET job_title = 'Founder & General Partner, Rock Health Capital',
    linkedin_url = 'https://www.linkedin.com/in/wevans',
    notes = 'CORRECTED 2026-10-18: not "Head of Research" as originally recorded. Evans was CEO & Managing Director of Rock Health itself 2016-2020, then founded Rock Health Capital (the venture fund). Still a plausible research-license or portfolio-intro path, but he is a fund GP, not an analyst — adjust the outreach angle accordingly. Verified via web search 2026-10-18.',
    updated_at = NOW()
WHERE slug = 'rock-health' AND full_name = 'Bill Evans';

-- ── Flare Capital Partners confirmation (no title correction needed) ───────
UPDATE company_contacts
SET linkedin_url = 'https://www.linkedin.com/in/michaelagreeley/',
    notes = notes || ' | LinkedIn confirmed 2026-10-18; title (Co-Founder/GP) and Boston base confirmed via multiple sources.',
    updated_at = NOW()
WHERE slug = 'flare-capital-partners' AND full_name = 'Michael Greeley';

SELECT slug, full_name, job_title, linkedin_url FROM company_contacts
WHERE slug IN ('compliance-ai-archer', 'rock-health', 'flare-capital-partners')
ORDER BY slug, full_name;
