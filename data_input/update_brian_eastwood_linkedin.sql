-- update_brian_eastwood_linkedin.sql
-- Confirms Brian Eastwood's (Chilmark Research) LinkedIn URL, cross-checked
-- 2026-10-18 against a parallel manual research pass found in acquire/.
UPDATE company_contacts
SET linkedin_url = 'https://www.linkedin.com/in/brian-eastwood-5705a2a/',
    notes = notes || ' | LinkedIn confirmed 2026-10-18 via cross-check — "Brian Eastwood Writes, LLC," Greater Boston, UMass Boston alum, matches his Chilmark bio.',
    updated_at = NOW()
WHERE slug = 'chilmark-research' AND full_name = 'Brian Eastwood';

SELECT slug, full_name, linkedin_url FROM company_contacts
WHERE slug = 'chilmark-research' AND full_name = 'Brian Eastwood';
