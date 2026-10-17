-- update_acquisition_contacts_tier2.sql
--
-- Tier-2 contact research pass (web search, 2026-10-18).
--
-- CORRECTION: Jason Krantz is no longer Definitive Healthcare's CEO — he
-- stepped back to Founder & Executive Chairman; Clay Ritchey became CEO
-- effective 2026-09-08. More importantly, Definitive Healthcare itself is
-- mid-acquisition: Krantz filed a Schedule 13D on 2026-09-02 disclosing a
-- 17.6% stake tied to weighing a rollover in Advent International's
-- take-private bid. A company actively being taken private is a poor
-- acquisition-outreach target right now — flagging confidence down.
UPDATE company_outreach
SET notes = notes || ' | UPDATE 2026-10-18: Advent International take-private bid in progress (13D filed 2026-09-02); Krantz is now Exec Chairman not CEO. Confidence downgraded — poor timing for outreach, revisit after the deal resolves.',
    updated_at = NOW()
WHERE slug = 'definitive-healthcare' AND purpose = 'ACQUISITION';

UPDATE company_contacts
SET job_title = 'Founder & Executive Chairman',
    notes = notes || ' | CORRECTION 2026-10-18: no longer CEO (Clay Ritchey became CEO 2026-09-08). Company is mid take-private process with Advent International — poor timing for outreach.',
    updated_at = NOW()
WHERE slug = 'definitive-healthcare' AND full_name = 'Jason Krantz';

-- Greenlight Guru — David DeRam's LinkedIn confirmed, but one secondary source
-- (theorg.com) lists him as Executive Chairman rather than CEO while most
-- others (Crunchbase, company blog) still show CEO — flagging the ambiguity
-- rather than asserting either with confidence.
UPDATE company_contacts
SET linkedin_url = 'https://www.linkedin.com/in/davidderam/',
    notes = notes || ' | LinkedIn confirmed 2026-10-18. Title ambiguity: most sources (Crunchbase, company blog) list CEO, one secondary source (theorg.com) lists Executive Chairman — verify current title before outreach.',
    updated_at = NOW()
WHERE slug = 'greenlight-guru' AND full_name = 'David DeRam';

SELECT slug, full_name, job_title, linkedin_url FROM company_contacts
WHERE slug IN ('definitive-healthcare', 'greenlight-guru')
ORDER BY slug, full_name;
