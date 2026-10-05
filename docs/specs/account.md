# Account Area — AIHealthcare Reference

> Last updated: 2026-10-05

The Account Area covers everything a user interacts with before, during, and after account creation: the login page, self-registration for demo access, the subscriber profile and self-service portal, the pricing page that drives conversion, and the privacy policy. Together these pages form the subscription funnel and account lifecycle for the application.

---

## Pages

### `/login` — Authentication

**What it shows:**
A single-field login form (email + password) built on Spring Security's standard session-based authentication. The form action posts to `/login` (Spring Security's default handler). On failure, `?error` is appended to the URL and the template shows a generic error message. On success, Spring Security redirects to the page the user originally requested (the `SavedRequest` mechanism), or to `/dashboard` as the default.

**Data source:**
`AppUserDetailsService` loads users from the `app_users` table via `AppUserRepository.findByEmail()`. The `enabled` column is checked; a disabled account is rejected before the password is verified. SAML2 logins bypass this form entirely — they enter via the IdP-configured redirect URL.

**Access / tier gating:**
No authentication required (obviously). Spring Security's `permitAll()` rule covers `/login`.

**How users get here:**
Spring Security redirects unauthenticated users to `/login?redirectTo=<originalPath>`. Users navigating directly to any gated URL (e.g. `/dashboard`) are redirected here first.

---

### `/register` — DEMO Self-Registration

**What it shows:**
A one-step registration form: full name, email, and password. On successful submission, the account is created at tier `DEMO` with an expiration 7 days from registration. A welcome email with the demo expiration date is sent via `TransactionalEmailService`. The user is immediately logged in and redirected to `/dashboard`.

**Data source:**
`RegistrationController` reads `RegisterRequest` from the form body. It calls `AppUserService.register()`, which writes to `app_users`. A `Subscriber` record is also created in `subscribers` for the same email (active=true) so the user receives digest emails during the trial period.

**Access / tier gating:**
Public, no authentication required.

**How DEMO expiration works:**
DEMO expiration is passive — there is no scheduler that downgrades the account. When a DEMO user accesses a gated page, `TierResolver.resolveEffectiveTier()` checks `AppUser.demoExpiresAt` against the current time and treats expired DEMO accounts as FREE. The tier in `app_users` is never automatically changed from DEMO to FREE.

**What happens after DEMO expires:**
The user's effective tier becomes FREE (article archive limited to 7 days; most premium pages show upgrade prompts). Their data is not deleted. They remain subscribed to digest emails. They can self-upgrade to SUBSCRIBER via the Stripe checkout on `/pricing`.

---

### `/profile` — Subscriber Self-Service

**What it shows:**
The user's subscription status card: email, tier badge, usage stats (AI search queries used this month, articles read today), and a Stripe billing portal link. For SUBSCRIBER-tier users, the portal link opens the Stripe customer portal for managing the subscription (payment method, invoice history, cancellation). For FREE and DEMO users, the portal link is absent and a pricing CTA is shown instead.

**Data source:**
- User record: `AppUserRepository.findByEmail()` — supplies tier, enabled status, demoExpiresAt.
- Usage stats: `UsageMetrics` from `UsageMeteringService` — stores per-user, per-day usage counts in `usage_metrics` table with columns for `aiSearchCount`, `articlesRead`, and `lastResetAt`.
- Subscriber status: `SubscriberRepository.findByEmail()` — checks whether the user has an active `subscribers` record (affects digest email delivery).
- Stripe portal URL: generated at request time by `StripePortalService.createPortalSession(stripeCustomerId)` for users who have a Stripe customer ID stored on their `AppUser`.

**Access / tier gating:**
Authenticated users only. No tier minimum — all tiers (FREE, DEMO, SUBSCRIBER, ENTERPRISE, ADMIN) can view the profile page. The content adjusts based on tier: SUBSCRIBER+ see the Stripe portal link; others see the upgrade CTA.

**How data gets here:**
Usage data is written by `UsageMeteringService.recordAiSearchQuery()` and `UsageMeteringService.recordArticleRead()`, called from `AiSearchController` and `DashboardController` respectively. The Stripe customer ID on `AppUser` is written by `StripeWebhookController` when a `checkout.session.completed` event is received.

