# VOC-1: Voice, Content Quality & Social Post Editing

**Created:** 2026-10-01  
**Status:** Phase 1 in progress — Phases 2–4 queued

## Problem statement

Three distinct fixes needed:
1. **Rendering bug** — DigestNewsletterRenderer wraps article cards in `<div box-shadow>` which
   Outlook strips. Cards look like flat unstyled text on Windows Outlook.
2. **Prompt quality** — house-style.md bans clichés but never asks the LLM to take a position,
   state stakes, or end with an action. Output is informational but passive.
3. **No edit-before-post workflow** — no way to add Bill's voice, edit, or track posted content.

## Phase 1 — Digest rendering fix + house-style update (DO NOW)
- DigestNewsletterRenderer.java: convert outer article card div with box-shadow → table
- house-style.md: add Stakes/Action and Angle sections

## Phase 2 — "Your Take" field on social post generator
- social-post.html: add yourTake textarea before Generate button
- MarketSocialPostController: prepend yourTake to LinkedIn/Facebook body if non-blank
- No DB, no new types

## Phase 3 — DB-backed social post draft editing (SOC-2)
New types: SocialPostPlatform enum, SocialPostStatus enum, SocialPostDraft record
New ports: SocialPostDraftPort (outbound), ManageSocialPostDraftsUseCase (inbound)
New service: SocialPostDraftService
New persistence: SocialPostDraftEntity, SocialPostDraftRepository, SocialPostDraftAdapter
Controller: save draft on generate, GET /dashboard/social/drafts (list),
            GET /dashboard/social/drafts/{id}/edit (TinyMCE), POST save/mark-posted/delete
Templates: social-post-drafts.html, social-post-draft-edit.html (TinyMCE — already in pom)
~20 tests

## Phase 4 — LinkedIn posts end with a question
- buildLinkedInBody(): append instruction that final line must be a reader question
- house-style.md: add LinkedIn note
- No DB, no new types

## Order: Phase 2 before Phase 3 (yourTake field feeds into the persisted draft)
## TinyMCE 7.9.0 already in pom — no new dependencies for Phase 3
