# Slice — LinkedIn Feature Post Rotation

Config-driven rotation resolving a calendar date to the LinkedIn feature post
due that day, rendered as a copy-ready page at
`GET /dashboard/linkedin/features`.

No AI call anywhere in this slice — deliberately. It exercises the same
hexagonal skeleton as the AI slices (record, port, domain service, adapter,
controller, view) with a rule that is pure date arithmetic, which makes it the
clean contrast case for the course.

## Files added

| Layer | File |
|-------|------|
| domain/model | `FeaturePost.java`, `FeaturePostSlot.java`, `FeaturePostScreenshot.java` |
| domain/port/outbound | `FeaturePostPort.java` |
| domain/port/inbound | `RotateFeaturePostsUseCase.java` |
| domain/service | `FeaturePostRotationService.java` |
| infrastructure/config | `YamlFeaturePostAdapter.java`, `FeaturePostConfig.java` |
| web/controller | `FeaturePostController.java` |
| resources | `templates/feature-post.html`, `linkedin/feature-posts.yml` |
| test | `FeaturePostRotationServiceTest.java`, `YamlFeaturePostAdapterTest.java`, `test/resources/linkedin/test-feature-posts.yml` |

Nothing existing was modified. `LinkedInPostController` and `nav.html` are
untouched — see "Wiring left to you" below.

## Design decisions worth knowing

**Rotation is stateless.** Week position is `floorMod(weeksBetween(anchorMonday,
mondayOf(date)), cycleWeeks)`. No stored cursor, no "last posted" column,
nothing to drift. Floor-modulo rather than `%` so dates before the anchor wrap
backwards instead of producing a negative week — there is a test for exactly
that, because `%` would have looked fine until someone previewed last month.

**Cycle length is derived, not configured.** It's the highest week number in
the YAML. Adding a seventh week of posts extends the rotation with no code or
config change.

**The domain package stays framework-free.** `FeaturePostRotationService` has
no Spring or Lombok imports, so its bean is defined in `FeaturePostConfig`
rather than annotated — matching how the rest of the domain is wired. Per
CONVENTIONS §6 it logs through the JDK's `System.Logger` instead of `@Slf4j`,
which isn't available inside `domain`. **If your other domain services do use
`@Slf4j`, switch this one to match** — swap the `System.Logger` field for the
annotation and the three `log.log(Level.X, () -> "...")` calls for
`log.debug(...)`. It's a five-line change and consistency beats my guess.

**A broken content file must not break the app.** The adapter logs and returns
an empty library rather than failing startup, and one malformed post is skipped
rather than costing the other twenty-nine. Marketing content should never take
down the platform.

**Meta tokens resolve, author placeholders don't.** `{{trial_url}}` is
substituted at load from the YAML's own `meta:` block. `{{COMPANY}}` is left
exactly as written, and `FeaturePost.unresolvedTokens()` reports it so the page
can warn before you paste a literal `{{COMPANY}}` into LinkedIn. Blanking
unknown tokens would have hidden the very thing the check exists to catch.

**Weekends show the next post rather than an empty page**, so a Sunday morning
can be spent drafting Monday's.

## Wiring left to you

Three small edits I deliberately didn't make, because I couldn't read the files
to make them safely:

1. **Nav link** — add a Feature Posts entry to `fragments/nav.html` pointing at
   `/dashboard/linkedin/features`. The controller passes `'linkedin'` as the
   nav fragment's active key, matching `linkedin-post.html`.
2. **Security** — `/dashboard/**` already requires login per the README, so the
   page inherits that. Confirm you don't want it ADMIN-only; it's an authoring
   tool, not subscriber content.
3. **Optional config** — both properties have working defaults, so
   `application.yml` needs nothing unless you want to override:

```yaml
aihealthcare:
  linkedin:
    feature-posts-location: classpath:linkedin/feature-posts.yml
    rotation-anchor: 2026-09-07   # the Monday week 1 starts on
```

## Verification status — read this

**Verified here.** The domain layer compiles clean under `javac -Xlint:all`
with zero warnings, and all 37 rotation and record assertions pass against the
real code: slot resolution, cycle wraparound, pre-anchor wraparound, weekend
handling, unfilled-slot skipping, week ordering, schedule sorting, id lookup,
empty-library safety, anchor normalisation, token detection, hashtag
immutability, and every validation guard.

The real `feature-posts.yml` was also parsed under exactly the rules the
adapter applies: 30 posts, all 30 slots unique and weekday-valid, a 6-week
cycle, every body under 3,000 characters after substitution, no unresolved
tokens in any body/hook/first_comment, the trial URL present in all 30
first-comment blocks, 4 typed prompts, and 2 posts flagged CRITICAL for
redaction (`developer-api`, `webhooks`).

**Not verified here.** `YamlFeaturePostAdapter`, `FeaturePostConfig`,
`FeaturePostController`, the Thymeleaf template, and the two JUnit test classes
were never compiled — this cloud session's egress policy blocks Maven Central
(HTTP 403), so Spring, Lombok, and JUnit couldn't be resolved. Their logic was
exercised by proxy (the YAML simulation above) but not by the compiler.

Run `/test-run` locally before trusting them. The most likely papercuts are
import paths if your `fragments/head` or `fragments/nav` signatures differ from
what `linkedin-post.html` uses, and whether `jakarta.annotation.PostConstruct`
matches your Jakarta version.

## Next slice, if you want one

A `@Scheduled` weekday reminder through the existing `WebhookNotificationPort`
— "today's feature post is Regulatory Alerts, here's the shot list" into Slack
at 8am. All the pieces exist; it's a scheduler and a message template.
