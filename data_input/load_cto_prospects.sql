-- CTO Prospect loader — safe to re-run (idempotent)
-- Creates enums, tables, loads CSV, runs cross-reference join

-- ── Enums ──────────────────────────────────────────────────────────────────
DO $$ BEGIN
    CREATE TYPE ai_focus_t AS ENUM ('core', 'applied', 'none', 'unclear');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE email_status_t AS ENUM ('current_company', 'likely_stale', 'personal', 'no_email');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE confidence_t AS ENUM ('high', 'medium', 'low');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- ── Main table ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS cto_prospect (
    id               BIGSERIAL PRIMARY KEY,
    linkedin_slug    TEXT           NOT NULL UNIQUE,
    linkedin_url     TEXT           NOT NULL UNIQUE,
    full_name        TEXT           NOT NULL,
    title            TEXT,
    company          TEXT           NOT NULL,
    company_domains  TEXT[],
    city             TEXT           NOT NULL,
    location         TEXT,
    email            TEXT,
    email_status     email_status_t NOT NULL DEFAULT 'no_email',
    all_known_emails TEXT[],
    github_url       TEXT,
    ai_focus         ai_focus_t     NOT NULL DEFAULT 'unclear',
    ai_confidence    confidence_t,
    what_they_do     TEXT,
    industry         TEXT,
    ai_evidence_url  TEXT,
    source           TEXT           NOT NULL DEFAULT 'pplx_people_search',
    sourced_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    verified_at      TIMESTAMPTZ,
    contacted_at     TIMESTAMPTZ,
    notes            TEXT,
    CONSTRAINT uq_person_company UNIQUE (full_name, company),
    CONSTRAINT ck_email_shape CHECK (email IS NULL OR email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$')
);

CREATE INDEX IF NOT EXISTS idx_cto_segment   ON cto_prospect (ai_focus, email_status, city);
CREATE INDEX IF NOT EXISTS idx_cto_uncontact ON cto_prospect (sourced_at) WHERE contacted_at IS NULL;

-- ── Staging table (all TEXT — arrays arrive as ';'-delimited) ──────────────
DROP TABLE IF EXISTS cto_prospect_stg;
CREATE TABLE cto_prospect_stg (
    city             TEXT,
    full_name        TEXT,
    title            TEXT,
    company          TEXT,
    location         TEXT,
    linkedin_url     TEXT,
    linkedin_slug    TEXT,
    email            TEXT,
    email_status     TEXT,
    all_known_emails TEXT,
    github_url       TEXT,
    ai_focus         TEXT,
    what_they_do     TEXT,
    industry         TEXT,
    ai_evidence_url  TEXT,
    ai_confidence    TEXT,
    company_domains  TEXT
);
