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