**Actions available:**
- `POST /profile/unsubscribe` — sets the user's `subscribers.active = false`. The Thymeleaf template shows this as "Unsubscribe from digest emails" with an `are you sure?` confirmation step. Does not affect the subscription tier or Stripe billing.
- `POST /profile/resubscribe` — sets `subscribers.active = true`.

---

### `/pricing` — Pricing and Upgrade CTA

**What it shows:**
A two-column tier comparison table: FREE (forever) vs. SUBSCRIBER ($49/month). Each column lists feature bullets with check/X marks. A "Get Started" button for FREE is hidden (users are already on the free tier if they reach this page authenticated). The SUBSCRIBER "Upgrade" button calls `POST /api/v1/stripe/checkout` to create a Stripe Checkout Session and redirects the browser to the resulting Stripe-hosted checkout URL.

**Data source:**
Static — no database reads. Tier features listed in the template are hardcoded. The pricing amount ($49/month) matches the `PRICE_ID` environment variable configured in the Stripe dashboard.

**Access / tier gating:**
Public, no authentication required. The Stripe checkout endpoint (`POST /api/v1/stripe/checkout`) requires authentication and will redirect to `/login` if called unauthenticated.

**How Stripe checkout works:**
`StripeCheckoutController.createCheckout()` creates a `Session` using the Stripe SDK with `mode=subscription`, `success_url=/pricing?success=true`, and `cancel_url=/pricing?cancelled=true`. On success, Stripe calls the webhook at `POST /api/v1/stripe/webhook`, which processes the `checkout.session.completed` event: creates a `Subscriber` record, upgrades `AppUser.tier` to SUBSCRIBER, and stores the Stripe `customerId` on the user record.

**What happens after payment:**
The Stripe webhook arrives asynchronously (typically within a few seconds). Until the webhook fires, the user is still on their pre-checkout tier. The profile page refreshes automatically after checkout via a `?success=true` query param that triggers a `meta refresh` in the template, giving the webhook time to process.

---

### `/privacy` — Privacy Policy

**What it shows:**
A static page with the privacy policy for bigskylabs.ai. Covers data collection practices, email usage, third-party services (AWS SES, Stripe, Perplexity), and a contact address (`newsletter@bigskylabs.ai`). No user-facing forms or interactive elements.

**Data source:**
Static — no database reads. The page is server-rendered by `PrivacyController` returning the `privacy.html` template. No model attributes are populated.

**Access / tier gating:**
Public, no authentication required.

**Why this page exists:**
The privacy policy was added as part of the AWS SES production access re-application (quota-increase case 178857329700837). Having a public, linkable privacy policy is an AWS SES requirement.

---

## Subscription Tier System

All five pages interact with the tier system. Here is how the tiers are defined and how they flow through the account lifecycle:

| Tier | How you get it | Key limits |
|---|---|---|
| `DEMO` | Self-registration via `/register` | Full access for 7 days. After expiration, effective tier is FREE. Never auto-downgraded in DB. |
| `FREE` | Default for demo-expired accounts; also manually assigned by admin | 7-day article archive; top-5 company sentiment; 10 deal signals; limited AI search |
| `FREE_PENDING` | Stripe checkout started but webhook not yet received | Short-lived; treated as FREE while payment processes |
| `SUBSCRIBER` | Stripe checkout completed; set by `StripeWebhookController` | Full archive; all pages; 100 deal signals; full AI search; Stripe billing portal |
| `ENTERPRISE` | Manually assigned by admin or via SAML JIT provisioning | Full access + `/enterprise/data` console |
| `ADMIN` | Manually assigned by admin | All access including `/admin/**` |

The effective tier for a user is resolved by `TierResolver.resolveEffectiveTier()`. For DEMO users it checks `demoExpiresAt < now()` and returns FREE if expired. All other tier checks use the stored value in `AppUser.tier` directly.

---

## Two-Table User Model

The application has two user tables with partially overlapping concerns:

| Table | Entity | What it stores |
|---|---|---|
| `app_users` | `AppUser` | Authentication credentials, role, tier, enabled flag, Stripe customer ID, SSO details |
| `subscribers` | `Subscriber` | Email, active flag for digest delivery. Also stores newsletter preferences. |

A user can exist in `app_users` without a matching `subscribers` row (e.g., if a welcome email failed). The inverse also exists: an early-stage subscriber added via the REST API (`POST /api/v1/subscribers`) without a corresponding `AppUser`. Code that needs both records must query both tables — there is no foreign key or JOIN enforced between them. The `RegistrationController` creates both records; the `StripeWebhookController` creates both records. No other flow guarantees both.

