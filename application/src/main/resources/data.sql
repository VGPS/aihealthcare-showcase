-- Seed the AI Healthcare topic.
-- WHERE NOT EXISTS guard prevents duplicate insertion on context restarts.
INSERT INTO topics (id, name, slug, prompt_context, tone, active)
SELECT 1,
       'AI Healthcare',
       'ai-healthcare',
       'Focus on clinical AI, FDA approvals, and healthcare interoperability.',
       'Professional and concise',
       true
WHERE NOT EXISTS (SELECT 1 FROM topics WHERE id = 1);

-- Seed default prompt variants for evaluation testing.
-- V1: concise style (baseline) — mirrors the production summarize-articles.txt template.
INSERT INTO prompt_variants (variant_id, name, template_text, description, created_at)
SELECT 'summarize-v1-concise',
       'Concise Baseline',
       'You are an expert medical and technology journalist writing a weekly AI-in-Healthcare newsletter.

Tone instruction: {toneInstruction}
Topic: {topic}

Below are {articleCount} article(s). Summarize them into a single concise newsletter section (2-4 sentences).

--- ARTICLES ---
{articles}
--- END ARTICLES ---

Respond in EXACTLY this format:
HEADLINE: <one punchy sentence>
SUMMARY: <2-4 sentences, cite specific findings>
SECTION_TYPE: <WHAT_SHIPPED | ARCHITECTURE_NOTE | FAILURE_MODE | POLICY_WATCH | TOOL_PICK>',
       'Baseline concise prompt — short summaries for quick scanning',
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM prompt_variants WHERE variant_id = 'summarize-v1-concise');

-- V2: detailed style — longer summaries with more context and attribution.
INSERT INTO prompt_variants (variant_id, name, template_text, description, created_at)
SELECT 'summarize-v2-detailed',
       'Detailed Analysis',
       'You are an expert medical and technology journalist writing a weekly AI-in-Healthcare newsletter.

Tone instruction: {toneInstruction}
Topic: {topic}

Below are {articleCount} article(s). Provide a comprehensive newsletter section with detailed analysis (4-8 sentences). Include specific data points, author attributions, and implications for practitioners.

--- ARTICLES ---
{articles}
--- END ARTICLES ---

Respond in EXACTLY this format:
HEADLINE: <one punchy sentence summarising the key development>
SUMMARY: <4-8 sentences with data points, attributions, and practitioner implications>
SECTION_TYPE: <WHAT_SHIPPED | ARCHITECTURE_NOTE | FAILURE_MODE | POLICY_WATCH | TOOL_PICK>',
       'Detailed prompt — longer summaries with data points and practitioner implications',
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM prompt_variants WHERE variant_id = 'summarize-v2-detailed');

-- ---------------------------------------------------------------------------
-- Search engine harvest prompts (Slice 6 — Prompt Refactoring)
-- GooglePrompt: broad discovery query for finding sources across categories.
-- PerplexityPrompt: deep-research query that demands extracted page content.
-- ---------------------------------------------------------------------------
INSERT INTO search_prompts (engine, name, template_text, description, active)
SELECT 'GOOGLE',
       'Google Broad Discovery',
       'Search for the most recent and relevant content about AI in Healthcare on the topic: {topic}.

Prioritize these source types (in order):
  1. Peer-reviewed papers and clinical studies
  2. FDA, CMS, WHO, and government regulatory announcements
  3. Hospital and health-system publications
  4. Company press releases (Google for Health, Claude for Healthcare, OpenAI for Healthcare, Microsoft for Healthcare, Perplexity Health, Amazon Health, Epic Systems)
  5. Conference proceedings and technical reports
  6. GitHub repositories for open-source healthcare AI tools (include README link)
  7. Hugging Face models tagged for medical, clinical, or biomedical use (include model card link)

For each result return:
  - Title
  - Source name and type
  - Date published
  - URL

Group results into: Clinical AI | Regulatory/Policy | Market/News | Technical/Programming
Exclude generic blog posts and social media unless uniquely authoritative.',
       'Broad discovery query — finds titles, source types, dates, and URLs across four content categories. Designed for use with Google Custom Search or Serper API.',
       true
WHERE NOT EXISTS (SELECT 1 FROM search_prompts WHERE engine = 'GOOGLE');

