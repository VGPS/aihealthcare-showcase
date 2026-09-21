-- Prospect Outreach Schema
-- Three-table hierarchy: company → contact → outreach touch
-- Safe to re-run (all CREATE IF NOT EXISTS)

-- ── Enums (reuse existing ai_focus_t, email_status_t, confidence_t if present) ──

DO $$ BEGIN
    CREATE TYPE role_category_t AS ENUM
        ('CTO', 'MARKETING', 'BUSINESS_DEV', 'SALES', 'CLINICAL', 'OTHER');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE outreach_channel_t AS ENUM
        ('LINKEDIN_NOTE', 'LINKEDIN_INMAIL', 'EMAIL', 'OTHER');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE outreach_outcome_t AS ENUM
        ('PENDING', 'CONNECTED', 'REPLIED_POSITIVE', 'REPLIED_NEGATIVE',
         'BOUNCED', 'NO_RESPONSE');
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- ── prospect_company ──────────────────────────────────────────────────────────
-- One row per target company. Links back to healthcare_ai_companies directory.

CREATE TABLE IF NOT EXISTS prospect_company (
    id          BIGSERIAL    PRIMARY KEY,
    hac_id      TEXT         REFERENCES healthcare_ai_companies(company_id) ON DELETE SET NULL,
    name        TEXT         NOT NULL,
    domain      TEXT         NOT NULL UNIQUE,
    sector      TEXT,
    sub_sector  TEXT,
    notes       TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ── prospect_contact ──────────────────────────────────────────────────────────
-- One row per person at a prospect company (CTO, CMO, VP BD, etc.).
-- Multiple contacts per company are expected as the Marketing/BD sweeps run.

CREATE TABLE IF NOT EXISTS prospect_contact (
    id              BIGSERIAL        PRIMARY KEY,
    company_id      BIGINT           NOT NULL REFERENCES prospect_company(id) ON DELETE CASCADE,
    full_name       TEXT             NOT NULL,
    title           TEXT,
    role_category   role_category_t  NOT NULL DEFAULT 'OTHER',
    linkedin_url    TEXT,
    email           TEXT,
    email_status    email_status_t   NOT NULL DEFAULT 'no_email',
    confidence      confidence_t,
    sourced_at      TIMESTAMPTZ      NOT NULL DEFAULT now(),
    notes           TEXT,
    CONSTRAINT uq_contact_per_company UNIQUE (full_name, company_id),
    CONSTRAINT ck_contact_email CHECK (email IS NULL OR email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$')
);

CREATE INDEX IF NOT EXISTS idx_contact_company  ON prospect_contact (company_id);
CREATE INDEX IF NOT EXISTS idx_contact_role     ON prospect_contact (role_category);
CREATE INDEX IF NOT EXISTS idx_contact_email    ON prospect_contact (email_status);

-- ── prospect_outreach ─────────────────────────────────────────────────────────
-- One row per touch. A contact may have many touches across channels over time.

CREATE TABLE IF NOT EXISTS prospect_outreach (
    id                BIGSERIAL          PRIMARY KEY,
    contact_id        BIGINT             NOT NULL REFERENCES prospect_contact(id) ON DELETE CASCADE,
    channel           outreach_channel_t NOT NULL,
    template_name     TEXT,              -- e.g. 'cto-clinical-ai', 'cto-payer-ai'
    message_text      TEXT,              -- full text of what was sent
    sent_at           TIMESTAMPTZ        NOT NULL DEFAULT now(),
    reply_received_at TIMESTAMPTZ,
    reply_text        TEXT,
    outcome           outreach_outcome_t NOT NULL DEFAULT 'PENDING',
    notes             TEXT
);

CREATE INDEX IF NOT EXISTS idx_outreach_contact ON prospect_outreach (contact_id);
CREATE INDEX IF NOT EXISTS idx_outreach_outcome ON prospect_outreach (outcome);
CREATE INDEX IF NOT EXISTS idx_outreach_sent    ON prospect_outreach (sent_at DESC);

-- ── Status view ───────────────────────────────────────────────────────────────
-- Quick dashboard: one row per contact with latest touch summary.

CREATE OR REPLACE VIEW prospect_status AS
SELECT
    pc.name                                  AS company,
    pc.domain,
    c.full_name,
    c.title,
    c.role_category,
    c.email,
    c.email_status,
    c.linkedin_url,
    COUNT(o.id)                              AS touch_count,
    MAX(o.sent_at)                           AS last_contacted_at,
    (array_agg(o.channel ORDER BY o.sent_at DESC))[1]  AS last_channel,
    (array_agg(o.outcome ORDER BY o.sent_at DESC))[1]  AS last_outcome
FROM   prospect_contact c
JOIN   prospect_company pc ON pc.id = c.company_id
LEFT   JOIN prospect_outreach o ON o.contact_id = c.id
GROUP  BY pc.name, pc.domain, c.full_name, c.title, c.role_category,
          c.email, c.email_status, c.linkedin_url
ORDER  BY last_contacted_at DESC NULLS LAST, pc.name, c.role_category;
