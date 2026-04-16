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