INSERT INTO search_prompts (engine, name, template_text, description, active)
SELECT 'PERPLEXITY',
       'Perplexity Deep Research',
       'You are my research assistant for an AI in Healthcare newsletter.
Find the most substantive, current, and credible web sources on this topic: {topic}.

Do not return only links or generic search results.
For each result, either:
  - Open the page and extract the relevant content, or
  - If the page cannot be opened, clearly say so and give only the link.

Prioritize primary sources first: peer-reviewed papers, hospital/health-system publications,
government or regulator sites, clinical organizations, company press releases, and conference
proceedings.

Also include:
  - Programming-related content: open-source healthcare AI projects with GitHub repo links,
    README summaries, and tool descriptions.
  - Company summaries: Google for Health (MedGemma, Health AI), Claude for Healthcare,
    OpenAI for Healthcare, Microsoft for Healthcare, Perplexity Health, Amazon Health, and Epic Systems (Healthcare Intelligence, AI-embedded EHR) current healthcare offerings.
  - Hugging Face healthcare LLMs: models tagged for medical-QA, radiology, EHR, or
    clinical NLP — with model card links and brief descriptions.

For each source return:
  - Title
  - Source type (academic | regulatory | company | open-source | huggingface)
  - Date published
  - Why it matters for AI in healthcare
  - 3 to 5 bullet points of substantive findings
  - Direct quotes only when essential and kept brief
  - URL

Also provide:
  - A 3-sentence synthesis of the main trend across all sources
  - Any disagreements or uncertainty across sources
  - A confidence rating for the overall quality of evidence (High / Medium / Low)

Group results into: Clinical AI | Regulatory/Policy | Market/News | Technical/Programming

Prefer results whose page content can be opened and extracted. If a result is only a link,
treat it as lower priority unless it is highly authoritative.',
       'Deep-research query — demands extracted page content with per-source bullet points, citations, synthesis, and confidence rating. Designed for the Perplexity Sonar API.',
       true
WHERE NOT EXISTS (SELECT 1 FROM search_prompts WHERE engine = 'PERPLEXITY');

-- ---------------------------------------------------------------------------
-- Market Intelligence prompt (Slice 9) — monthly competitive landscape report.
-- Engine key: MARKET_INTELLIGENCE
-- Editable via PUT /api/v1/search-prompts/MARKET_INTELLIGENCE
-- ---------------------------------------------------------------------------
INSERT INTO search_prompts (engine, name, template_text, description, active)
SELECT 'MARKET_INTELLIGENCE',
       'Healthcare AI Market Intelligence',
       'I am analyzing Healthcare AI Frameworks from Claude for Healthcare, OpenAI for Healthcare, Google for Health, Microsoft for Healthcare, Perplexity Health, Amazon Health, and Epic Systems. Please summarize each company''s offerings and features, pricing, developer tools, and market placement in HTML output so it can be used in a newsletter. Order features within each company section by last date updated (most recent first). Provide direct links to each item. Provide a final summary paragraph for each company. Provide a summary of the current state of this market in the Healthcare and AI segment.

Format requirements:
- Return a complete, self-contained HTML document with inline CSS only (no external stylesheets or JavaScript).
- Use a dark, professional newsletter aesthetic.
- Group content by company (Claude for Healthcare, OpenAI for Healthcare, Google for Health, Microsoft for Healthcare, Perplexity Health, Amazon Health, Epic Systems) with clear section headers.
- Include a feature/capability table per company with columns: Feature, Details, Date Updated, Source Link.
- Include a pricing section per company.
- Include a strengths/weaknesses verdict per company.
- Include a market state section at the end with key statistics and a competitive timeline.
- All source citations must be real, working URLs.',
       'Monthly competitive landscape report: Claude for Healthcare, OpenAI for Healthcare, Google for Health, Microsoft for Healthcare, Perplexity Health, Amazon Health, Epic Systems — offerings, pricing, developer tools. Returns a styled, self-contained HTML document.',
       true
WHERE NOT EXISTS (SELECT 1 FROM search_prompts WHERE engine = 'MARKET_INTELLIGENCE');

