# Slice SSO-1: SAML2 SSO + Multi-Tenant IdP Registry

## Context

SSO/SAML is the single biggest procurement blocker for healthcare enterprise customers.
Hospital systems and payers require SAML-based SSO (via Okta, Azure AD, PingFederate)
before their InfoSec teams will even evaluate a vendor. This slice adds SAML2 Service
Provider support with a per-customer IdP registry, just-in-time user provisioning, and
an admin UI for managing SSO connections.

**Current state:** Session-based form login with BCrypt passwords, `AppUserDetailsService`,
two roles (USER/ADMIN), five tiers (DEMO through ENTERPRISE). Zero OAuth2/SAML code exists.
The `SecurityConfig` has a single `filterChain` with `.formLogin()`.

**Goal:** Enterprise customers authenticate via their corporate IdP. First login auto-creates
an ENTERPRISE-tier account. Existing form login remains untouched for non-enterprise users.

---

## Architecture Decisions (locked)

1. **SAML2 only** (no OIDC in this slice) — healthcare enterprises overwhelmingly use SAML. OIDC can follow in a future slice.
2. **No Flyway** — JPA entities with `ddl-auto: create-drop`, same as every other entity.
3. **Flat packages** — records in `domain.model`, ports in `domain.port.outbound`, entities in `infrastructure.persistence`, etc.
4. **Single SP key pair** — one signing key for all tenants, configured in `application.yml`. Per-tenant SP keys are unnecessary complexity.
5. **Dual auth** — form login + SAML coexist. SSO users authenticate via SAML; non-SSO users use the existing form. No forced migration.
6. **JIT provisioning** — first SAML login auto-creates AppUser + Subscriber with ENTERPRISE tier. Existing users who SAML-login are linked (tier upgraded to ENTERPRISE, no password change).
7. **Password sentinel** — SSO-provisioned users get `passwordHash = "{SSO}"` (prevents form login; BCrypt comparison always fails). Existing users who link via SSO keep their password (can still form-login).
8. **Admin-only IdP management** — only ADMIN role can create/edit/delete IdP configurations.

---

## Domain Model

### Records (all in `domain.model`)

| Record | Fields | Notes |
|--------|--------|-------|
| `SsoIdentityProvider` | registrationId (PK slug), label, entityId, ssoUrl, certificate (PEM), metadataUrl (nullable), emailAttribute (default "email"), displayNameAttribute (default "displayName"), defaultTier (ENTERPRISE), active, createdAt, updatedAt | 12 fields |
| `SsoProvisioningEvent` | eventId (UUID), registrationId, email, action (CREATED/LINKED/LOGIN), occurredAt | 5 fields — audit trail |
| `SsoProvisioningAction` | CREATED, LINKED, LOGIN | enum — 3 values |

### Ports

| Port | Package | Kind | Methods |
|------|---------|------|---------|
| `ManageSsoProvidersUseCase` | `domain.port.inbound` | inbound | `create(SsoIdentityProvider)`, `update(SsoIdentityProvider)`, `delete(registrationId)`, `getById(registrationId)`, `getAll()`, `getAllActive()` |
| `SsoIdentityProviderPort` | `domain.port.outbound` | outbound | `save(SsoIdentityProvider)`, `findById(registrationId)`, `findAll()`, `findAllActive()`, `deleteById(registrationId)`, `existsById(registrationId)` |
| `SsoProvisioningEventPort` | `domain.port.outbound` | outbound | `record(SsoProvisioningEvent)`, `findByEmail(email)`, `findByRegistrationId(registrationId)` |

### Domain Service

`SsoProviderService` in `domain.service` — implements `ManageSsoProvidersUseCase`. Validates registrationId format (lowercase alphanumeric + hyphens), prevents duplicate registrationIds, delegates to ports.

`SsoProvisioningService` in `domain.service` — JIT provisioning logic:
- `provisionOrLink(email, displayName, registrationId)` -> `AppUser`
- If email exists in `app_users`: upgrade tier to ENTERPRISE, record LINKED event
- If email does not exist: create AppUser (role=USER, tier=ENTERPRISE, passwordHash="{SSO}", enabled=true) + Subscriber (tier=ENTERPRISE, active=true), record CREATED event
- On subsequent logins: record LOGIN event only

---

## Persistence (JPA)

### Entities

