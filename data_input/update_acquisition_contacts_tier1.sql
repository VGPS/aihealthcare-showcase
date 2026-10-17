-- update_acquisition_contacts_tier1.sql
--
-- Tier-1 contact research pass (web search, 2026-10-18) for the acquisition
-- outreach targets seeded in seed_acquisition_outreach.sql.
--
-- CORRECTION: the original acquisition-outreach-targets.md listed Yoann
-- Sapanel as Galen Growth's CEO. He is not affiliated with Galen Growth's
-- leadership (he's Head of Health Innovation at NUS WisDM). Galen Growth's
-- actual Founder/CEO is Julien de Salaberry. This script removes the wrong
-- contact and adds the correct one.
--
-- No emails found via web search for any Tier-1 contact — Hunter.io lookup
-- still needed, left NULL.

-- Chilmark Research — John Moore LinkedIn found via Crunchbase (single-source,
-- not cross-verified — moderate confidence).
UPDATE company_contacts
SET linkedin_url = 'https://www.linkedin.com/in/jmoore2',
    notes = notes || ' | LinkedIn found via Crunchbase 2026-10-18, single-source, not independently cross-verified.',
    updated_at = NOW()
WHERE slug = 'chilmark-research' AND full_name = 'John Moore';

-- Galen Growth — remove incorrect contact (Yoann Sapanel is not Galen Growth leadership).
DELETE FROM company_contacts
WHERE slug = 'galen-growth' AND full_name = 'Yoann Sapanel';

-- Galen Growth — add the correct Founder/CEO.
INSERT INTO company_contacts (slug, full_name, job_title, email, linkedin_url, source, status, notes, created_at, updated_at)
SELECT 'galen-growth', 'Julien de Salaberry', 'Founder & CEO', NULL,
       'https://ch.linkedin.com/in/desalaberry', 'OTHER', 'IDENTIFIED',
       'Founder-CEO; direct decision-maker. Based in Basel, Switzerland. Confirmed via web search 2026-10-18 — corrects prior doc error that listed Yoann Sapanel (not Galen Growth leadership).',
       NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM company_contacts WHERE slug = 'galen-growth' AND full_name = 'Julien de Salaberry'
);

-- Trilliant Health — Hal Andrews confirmed as CEO via multiple sources; no direct
-- personal LinkedIn URL surfaced despite repeated search.
UPDATE company_contacts
SET notes = notes || ' | CEO role confirmed via multiple sources 2026-10-18; no direct personal LinkedIn URL found — only company-page mentions.',
    updated_at = NOW()
WHERE slug = 'trilliant-health' AND full_name = 'Hal Andrews';

-- KLAS Research — Adam Gale confirmed Co-Founder/CEO via multiple sources; no
-- direct personal LinkedIn URL surfaced despite repeated search.
UPDATE company_contacts
SET notes = notes || ' | Co-Founder/CEO role confirmed via multiple sources 2026-10-18; no direct personal LinkedIn URL found.',
    updated_at = NOW()
WHERE slug = 'klas-research' AND full_name = 'Adam Gale';

SELECT slug, full_name, job_title, linkedin_url FROM company_contacts
WHERE slug IN ('chilmark-research','galen-growth','trilliant-health','klas-research')
ORDER BY slug, full_name;