-- V3: accessible style — plain language for non-specialist readers.
INSERT INTO prompt_variants (variant_id, name, template_text, description, created_at)
SELECT 'summarize-v3-accessible',
       'Plain Language',
       'You are a health technology writer making AI-in-Healthcare news accessible to a general audience.

Tone instruction: {toneInstruction}
Topic: {topic}

Below are {articleCount} article(s). Summarize them in plain language that a non-specialist can understand. Avoid jargon. Explain why this matters to patients and healthcare workers.

--- ARTICLES ---
{articles}
--- END ARTICLES ---

Respond in EXACTLY this format:
HEADLINE: <one clear sentence a non-specialist can understand>
SUMMARY: <3-5 sentences in plain language, explain relevance to patients and clinicians>
SECTION_TYPE: <WHAT_SHIPPED | ARCHITECTURE_NOTE | FAILURE_MODE | POLICY_WATCH | TOOL_PICK>',
       'Plain-language prompt — accessible to non-specialist readers',
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM prompt_variants WHERE variant_id = 'summarize-v3-accessible');

-- ---------------------------------------------------------------------------
-- Seed newsletter subscribers for local development and testing.
-- ---------------------------------------------------------------------------
INSERT INTO subscribers (email, name, active, subscribed_at, tier, unsubscribe_token)
SELECT 'wgblackmonall@gmail.com', 'Bill Blackmon', true, CURRENT_TIMESTAMP, 'SUBSCRIBER',
       'a1b2c3d4-e5f6-7890-abcd-ef1234567890'
WHERE NOT EXISTS (SELECT 1 FROM subscribers WHERE email = 'wgblackmonall@gmail.com');

INSERT INTO subscribers (email, name, active, subscribed_at, tier, unsubscribe_token)
SELECT 'dshihtzu@gmail.com', 'D Shihtzu', true, CURRENT_TIMESTAMP, 'SUBSCRIBER',
       'f9e8d7c6-b5a4-3210-fedc-ba9876543210'
WHERE NOT EXISTS (SELECT 1 FROM subscribers WHERE email = 'dshihtzu@gmail.com');

INSERT INTO subscribers (email, name, active, subscribed_at, tier, unsubscribe_token)
SELECT 'demo@gmail.com', 'Demo User', true, CURRENT_TIMESTAMP, 'SUBSCRIBER',
       'ddddd000-1111-2222-3333-444444444444'
WHERE NOT EXISTS (SELECT 1 FROM subscribers WHERE email = 'demo@gmail.com');

INSERT INTO subscribers (email, name, active, subscribed_at, tier, unsubscribe_token)
SELECT 'wku@gmail.com', 'WKU Tester', true, CURRENT_TIMESTAMP, 'SUBSCRIBER',
       'eeeee000-aaaa-bbbb-cccc-dddddddddddd'
WHERE NOT EXISTS (SELECT 1 FROM subscribers WHERE email = 'wku@gmail.com');

-- ---------------------------------------------------------------------------
-- Seed application users for local development and testing (Slice 29).
-- Hashes generated with BCrypt (cost factor 10).
-- ---------------------------------------------------------------------------
INSERT INTO app_users (email, password_hash, display_name, role, enabled, tier)
SELECT 'admin@gmail.com',
       '$2b$10$K7fAbvP1NM3iDl8JEHlR4O/Ct2gfxs5tSJAJ3aB62uYeob6BcX/3m',
       'Admin', 'ADMIN', true, 'SUBSCRIBER'
WHERE NOT EXISTS (SELECT 1 FROM app_users WHERE email = 'admin@gmail.com');

INSERT INTO app_users (email, password_hash, display_name, role, enabled, tier)
SELECT 'demo@gmail.com',
       '$2b$10$jEQkdOFoE4afcbjkMm2DY.8b.RSpLYZdE6qHYPGSAB1SWP3aV1v7e',
       'Demo User', 'USER', true, 'DEMO'
WHERE NOT EXISTS (SELECT 1 FROM app_users WHERE email = 'demo@gmail.com');

INSERT INTO app_users (email, password_hash, display_name, role, enabled, tier)
SELECT 'wgblackmonall@gmail.com',
       '$2b$10$nTtFY0TV/cB8/K0W5mEj2.YMg.EQhOrX.vBHAv0FkxYJtXx5g/fNa',
       'Bill Blackmon', 'ADMIN', true, 'SUBSCRIBER'