| Entity | Table | Notes |
|--------|-------|-------|
| `SsoIdentityProviderEntity` | `sso_identity_providers` | registrationId VARCHAR PK, label, entityId, ssoUrl, certificate TEXT, metadataUrl, emailAttribute, displayNameAttribute, defaultTier, active, createdAt, updatedAt |
| `SsoProvisioningEventEntity` | `sso_provisioning_events` | eventId UUID PK, registrationId FK, email, action, occurredAt |

### Repositories

- `SsoIdentityProviderRepository extends JpaRepository<SsoIdentityProviderEntity, String>` — `findAllByActiveTrue()`
- `SsoProvisioningEventRepository extends JpaRepository<SsoProvisioningEventEntity, UUID>` — `findByEmail(String)`, `findByRegistrationId(String)`

### Adapter

`SsoIdentityProviderAdapter` — implements both `SsoIdentityProviderPort` and `SsoProvisioningEventPort`. Standard `toDomain()`/`toEntity()` mapping.

---

## Security Integration

### Dependency

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-saml2-service-provider</artifactId>
</dependency>
```

### `DatabaseRelyingPartyRegistrationRepository`

**Package:** `infrastructure.config`

Implements Spring Security's `RelyingPartyRegistrationRepository`. Loads active `SsoIdentityProvider` records from DB and converts each to a `RelyingPartyRegistration`:
- `assertingPartyDetails`: entityId, ssoUrl, certificate from the DB record
- `registrationId`: the slug from the DB record
- SP entity ID: `{baseUrl}/saml2/service-provider-metadata/{registrationId}`
- ACS URL: `{baseUrl}/login/saml2/sso/{registrationId}`
- SP signing key: loaded from `application.yml` properties

### `SsoAuthenticationSuccessHandler`

**Package:** `infrastructure.config`

Implements `AuthenticationSuccessHandler`. Called after successful SAML authentication:
1. Extract email from `Saml2AuthenticatedPrincipal` using the IdP's configured `emailAttribute`
2. Extract displayName using `displayNameAttribute`
3. Call `SsoProvisioningService.provisionOrLink(email, displayName, registrationId)`
4. Build a `UsernamePasswordAuthenticationToken` with the provisioned user's roles and set it on `SecurityContextHolder`
5. Redirect to `/dashboard`

### `SecurityConfig` changes

Add `.saml2Login()` block alongside existing `.formLogin()`:

```java
.saml2Login(saml2 -> saml2
    .relyingPartyRegistrationRepository(relyingPartyRegistrationRepository)
    .authenticationManager(saml2AuthenticationManager)
    .successHandler(ssoAuthenticationSuccessHandler)
    .loginPage("/login")
)
```

Add SAML2 URLs to `permitAll()`:
- `/saml2/**`
- `/login/saml2/**`

Add to CSRF exclusions:
- `/login/saml2/**` (SAML ACS is a POST from the IdP)

### `application.yml` additions

```yaml
aihealthcare:
  sso:
    enabled: true
    sp:
      entity-id: https://app.bigskylabs.ai
      signing-key: ${SSO_SP_SIGNING_KEY:}
      signing-certificate: ${SSO_SP_SIGNING_CERT:}
```

---

## Web Layer

### Admin UI

| URL | Controller | Template | Notes |
|-----|-----------|----------|-------|
| `GET /admin/sso` | `SsoAdminController` | `sso-providers.html` | List all IdPs with status badges, add button |
| `GET /admin/sso/new` | `SsoAdminController` | `sso-provider-form.html` | Create form: registrationId, label, entityId, ssoUrl, certificate textarea, attribute mappings |
| `GET /admin/sso/{id}/edit` | `SsoAdminController` | `sso-provider-form.html` | Edit form (reuses create template) |
| `POST /admin/sso` | `SsoAdminController` | redirect | Create IdP |
| `POST /admin/sso/{id}` | `SsoAdminController` | redirect | Update IdP |
| `POST /admin/sso/{id}/delete` | `SsoAdminController` | redirect | Delete IdP |
| `GET /admin/sso/{id}/metadata` | `SsoAdminController` | XML response | Download SP metadata XML for this registration — give to the enterprise customer's IT team |

### REST API

| Method | Path | Notes |
|--------|------|-------|
| `GET` | `/api/v1/sso/providers` | List active providers (ADMIN) |
| `POST` | `/api/v1/sso/providers` | Create provider (ADMIN) |
| `PUT` | `/api/v1/sso/providers/{id}` | Update provider (ADMIN) |
| `DELETE` | `/api/v1/sso/providers/{id}` | Delete provider (ADMIN) |

### Login page update

Add "Enterprise SSO" section to `login.html` below the password form:
- Only visible when active IdPs exist (controller passes `ssoProviders` list to model)
- Each active IdP shows as a button: "Sign in with {label}" -> links to `/saml2/authenticate/{registrationId}`
- Styled as secondary buttons below a divider

---

## Build Order

```
Phase 1 — Domain + Persistence:
  1.1 Domain records: SsoIdentityProvider, SsoProvisioningEvent, SsoProvisioningAction enum
  1.2 Ports: SsoIdentityProviderPort, SsoProvisioningEventPort, ManageSsoProvidersUseCase
  1.3 Domain services: SsoProviderService, SsoProvisioningService
  1.4 JPA entities + repositories + SsoIdentityProviderAdapter
  1.5 Tests: domain records, services, adapter

Phase 2 — Security Integration:
  2.1 pom.xml: add spring-security-saml2-service-provider
  2.2 application.yml: SSO SP properties
  2.3 DatabaseRelyingPartyRegistrationRepository
  2.4 SsoAuthenticationSuccessHandler (JIT provisioning)
  2.5 SecurityConfig update: .saml2Login() + permitAll + CSRF
  2.6 AppConfig: wire SSO beans
  2.7 Tests: registration repo, success handler, security config

Phase 3 — Admin UI + Login:
  3.1 SsoAdminController (Thymeleaf CRUD)
  3.2 sso-providers.html + sso-provider-form.html templates
  3.3 SsoRestController (REST CRUD for API consumers)
  3.4 LoginController update: pass active IdPs to model
  3.5 login.html update: SSO buttons section
  3.6 Nav: "SSO Providers" link in admin dropdown
  3.7 Tests: SsoAdminControllerTest, SsoRestControllerTest
```

---

## Key Files to Modify

| File | Change |
|------|--------|
| `pom.xml` | Add `spring-security-saml2-service-provider` dependency |
| `SecurityConfig.java` | Add `.saml2Login()`, update `permitAll()` and CSRF exclusions |
| `LoginController.java` | Inject `SsoIdentityProviderPort`, pass active IdPs to model |
| `login.html` | Add SSO buttons section |
| `application.yml` | Add `aihealthcare.sso.*` properties |
| `AppConfig.java` | Wire SSO beans (services, handler, registration repo) |

## Reusable Existing Code

- `RegistrationService` — pattern for dual AppUser+Subscriber creation (reuse in `SsoProvisioningService`)
- `AppUserPort` / `SubscriberPort` — reuse for user lookup and save
- `TierResolver` — no changes needed; already resolves tier from Subscriber table
- `TransactionalEmailPort` — reuse for admin notification on new SSO provisioning
- `SecurityConfig.filterChain()` — extend, don't replace

---

## Testing

| Test class | Count | Focus |
|-----------|-------|-------|
| `SsoIdentityProviderTest` | ~6 | Domain record validation |
| `SsoProviderServiceTest` | ~6 | CRUD + validation logic |
| `SsoProvisioningServiceTest` | ~8 | JIT provisioning: new user, existing user link, subsequent login |
| `SsoIdentityProviderAdapterTest` | ~5 | @DataJpaTest persistence |
| `DatabaseRelyingPartyRegistrationRepositoryTest` | ~5 | DB -> RelyingPartyRegistration conversion |
| `SsoAuthenticationSuccessHandlerTest` | ~5 | Attribute extraction, provisioning delegation |
| `SsoAdminControllerTest` | ~7 | MockMvc CRUD + admin-only access |
| `SsoRestControllerTest` | ~5 | REST CRUD + auth |
| **Total** | ~47 | |

### Verification

1. `mvn test -Dtest="SsoIdentityProviderTest,SsoProviderServiceTest,SsoProvisioningServiceTest,SsoIdentityProviderAdapterTest,DatabaseRelyingPartyRegistrationRepositoryTest,SsoAuthenticationSuccessHandlerTest,SsoAdminControllerTest,SsoRestControllerTest"` — all ~47 tests pass
2. Start app locally, verify `/login` page shows no SSO buttons (no IdPs configured)
3. Navigate to `/admin/sso`, create a test IdP configuration
4. Verify `/login` page now shows SSO button
5. Verify SP metadata endpoint returns valid XML at `/admin/sso/{id}/metadata`
6. Full integration test requires a SAML IdP (e.g., Keycloak dev instance) — not in CI
