# Spec Gap Register — StealADeal_Dev_Instruction_Prompt v1.0

Every requirement in the instruction doc vs. the actual **backend** repo
(`steal-a-deal-be`), with disposition. Frontend/mobile live in a
separate repo (`StealADeal_fe`) and are out of scope here.

Disposition key: **FIX** (safe, additive, in-repo — doing now) ·
**DONE** (already satisfied/exceeded) · **DEFER-CONTRACT** (would break
the frontend api.ts / OpenAPI contract — §6 forbids; needs both repos +
Founder) · **OUT** (different repo / external creds / Founder /
infra) · **PARTIAL** (substantially done, residual noted).

## Sprint 1 (doc priority order)

| # | Requirement | Current state | Disposition |
|---|---|---|---|
| 1 | Auth = **JWT** access(15m)+refresh(30d), `/api/auth/register|login|refresh|me`, roles in claims, `JWT_SECRET` env | ~~Opaque DB token, no refresh~~ | **DONE** — HS256 JWT (`JwtService`), rotating `RefreshToken` + `POST /api/auth/refresh`, `JWT_SECRET` env, claims carry role/uid/dealerId; existing HTTP response shape preserved (+`refreshToken`), all tests green |
| 2 | Rate limit `/api/auth/*` 5 req/min/IP (§10 MANDATORY) | ~~None~~ | **DONE** — `AuthRateLimitFilter` per-IP fixed window, configurable (`app.auth.rate-limit-per-minute`, default 5; test profile lifted) |
| 3 | Standardised error `{error,message,timestamp}` (§9.1) | ~~`{error,message}`~~ | **DONE** — ISO-8601 `timestamp` added to `ApiExceptionHandler` + Security 401/403 writers + OpenAPI `ErrorResponse` (values unchanged → no assertion breakage) |
| 4 | `POST /api/admin/dealers/{id}/approve` + `/reject` | ~~Only PATCH approval~~ | **DONE** — `AdminController` with audit + dealer notification; legacy PATCH retained; OpenAPI updated |
| 5 | Dealer SaaS portal, 5-step onboarding, admin approval gate | Dealer portal + automated onboarding tracker + approval gate built | **DONE** (meets/exceeds) |
| 6 | PostgreSQL + Flyway (dev+prod) | Flyway V1–V13, prod profile, verified vs live PG | **DONE** |
| 7 | Docker, CI/CD GitHub Actions | Dockerfile + compose + Actions (build/test/coverage/docker) | **DONE** |
| 8 | SeedDataConfig disabled in prod | `app.seed.demo.enabled=false` in prod profile | **DONE** |

## Sprint 2

| Requirement | Current | Disposition |
|---|---|---|
| Stripe subscription + deposit PaymentIntent + 0.75% tx fee + webhooks | `StripeBillingProvider` implements all of this behind the SPI; webhook signature verify | **DONE** (code) — live keys are Founder/secrets (**OUT**) |
| Grace period: 7d → block new deals; >30d → lose listing visibility | Not enforced | **DEFER-CONTRACT/PARTIAL** — needs subscription-status gating across deal/listing flows; coupled to billing ops; flagged for a dedicated change |
| F&I product cards + signed election before DOCUMENTS | `FAndIProduct`/`DealFAndIProduct` + attach/revenue-share exist; stage-ordering rule not enforced | **PARTIAL** — model done; rule enforcement tied to divergent DealStage (see below) |
| Buyer deposit refund (admin-triggered) | Deposit flow exists; admin refund path not built | **PARTIAL** |

## Bridge / cross-cutting

| Requirement | Disposition |
|---|---|
| DocuSign/HelloSign e-sign | **DONE differently** — self-hosted DocuSeal adapter, verified live (Founder picked DocuSeal over paid DocuSign) |
| Admin portal (approval queue, health, revenue, disputes) | **PARTIAL/OUT** — backend data exists (audit, dashboard, dealer approval); the *portal UI* is frontend repo |
| React Native app, buyer/dealer/admin UX (§5) | **OUT** — `StealADeal_fe` repo |
| Regulatory disclosure banner, design system, AuthContext, Vitest/Playwright, no-`any`, etc. (§5, §9.2, §9.3, §11 FE) | **OUT** — frontend repo |
| Secrets Manager, SSL, domain, CloudWatch monitoring, OWASP ZAP, Founder sign-off | **OUT** — infra / external / Founder |
| Testcontainers integration tests, 80% service-layer coverage | **PARTIAL** — MockMvc integration tests + 70% JaCoCo gate exist; raising to 80% + Testcontainers is a separate testing initiative |

## Deliberately NOT changed (destructive / contract-breaking — §6 CRITICAL)

The doc's §7 lists enums as the *existing* model, but the real codebase
diverged (and was built/tested/merged against the current values, with
the frontend `api.ts` bound to them). Renaming them here would break
the separate frontend contract, which §6 explicitly forbids without
updating both repos, and would destroy merged, passing functionality.
These are **reported, not silently changed or skipped**:

- `DealStage` doc=`INQUIRY,OFFER,DEPOSIT,FINANCING,DOCUMENTS,F_AND_I,DELIVERY`
  vs code=`INITIATED,OFFER_SENT,BUYER_CONFIRMED,DEPOSIT_PAID,DOCUMENTS_PENDING,READY_FOR_HANDOFF,COMPLETED,CANCELED`
- `DocumentType` doc=`BUYERS_AGREEMENT,ODOMETER_DISCLOSURE,WARRANTY,GAP,TITLE`
  vs code=`BUYER_AGREEMENT,DRIVER_LICENSE,INSURANCE_PROOF,TITLE_DISCLOSURE`
- `AppointmentType` doc=`TEST_DRIVE,DELIVERY,INSPECTION` vs code=`TEST_DRIVE,HOME_DELIVERY`
- `User` (doc) vs `UserAccount` (code) — functionally equivalent; rename is destructive, no benefit
- Stage-gated business rules (§8.1) depend on the doc's DealStage names

**Recommendation:** reconciling §7 enums is a coordinated cross-repo +
Founder decision (rename in both backend & `api.ts` + migration, or
amend the spec to match the shipped contract). It must not be done
unilaterally from the backend.