WHERE NOT EXISTS (SELECT 1 FROM app_users WHERE email = 'wgblackmonall@gmail.com');

-- Tester account — full ADMIN access for QA testing
INSERT INTO app_users (email, password_hash, display_name, role, enabled, tier)
SELECT 'wku@gmail.com',
       '$2b$10$j4xZ61UCdUQE4clOJKhOX.tbA6pY/7SqVwyZj1P/o4VW2XrEQKwTW',
       'WKU Tester', 'ADMIN', true, 'SUBSCRIBER'
WHERE NOT EXISTS (SELECT 1 FROM app_users WHERE email = 'wku@gmail.com');

-- Robert Blackmon — full ADMIN access
INSERT INTO app_users (email, password_hash, display_name, role, enabled, tier)
SELECT 'robertblackmon@gmail.com',
       '$2b$10$Z29vdt3HXJrUVDxjXoMqL./AsgaWWP0GUk4U/f/iQEfElZBpGYk/i',
       'Robert Blackmon', 'ADMIN', true, 'SUBSCRIBER'
WHERE NOT EXISTS (SELECT 1 FROM app_users WHERE email = 'robertblackmon@gmail.com');

-- Enterprise test user — ENTERPRISE tier for QA
INSERT INTO app_users (email, password_hash, display_name, role, enabled, tier)
SELECT 'enterprise@test.com',
       '$2a$10$e382iCnQT8GJWFpwiW9ANuSEgNUNuKb5vHtm6zPMU5K87xe9Cqf56',
       'Enterprise Tester', 'USER', true, 'ENTERPRISE'
WHERE NOT EXISTS (SELECT 1 FROM app_users WHERE email = 'enterprise@test.com');

-- Ensure all ADMIN-role users have ENTERPRISE tier in the subscribers table.
-- Uses ON CONFLICT so this is safe to re-run on any deployment.
INSERT INTO subscribers (email, active, name, subscribed_at, tier)
SELECT u.email, true, u.display_name, NOW(), 'ENTERPRISE'
FROM app_users u
WHERE u.role = 'ADMIN'
ON CONFLICT (email) DO UPDATE SET tier = 'ENTERPRISE';

-- ---------------------------------------------------------------------------
-- Fix company_relationships: replace UUID evidence_article_ids with actual
-- article URLs by joining back to news_articles table.
-- ---------------------------------------------------------------------------
UPDATE company_relationships cr
SET evidence_article_id = (
    SELECT CAST(na.url AS VARCHAR(500))
    FROM news_articles na
    WHERE na.article_id = cr.evidence_article_id
)
WHERE EXISTS (
    SELECT 1 FROM news_articles na WHERE na.article_id = cr.evidence_article_id
)
AND cr.evidence_article_id NOT LIKE 'http%';

-- ---------------------------------------------------------------------------
-- Fix company_relationships: replace concatenated fragment summaries with
-- the actual article title by joining on evidence URL.
-- ---------------------------------------------------------------------------
UPDATE company_relationships cr
SET summary = (
    SELECT na.title
    FROM news_articles na
    WHERE CAST(na.url AS VARCHAR(2048)) = cr.evidence_article_id
)
WHERE EXISTS (
    SELECT 1 FROM news_articles na
    WHERE CAST(na.url AS VARCHAR(2048)) = cr.evidence_article_id
);

-- ---------------------------------------------------------------------------
-- CI-32: AI Accountability Tracker seed data
-- 15 largest U.S. health systems, enforcement actions, and AI deployments
-- Source: health-systems-ai-accountability.pplx.md research dossier
-- ---------------------------------------------------------------------------

