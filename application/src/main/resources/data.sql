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
  4. Company press releases (Google for Health, Claude for Healthcare, OpenAI for Healthcare, Microsoft for Healthcare, Perplexity Health, Amazon Health)
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
    OpenAI for Healthcare, Microsoft for Healthcare, Perplexity Health, and Amazon Health current healthcare offerings.
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
       'I am analyzing Healthcare AI Frameworks from Claude for Healthcare, OpenAI for Healthcare, Google for Health, Microsoft for Healthcare, Perplexity Health, and Amazon Health. Please summarize each company''s offerings and features, pricing, developer tools, and market placement in HTML output so it can be used in a newsletter. Order features within each company section by last date updated (most recent first). Provide direct links to each item. Provide a final summary paragraph for each company. Provide a summary of the current state of this market in the Healthcare and AI segment.

Format requirements:
- Return a complete, self-contained HTML document with inline CSS only (no external stylesheets or JavaScript).
- Use a dark, professional newsletter aesthetic.
- Group content by company (Claude for Healthcare, OpenAI for Healthcare, Google for Health, Microsoft for Healthcare, Perplexity Health, Amazon Health) with clear section headers.
- Include a feature/capability table per company with columns: Feature, Details, Date Updated, Source Link.
- Include a pricing section per company.
- Include a strengths/weaknesses verdict per company.
- Include a market state section at the end with key statistics and a competitive timeline.
- All source citations must be real, working URLs.',
       'Monthly competitive landscape report: Claude for Healthcare, OpenAI for Healthcare, Google for Health, Microsoft for Healthcare, Perplexity Health, Amazon Health — offerings, pricing, developer tools. Returns a styled, self-contained HTML document.',
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
