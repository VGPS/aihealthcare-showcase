-- seed_outreach_from_prospects.sql
--
-- Copies the 41 prospect companies + 70 contacts from the Node CLI prospect tables
-- into the app's company_outreach and company_contacts tables.
--
-- REQUIREMENTS:
--   1. The app must have been started at least once (so JPA created the tables).
--   2. Run against local aihealthcaredb:
--        "C:/Program Files/PostgreSQL/16/bin/psql.exe" -h localhost -U admin -d aihealthcaredb -f data_input/seed_outreach_from_prospects.sql
--
-- IDEMPOTENT: uses INSERT ... ON CONFLICT DO NOTHING so safe to re-run.
-- Slug formula mirrors SlugUtils.toSlug(): lowercase → replace [^a-z0-9]+ with '-' → trim hyphens.

-- ── 1. Outreach rows ──────────────────────────────────────────────────────────
-- One EMPLOYMENT row per company that has a CTO contact.
-- One SUBSCRIPTION row per company that has Marketing/BD/Market Research contact.

INSERT INTO company_outreach (slug, purpose, status, notes, created_at, updated_at)
SELECT
    TRIM(BOTH '-' FROM REGEXP_REPLACE(LOWER(pc.name), '[^a-z0-9]+', '-', 'g')) AS slug,
    'EMPLOYMENT' AS purpose,
    'NOT_STARTED' AS status,
    'Seeded from prospect DB — ' || STRING_AGG(DISTINCT pct.full_name || ' (' || pct.title || ')', '; ') AS notes,
    NOW() AS created_at,
    NOW() AS updated_at
FROM prospect_company pc
JOIN prospect_contact pct ON pct.company_id = pc.id
WHERE pct.role_category = 'CTO'
GROUP BY pc.id, pc.name
ON CONFLICT (slug, purpose) DO NOTHING;

INSERT INTO company_outreach (slug, purpose, status, notes, created_at, updated_at)
SELECT
    TRIM(BOTH '-' FROM REGEXP_REPLACE(LOWER(pc.name), '[^a-z0-9]+', '-', 'g')) AS slug,
    'SUBSCRIPTION' AS purpose,
    'NOT_STARTED' AS status,
    'Seeded from prospect DB — ' || STRING_AGG(DISTINCT pct.full_name || ' (' || pct.title || ', ' || pct.role_category::text || ')', '; ') AS notes,
    NOW() AS created_at,
    NOW() AS updated_at
FROM prospect_company pc
JOIN prospect_contact pct ON pct.company_id = pc.id
WHERE pct.role_category IN ('MARKETING', 'BUSINESS_DEV', 'MARKET_RESEARCH')
GROUP BY pc.id, pc.name
ON CONFLICT (slug, purpose) DO NOTHING;

-- ── 2. Contacts ───────────────────────────────────────────────────────────────
-- One row per contact. linkedin_url and email carried over directly.
-- role_category stored in notes field for reference.

INSERT INTO company_contacts (slug, full_name, job_title, email, linkedin_url, source, status, notes, created_at, updated_at)
SELECT
    TRIM(BOTH '-' FROM REGEXP_REPLACE(LOWER(pc.name), '[^a-z0-9]+', '-', 'g')) AS slug,
    pct.full_name,
    pct.title AS job_title,
    NULLIF(pct.email, '') AS email,
    NULLIF(pct.linkedin_url, '') AS linkedin_url,
    'PERPLEXITY' AS source,
    'IDENTIFIED' AS status,
    pct.role_category::text || ' — confidence: ' || pct.confidence::text AS notes,
    NOW() AS created_at,
    NOW() AS updated_at
FROM prospect_company pc
JOIN prospect_contact pct ON pct.company_id = pc.id
ON CONFLICT DO NOTHING;

-- ── 3. Verify ─────────────────────────────────────────────────────────────────
SELECT 'company_outreach rows' AS table_name, COUNT(*) AS inserted FROM company_outreach
UNION ALL
SELECT 'company_contacts rows', COUNT(*) FROM company_contacts;