INSERT INTO health_systems (id, canonical_name, aliases_pipe, hq_state, system_type) VALUES
('kaiser',               'Kaiser Permanente',          'Kaiser Foundation Hospitals|Permanente Medical Groups',                         'CA', 'NONPROFIT'),
('hca',                  'HCA Healthcare',             'Hospital Corporation of America|HCA Inc.',                                      'TN', 'FOR_PROFIT'),
('commonspirit',         'CommonSpirit Health',        'Dignity Health|Catholic Health Initiatives',                                    'IL', 'NONPROFIT'),
('advocate',             'Advocate Health',            'Advocate Aurora Health|Aurora Health Care',                                     'NC', 'NONPROFIT'),
('upmc',                 'UPMC',                       'University of Pittsburgh Medical Center',                                       'PA', 'NONPROFIT'),
('providence',           'Providence',                 'Providence Health & Services|Swedish Health Services',                          'WA', 'NONPROFIT'),
('uc-health',            'UC Health',                  'UCSF Health|UCLA Health|UC San Diego Health|UC Davis Health',                   'CA', 'ACADEMIC'),
('trinity',              'Trinity Health',             'Mercy Health|Holy Cross Health',                                               'MI', 'NONPROFIT'),
('ascension',            'Ascension',                  'Ascension Health|Ascension Michigan',                                          'TX', 'NONPROFIT'),
('adventhealth',         'AdventHealth',               'Adventist Health System',                                                      'FL', 'NONPROFIT'),
('mass-general-brigham', 'Mass General Brigham',       'Partners HealthCare|Massachusetts General Hospital|Mass Eye and Ear',          'MA', 'NONPROFIT'),
('northwell',            'Northwell Health',           'North Shore-Long Island Jewish Health System',                                 'NY', 'NONPROFIT'),
('mayo',                 'Mayo Clinic',                'Mayo Foundation for Medical Education and Research',                           'MN', 'NONPROFIT'),
('tenet',                'Tenet Healthcare',           'Tenet Health|Detroit Medical Center|Clinica de la Mama',                       'TX', 'FOR_PROFIT'),
('ut-health',            'UT Health System',           'UT Southwestern Medical Center|MD Anderson Cancer Center|UT Health San Antonio','TX', 'ACADEMIC')
ON CONFLICT (id) DO NOTHING;

INSERT INTO enforcement_actions
    (id, health_system_id, entity_name_at_time, agency, theory, amount_usd, settlement_date, description, source_url)
VALUES
('ea-kaiser-2026',     'kaiser',              'Kaiser Foundation Health Plan',           'DOJ',      'FALSE_CLAIMS_ACT',    556000000, '2026-01-01',
 'Medicare Advantage risk-score inflation (upcoding) — largest MA risk-code FCA settlement on record',
 'https://www.justice.gov/opa/pr/kaiser-permanente-affiliates-pay-556m-resolve-false-claims-act-allegations'),
('ea-hca-2003',        'hca',                 'HCA Inc.',                                'DOJ',      'FALSE_CLAIMS_ACT',     16500000, '2003-01-01',
 'Stark Law violations — improper physician compensation at Parkridge Health System',
 'https://www.justice.gov/archives/opa/pr/hospital-chain-hca-inc-pays-165-million-settle-false-claims-act-allegations-regarding'),
('ea-commonspirit',    'commonspirit',        'Dignity Health',                          'DOJ',      'FALSE_CLAIMS_ACT',     37000000, '2014-01-01',
 'Medically unnecessary kyphoplasty procedures (predecessor entity Dignity Health)',
 'https://www.justice.gov/archives/opa/pr/dignity-health-agrees-pay-37-million-settle-false-claims-act-allegations'),
('ea-advocate',        'advocate',            'Aurora Health Care Inc.',                 'DOJ',      'FALSE_CLAIMS_ACT',     12000000, '2021-01-01',
 'Stark Law violations — improper physician compensation (predecessor entity Aurora Health Care)',
 'https://www.justice.gov/usao-edwi/pr/aurora-health-care-inc-agrees-pay-12-million-settle-allegations-under-false-claims-act'),
('ea-upmc-2023',       'upmc',                'UPMC',                                    'DOJ',      'FALSE_CLAIMS_ACT',      8500000, '2023-01-01',
 'Neurosurgery upcoding — Luketich FCA settlement',
 'https://www.phillipsandcohen.com/upmc-settles-for-8-5-million/'),
('ea-providence-2022', 'providence',          'Providence Health Services',              'STATE_AG', 'CONSUMER_PROTECTION', 157800000, '2022-01-01',
 'Charity-care violations: McKinsey Rev-Up collections against charity-eligible patients; Washington AG action',
 'https://www.atg.wa.gov/news/news-releases/ag-ferguson-providence-must-provide-1578-million-refunds-and-debt-relief'),