---

### `/settings/webhooks` — User Webhook Channels

**What it shows:**
A list of the current user's configured `WebhookChannel` records. Each row shows: channel label, channel type (SLACK, TEAMS, GENERIC_HTTP), the target webhook URL (truncated for display), the event types subscribed to (pipe-delimited list rendered as pills: DEAL_SIGNAL, REGULATORY_EVENT, TREND_ALERT, DIGEST_READY), active flag, last-triggered date, and action buttons (Edit, Delete, Test). A form above the list lets the user add a new channel by providing a label, type, URL, and event type checkboxes.

**Data source:**
`webhook_channels` table via `WebhookChannelPort.findByOwnerEmail(principal.getName())`. Queries are strictly owner-scoped — only the current user's channels are returned.

**Access / tier gating:**
Authenticated users only. All tiers (FREE, DEMO, SUBSCRIBER, ENTERPRISE, ADMIN) can access the webhooks page and create channels. No tier minimum. `principal.getName()` is the owner identifier — all reads and writes use the authenticated user's email as the owner key.

**How webhooks fire:**
When a triggering event occurs (e.g., a new `DealSignal` is saved), `WebhookDispatchService` queries all active channels subscribed to the matching event type and posts a JSON payload to each channel URL. `WebhookChannel.lastTriggeredAt` is updated on each delivery. Delivery failures are logged but do not retry — a failed webhook silently moves on.

**Actions available:**
- `POST /settings/webhooks/add` — create a new channel. The URL is validated to start with `https://` before save.
- `POST /settings/webhooks/{id}/test` — posts a `TEST_PING` payload to the channel URL immediately. Returns a success or failure flash message depending on the HTTP response from the target.
- `POST /settings/webhooks/{id}/delete` — deletes the channel record. Owner check is enforced: deleting another user's channel returns 404.
- `POST /settings/webhooks/{id}/toggle` — toggles the `active` flag. Inactive channels receive no deliveries.

**Known limitations:**
- No webhook delivery history. There is no log of past dispatches — only `lastTriggeredAt` timestamp is stored, with no per-delivery status.
- No retry on failure. A transient network error or 5xx response from the target silently drops the event.
- `POST /settings/webhooks/{id}/test` makes a synchronous HTTP call from the app server to the webhook URL. If the target URL is slow or unreachable, the test action blocks the request thread until the HTTP client timeout (30 seconds).
- The channel URL `https://` prefix check is done at the controller layer. The underlying `WebhookDispatchService` also enforces HTTPS-only via `RemoteEndpointGuard` — plain HTTP URLs that somehow bypass the controller check will be rejected at dispatch time.

---

## Known Limitations

- **DEMO tier never downgrades in the database.** An expired DEMO user's `AppUser.tier` column still reads `DEMO`. Any code that reads `tier` directly without going through `TierResolver` will see the wrong effective tier. All controllers in the app use `TierResolver` for this reason, but any future code that bypasses it will have a latent bug.
- **No email verification.** Self-registration at `/register` creates an account with no email ownership proof. Anyone can register with someone else's email address.
- **Stripe webhook timing gap.** Between when the user completes Stripe checkout and when the webhook fires, the user's tier is still their pre-upgrade tier. The `?success=true` meta-refresh is a workaround, not a guarantee. If the webhook fails to arrive (e.g., Stripe delivery failure), the user will have paid but not received the upgraded tier.
- **Unsubscribe does not cancel Stripe billing.** `POST /profile/unsubscribe` only sets `subscribers.active = false` — it does not cancel the Stripe subscription. The user will continue to be billed unless they also cancel via the Stripe portal.
- **Usage meters reset by date, not UTC midnight.** `UsageMeteringService` resets daily counters by comparing `lastResetAt` date in America/Chicago. If the server timezone differs, usage windows may not align with what users expect.
- **Profile Stripe portal link requires a stored customer ID.** If `AppUser.stripeCustomerId` is null (e.g., for manually-upgraded SUBSCRIBER users), the Stripe portal link is absent with no explanation to the user.
- **Password reset exists but is not linked from the login page.** `PasswordResetController` handles `GET /forgot-password` and `POST /reset-password`. The templates `forgot-password.html` and `reset-password.html` exist. However, the login page does not display a "Forgot password?" link, so users must know the URL directly.
