# Market Analysis — Sequential Merge Checklist

For running Slices 2.1 → 2.3 → 3.3 one at a time against the shared `ImpactClassificationResponse` / `ImpactClassificationPrompts` DTO. Work through this once per slice, in order. Do not start the next slice's Claude Code session until its predecessor's checklist is fully checked off.

Order: **Slice 2.1 (guidance) → Slice 2.3 (regulatory) → Slice 3.3 (deal terms)**

---

## Before starting Slice 2.1

- [ ] Confirm Slice 1.6 (full pipeline) and Slice 1.5 (classifier) are merged to mainline.
- [ ] Confirm no other session currently has uncommitted changes to `infrastructure/marketanalysis/ai/ImpactClassificationResponse.java` or `ImpactClassificationPrompts.java`.
- [ ] Note the current latest Flyway/migration version number in `db/migration/` — the 2.1 prompt needs this for `V<next>__create_guidance_history.sql`.
- [ ] Paste the Slice 2.1 prompt into a fresh Claude Code Plan Mode session.

## During Slice 2.1

- [ ] Review the proposed plan before letting it execute — confirm it reads the current DTO state first rather than assuming it's untouched.
- [ ] Confirm the plan adds the guidance field as an *additional* optional field, not a restructure of the DTO.

## After Slice 2.1 implementation

- [ ] Run the full test suite (not just new tests) — confirm all pass, including pre-existing Slice 1.5 classifier tests.
- [ ] Review the diff on `ImpactClassificationResponse.java` and `ImpactClassificationPrompts.java` — confirm only additive changes (new optional field/prompt instructions), nothing removed or restructured.
- [ ] Confirm the new `guidance_history` migration file uses the correct next version number (re-check — it may have shifted if anything else merged since you noted it).
- [ ] Confirm `GuidanceComparison`/initial-guidance handling, unique constraint, and end-to-end enrichment acceptance criteria all pass.
- [ ] Merge Slice 2.1 into mainline.
- [ ] Pull latest mainline into your next Claude Code session's working branch before starting Slice 2.3.

---

## Before starting Slice 2.3

- [ ] Confirm Slice 2.1 is merged (previous section fully checked).
- [ ] Confirm your working branch is up to date with mainline (has Slice 2.1's DTO changes).
- [ ] Note the current latest migration version number again — it has changed since Slice 2.1 added one.
- [ ] Paste the Slice 2.3 prompt into a fresh Claude Code Plan Mode session.

## During Slice 2.3

- [ ] Review the proposed plan — confirm it reads the current DTO state first (which now includes Slice 2.1's guidance field) and adds its own fields alongside it.
- [ ] Confirm the plan does not touch or remove the guidance field Slice 2.1 added.

## After Slice 2.3 implementation

- [ ] Run the full test suite — confirm all pass, including Slice 1.5's classifier tests AND Slice 2.1's guidance tests.
- [ ] Review the diff on `ImpactClassificationResponse.java` and `ImpactClassificationPrompts.java` — confirm Slice 2.1's fields are untouched and only new regulatory fields were added.
- [ ] Confirm the `regulatory_tracker` migration uses the correct next version number.
- [ ] Confirm the partial unique index (`docket_id IS NOT NULL`) acceptance criterion passes — verify two null-docket rows for the same jurisdiction don't collide.
- [ ] Confirm the deadline-sweep idempotency behavior (T-7/T-1 surfacing, not daily) is implemented and tested.
- [ ] Merge Slice 2.3 into mainline.
- [ ] Pull latest mainline into your next Claude Code session's working branch before starting Slice 3.3.

---

## Before starting Slice 3.3

- [ ] Confirm Slice 2.3 is merged (previous section fully checked).
- [ ] Confirm your working branch is up to date with mainline (has both Slice 2.1's and Slice 2.3's DTO changes).
- [ ] Paste the Slice 3.3 prompt into a fresh Claude Code Plan Mode session.

## During Slice 3.3

- [ ] Review the proposed plan — confirm it reads the current DTO state first (which now includes both guidance and regulatory fields) and adds deal-terms fields alongside them.
- [ ] Confirm the plan does not touch or remove the guidance or regulatory fields from 2.1/2.3.

## After Slice 3.3 implementation

- [ ] Run the full test suite — confirm all pass, including Slice 1.5, Slice 2.1, and Slice 2.3 tests.
- [ ] Review the diff on `ImpactClassificationResponse.java` and `ImpactClassificationPrompts.java` — confirm only the new `dealTerms` optional field was added, nothing else changed.
- [ ] Confirm `dealTerms` is only populated for `M_AND_A`/`MAJOR_PARTNERSHIP` categories and stays null/absent for other categories (including `EARNINGS` and `REGULATORY`, which now have their own fields from 2.1/2.3 — confirm those still populate correctly and independently of `dealTerms`).
- [ ] Confirm the `UNDISCLOSED`/`PARTIAL`/`FULL` disclosure-level fixtures all pass, especially the "no guessed split" case.
- [ ] Merge Slice 3.3 into mainline.

---

## Final verification (after all three are merged)

- [ ] Run the full test suite one more time on mainline post-merge — confirm Slices 1.5, 2.1, 2.3, and 3.3 all coexist cleanly.
- [ ] Manually inspect the final `ImpactClassificationResponse.java` — it should now contain three additive optional-field groups (guidance, regulatory metadata, deal terms) layered on the original Slice 1.5 shape, with nothing overwritten.
- [ ] Manually inspect `ImpactClassificationPrompts.java` — confirm the prompt instructions for each category (EARNINGS, REGULATORY, M_AND_A/MAJOR_PARTNERSHIP) are distinct and non-conflicting.
- [ ] Spot-check one end-to-end digest generation run and confirm an EARNINGS entry, a REGULATORY entry, and an M_AND_A entry each populate their respective slice's fields correctly and don't leak into each other's categories.
- [ ] Update the "Cross-slice notes" section of the implementation-slices document to mark all three as complete (not just decided/planned).

---

## If something goes wrong

- **Merge conflict despite sequential execution:** means something else touched the DTO between sessions (e.g. a manual edit, or a fourth slice not covered by this plan). Stop and diff manually rather than auto-resolving — these are structured-output schema fields, and a bad auto-merge can silently break the LLM's response parsing.
- **A later slice's tests fail against an earlier slice's fields:** most likely the classifier prompt for one category is bleeding into another (e.g. deal-terms extraction instructions being applied to EARNINGS entries). Check `ImpactClassificationPrompts.java` category-gating logic first.
- **Unsure whether it's safe to resume Slice 2.3 or 3.3 after a gap in time:** re-run this checklist's "Before starting" section for that slice from scratch — don't assume yesterday's "up to date with mainline" is still true today.