('ea-uchealth-2019',   'uc-health',           'Regents of the University of California', 'HHS_OIG', 'CMP',                   5300000, '2019-01-01',
 'UC San Diego upcoding — Civil Monetary Penalties Law violations for overcoded E&M services',
 'https://oig.hhs.gov/fraud/enforcement/the-regents-of-the-university-of-california-agreed-to-pay-53-million-for-allegedly-violating-the-civil-monetary-penalties-law-by-submitting-upcoded-claims/'),
('ea-trinity-2020',    'trinity',             'Trinity Health Genesis Network',          'DOJ',      'FALSE_CLAIMS_ACT',      4600000, '2020-01-01',
 'Impella mechanical circulatory support device overbilling — medically unnecessary procedures',
 'https://www.beckershospitalreview.com/legal-regulatory-issues/trinity-health-genesis-health-mercy-health-network-settle-4-6m-overbilling-case/'),
('ea-ascension-2021',  'ascension',           'Ascension Michigan',                      'DOJ',      'FALSE_CLAIMS_ACT',      2800000, '2021-01-01',
 'False Claims Act violations at Michigan facilities',
 'https://www.justice.gov/usao-edmi/pr/ascension-michigan-pay-28-million-resolve-false-claims-act-allegations'),
('ea-advent-2015',     'adventhealth',        'Adventist Health System',                 'DOJ',      'FALSE_CLAIMS_ACT',    115000000, '2015-01-01',
 'Wide-ranging FCA — billing fraud across specialties (predecessor entity Adventist Health System)',
 'https://www.justice.gov/archives/opa/pr/adventist-health-system-agrees-pay-115-million-settle-false-claims-act-allegations'),
('ea-mgb-2022',        'mass-general-brigham','Massachusetts General Hospital',          'DOJ',      'FALSE_CLAIMS_ACT',     14600000, '2022-01-01',
 'Concurrent surgery FCA — operating on two patients simultaneously without patient disclosure',
 'https://www.jdsupra.com/legalnews/massachusetts-general-hospital-reaches-7023796/'),
('ea-northwell-2018',  'northwell',           'Northwell Health',                        'HHS_OIG',  'CMP',                  12700000, '2018-01-01',
 'Spinal procedures not provided as claimed or medically unnecessary — Civil Monetary Penalties',
 'https://oig.hhs.gov/fraud/enforcement/northwell-health-agreed-to-pay-127-million-for-allegedly-violating-the-civil-monetary-penalties-law-by-submitting-claims-for-spinal-procedures-that-were-not-provided-as-claimed-or-medically-unnecessary/'),
('ea-mayo-2005',       'mayo',                'Mayo Clinic',                             'DOJ',      'FALSE_CLAIMS_ACT',      6500000, '2005-01-01',
 'NIH grant fraud',
 'https://www.justice.gov/archive/opa/pr/2005/May/05_civ_292.htm'),
('ea-tenet-2006',      'tenet',               'Tenet Healthcare Corporation',            'DOJ',      'FALSE_CLAIMS_ACT',    900000000, '2006-01-01',
 'Wide-ranging FCA — largest resolution in cohort excluding Kaiser 2026; predecessor entity settlement',
 'https://www.justice.gov/archive/opa/pr/2006/June/06_civ_406.html'),
('ea-ut-2022',         'ut-health',           'UT Southwestern Medical Center',          'DOJ',      'CONTROLLED_SUBSTANCES', 4500000, '2022-01-01',
 'Controlled Substances Act violations',
 'https://www.justice.gov/usao-ndtx/pr/ut-southwestern-pay-45-million-resolve-alleged-controlled-substance-act-violations')
ON CONFLICT (id) DO NOTHING;

INSERT INTO ai_deployments
    (id, health_system_id, vendor, product, domain, scale_metric, source_url, deployed_date)
VALUES
('ai-kaiser-ambient',   'kaiser',              'Abridge',              'Ambient Documentation',                       'CLINICAL',          '40 hospitals; 6M+ visits AI-transcribed in 2025',                                                            'https://about.kaiserpermanente.org/expertise-and-impact/public-policy/our-key-issues/artificial-intelligence',                   '2024-01-01'),
('ai-kaiser-rcm',       'kaiser',              'Epic',                 'ClaimsConnect (Tapestry-based)',              'REVENUE_CYCLE',     'Integrated claims management across 13.4M members',                                                           'https://about.kaiserpermanente.org/news/press-release-archive/kaiser-permanente-improves-member-experience-with-ai-enabled-clinical-technology', '2024-01-01'),
('ai-hca-rcm',          'hca',                 'Internal',             'Revenue Cycle AI (payer-denial-focused)',     'REVENUE_CYCLE',     '180+ hospitals; CFO cited as direct response to growing payer denial rates',                                   'https://www.beckershospitalreview.com/finance/where-hca-is-focusing-its-revenue-cycle-ai-efforts/',                              '2024-01-01'),
('ai-hca-ambient',      'hca',                 'Commure/Augmedix',     'Ambient Docs + SPOT Sepsis AI',              'CLINICAL',          '130+ hospitals with Timpani AI scheduling; SPOT sepsis early-warning AI',                                     'https://www.beckershospitalreview.com/healthcare-information-technology/ai/how-hca-healthcare-built-its-enterprise-ai-strategy/', '2024-01-01'),
('ai-cs-rcm',           'commonspirit',        'Midstream Health',     'AI Underpayment and Denial Agents',          'REVENUE_CYCLE',     '242 AI tools deployed; ~$100M annual value (vendor-reported)',                                                'https://www.fiercehealthcare.com/providers/jpm26-commonspirit-ceo-teases-new-divestures-outlines-ai-wins-and-pitfalls',          '2024-01-01'),
('ai-advocate-ambient', 'advocate',            'Microsoft/Nuance',     'DAX Copilot',                                'CLINICAL',          'Largest Microsoft DAX deployment in the country; Epic Agent Factory autonomous agents',                         'https://medcitynews.com/2025/04/ai-healthcare-advocate-technology-hospital/',                                                     '2024-01-01'),
('ai-advocate-imaging', 'advocate',            'Aidoc',                'AI Imaging Diagnostics',                     'CLINICAL',          'System-wide radiology triage AI',                                                                             'https://www.advocatehealth.org/news/advocate-health-deploys-ai-solution-to-redefine-diagnostic-excellence-through-agreement-with-aidoc', '2024-01-01'),
('ai-upmc-rcm',         'upmc',                'Internal',             'Claims-Data Predictive Models',              'REVENUE_CYCLE',     'Health plan denial predictive models; RPA claims statusing',                                                  'https://www.pachamber.org/media/the_current/upmc_ai_blog_article/',                                                              '2024-01-01'),
('ai-upmc-ambient',     'upmc',                'Abridge',              'Ambient Documentation',                      'CLINICAL',          '12,000+ clinicians on systemwide rollout',                                                                    'https://www.beckershospitalreview.com/healthcare-information-technology/ai/upmc-to-roll-out-abridges-ai-documentation-tool-systemwide/', '2024-01-01'),
('ai-prov-rcm',         'providence',          'Xsolis',               'Utilization Management AI',                  'REVENUE_CYCLE',     '$40M+ savings (vendor-reported); 91.8% AI prior-auth acceptance rate; 84% Epic AI draft acceptance',          'https://www.fiercehealthcare.com/sponsored/xsolis-ai-solutions-have-saved-providence-health-system-more-40-million',            '2024-01-01'),
('ai-uc-clinical',      'uc-health',           'UCSF Internal',        'Versa GenAI Platform',                       'CLINICAL',          'UCSF enterprise generative AI platform; UCLA appointed chief AI officer 2025',                                 'https://chancellor.ucsf.edu/news/now-available-versa-ucsf-generative-ai-platform',                                               '2024-01-01'),
('ai-uc-collections',   'uc-health',           'Experian',             'Collections AI / Propensity-to-Pay',         'FINANCIAL_SCORING', 'UC San Diego Health scores patients by propensity to pay; improved collections, reduced bad debt (vendor-reported)', 'https://www.experian.com/blogs/healthcare/case-study-how-ucsdh-improved-collections-and-reduced-bad-debt/', '2023-01-01'),
('ai-trinity-charity',  'trinity',             'Internal',             'Presumptive Charity Eligibility Scoring',    'FINANCIAL_SCORING', 'Automated presumptive charity scoring on ALL patients using address, ZIP code, and credit history',              'https://www.hfma.org/qa-one-systems-approach-to-presumptive-charity-care/',                                                       '2023-01-01'),
('ai-trinity-research', 'trinity',             'Truveta',              'Truveta Genome Project',                     'RESEARCH',          'Real-world genomic data platform for clinical research',                                                      'https://www.jhconline.com/trinity-health-and-the-potential-of-ai.html',                                                          '2024-01-01'),
('ai-ascension-rcm',    'ascension',           'R1 RCM',               'Full RCM Outsourcing with AI',               'REVENUE_CYCLE',     'Entire RCM outsourced to R1 through 2031 including AI-powered denials management',                            'https://www.r1rcm.com/news-and-press/r1-announces-rcm-partnership-expansion-and-extension-with-ascension/',                     '2023-01-01'),
('ai-ascension-cloud',  'ascension',           'Google',               'Project Nightingale / Cloud AI',             'CLINICAL',          'Google Cloud AI partnership; ambient documentation and AI nursing flowsheets',                                 'https://cloud.google.com/blog/topics/inside-google-cloud/our-partnership-with-ascension',                                         '2024-01-01'),
('ai-advent-rcm',       'adventhealth',        'Iodine Software',      'Clinical Documentation Integrity AI',        'REVENUE_CYCLE',     '>90% physician queries answered in one day; pre-bill AI denial prevention on every discharge record (vendor-reported)', 'https://iodinesoftware.com/customer-successes/adventhealth/',                             '2024-01-01'),
('ai-mgb-coding',       'mass-general-brigham','CodaMetrix',           'Autonomous Medical Coding',                  'REVENUE_CYCLE',     '>80% of radiology coding fully automated; AI targets appeals most likely to be overturned',                   'https://www.healthleadersmedia.com/revenue-cycle/spark-joy-vanquish-vapor-how-mass-general-brigham-uses-tech-rev-rcm',           '2024-01-01'),
('ai-mgb-ambient',      'mass-general-brigham','Abridge',              'Hybrid Ambient Documentation',               'CLINICAL',          '4,000+ providers; reduces after-hours documentation burden',                                                  'https://www.massgeneralbrigham.org/en/about/newsroom/press-releases/hybrid-ambient-documentation-reduces-after-hours-work',      '2024-01-01'),
('ai-northwell-rcm',    'northwell',           'Clinithink + XiFin',   'NLP Denials Mgmt + AI Appeals Agent',        'REVENUE_CYCLE',     '10-year Clinithink NLP deal; XiFin AI appeals agent; 30-35% initial denial rates from some payers',          'https://www.techtarget.com/revcyclemanagement/news/366600965/Northwell-Health-to-Use-AI-NLP-to-Improve-Revenue-Cycle-Management', '2024-01-01'),
('ai-mayo-research',    'mayo',                'Cerebras/Microsoft',   'Foundation Models on Genomic Data',          'RESEARCH',          '100,000+ genomes; Cerebras computing power; Optum360 AI-assisted coding and claims editing',                  'https://newsnetwork.mayoclinic.org/discussion/mayo-clinic-engages-cerebras-to-deliver-potent-computing-power-scale-ai-transformation/', '2024-01-01'),
('ai-tenet-rcm',        'tenet',               'Conifer/Google Cloud', 'End-to-End AI Revenue Cycle Management',     'REVENUE_CYCLE',     '17M encounters; $32B NPR processed through AI pipeline; Commure ambient AI across hospitals',                  'https://www.techtarget.com/revcyclemanagement/news/366634448/Google-Cloud-Conifer-team-up-for-end-to-end-AI-RCM',                '2024-01-01'),
('ai-ut-clinical',      'ut-health',           'Microsoft/Abridge',    'Ambient Documentation Head-to-Head Trial',   'CLINICAL',          'UT Southwestern head-to-head DAX vs Abridge trial (published JAMA); MD Anderson IDSO $100M data-science institute', 'https://pubmed.ncbi.nlm.nih.gov/41734793/',                                              '2024-01-01')
ON CONFLICT (id) DO NOTHING;
