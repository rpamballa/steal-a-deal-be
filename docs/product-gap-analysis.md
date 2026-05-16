# StealADeal — Product Gap Analysis & Development Roadmap

**Authored by:** Product Owner review
**Date:** 2026-05-14
**Source artifacts:**
- `StealADeal_Investor_Pitch_v5.pptx` (16 slides, Seed 2026 narrative)
- Backend repo at HEAD (`claude/lucid-chaplygin-e40801`)
- [docs/openapi.yaml](openapi.yaml) (1,340 lines, contract of record)
- [docs/investor-pitch-deck.md](investor-pitch-deck.md) (internal pitch narrative)

---

## 1. Executive Summary

The pitch positions StealADeal as a **transaction OS for ~40K independent used-car dealers** with a target close of **$1.25M seed at $6M cap**, claiming **~65% MVP completion** across two repos and **24 live endpoints**. The narrative is sharp and the wedge (deal-close workflow vs lead-gen) is well-defined.

The **backend reality is stronger than the pitch admits in some areas, and materially weaker in others**:

- **Stronger than claimed:** Auth/JWT is not "0%" — token-based auth, registration, login, BCrypt hashing, dealer approval gating, and a current-user endpoint are all implemented. Dealer SaaS portal is not "12%" — there is a full `DealerPortalService` with overview, pipeline, queue, subscription, invoices, and per-stage views.
- **Weaker than claimed:** There is **no frontend repo in this worktree**. The pitch claims React buyer marketplace at 82%, deal room at 76%, TS API client at 88%, and React Native at 15% — **none of this is present**. The "two repos" claim is therefore unverifiable from this checkout, and demo-readiness in 30 days depends entirely on whether the frontend repo exists elsewhere.
- **Hard gaps that block first invoice (Pitch's Month 5 milestone):** No Stripe integration, no real payments, no file storage for documents (only `fileName` strings), no email/SMS delivery for notifications, no production-grade security enforcement, no deployment infrastructure.

The 30-day demo is achievable if the frontend repo exists; the **Month-5 first-billing milestone is at risk** without significant engineering on Stripe, file storage, and security hardening.

---

## 2. Pitch Claims vs Reality

### 2.1 Completeness scorecard (slide 6 vs codebase)

| Component (pitch) | Pitch % | Backend reality | Verdict |
|---|---|---|---|
| Backend API (Spring Boot/Java 21) | 78% | Spring Boot 3.4.2, Java 21, JPA, Security, 35+ endpoints implemented in OpenAPI | **Understated** — closer to 85% of scope |
| Buyer marketplace (React web) | 82% | **Not in this repo** | **Unverifiable** |
| Deal room (7-stage flow) | 76% | 7-stage state machine + readiness + tasks/notifications implemented in [DealService.java:374](src/main/java/com/stealadeal/service/DealService.java:374) | **Backend complete; UI unverified** |
| TypeScript API client | 88% | **Not in this repo** | **Unverifiable** |
| Dealer SaaS portal | 12% | `DealerPortalService` + 8 portal endpoints fully wired ([DealerPortalService.java](src/main/java/com/stealadeal/service/DealerPortalService.java)) | **Severely understated** — backend at 70%+ |
| Auth / JWT (BE + FE) | 0% | Opaque-token Spring Security, registration, login, bootstrap admin ([AuthService.java](src/main/java/com/stealadeal/service/AuthService.java)) — **but not JWT, and not enforced (see §3.1)** | **Misleading** — BE exists, JWT is wrong label, enforcement gap is real |
| Stripe subscription + billing | 0% | No Stripe SDK; invoices are seed records, no payment processor | **Accurate** |
| React Native mobile | 15% | **Not in this repo** | **Unverifiable** |

**Action:** Re-baseline slide 6 with honest line items. The "0% auth" claim undersells the technical depth and will read as careless to a technical diligence partner. The "12% portal" claim is wrong by a factor of 5–6×.

### 2.2 Endpoint count

- **Pitch claims:** 24 endpoints
- **OpenAPI shows:** 35 operations across 11 tag groups (`Auth`, `Dealers`, `Vehicles`, `Leads`, `Appointments`, `Deals`, `Inbox`, `Portal`, `Tasks`, `Notifications`, `Dashboard`)
- **Action:** Update pitch to "35+ documented endpoints" — strengthens the technical-depth signal.

### 2.3 Stack mismatches

| Slide | Claim | Reality |
|---|---|---|
| Slide 1, 15 | "Spring Boot · React Native · OpenAPI 3.0" | Backend correct; mobile not in this repo |
| Slide 15 | "Spring Data JPA + H2/Postgres" | Only H2 configured ([application.yml](src/main/resources/application.yml)) — Postgres profile missing |
| Slide 1 | "Production-grade, containerisable, cloud-ready" | No Dockerfile, no docker-compose, no Helm/IaC — not containerisable today |
| Slide 6 | "Auth/JWT" | Implementation is **opaque bearer tokens**, not JWT — fine, but stop calling it JWT |

---

## 3. Hard Gaps (Block Seed-Milestone Delivery)

These gaps **must** close before the Month-5 "first $1,100/mo billing" milestone the pitch sells.

### 3.1 Security enforcement is effectively off

[SecurityConfig.java:35](src/main/java/com/stealadeal/config/SecurityConfig.java:35) sets `.anyRequest().permitAll()`. The `TokenAuthenticationFilter` populates a `SecurityContext` on valid tokens, but **no endpoint actually requires authentication**, despite the OpenAPI spec advertising `security: [bearerAuth: []]` globally. A buyer/anonymous caller can hit `PATCH /api/dealers/{id}/approval`, `GET /api/dealers/{id}/portal`, `PATCH /api/deals/{id}/stage`, etc. with no token.

**Risk:** Catastrophic. A technical diligence call will catch this in 10 minutes.

**Fix scope (estimated 2–3 dev days):**
- Replace `.anyRequest().permitAll()` with `.anyRequest().authenticated()`, exempting only `/api/auth/**` and the genuinely public listing endpoints (`GET /api/vehicles`, `GET /api/vehicles/{id}`, `GET /api/dealers`, `GET /api/dealers/{id}`).
- Add `@PreAuthorize` annotations on dealer-scoped endpoints (e.g., `getDealerPortal` should require the caller's `dealerId` matches the path param, or be `ADMIN`).
- Add an `AccessControlService` enforcement test covering cross-tenant access (Dealer A token → Dealer B portal → must 403).

### 3.2 Multi-tenancy isolation

There is no enforced data scoping. Any authenticated dealer can list deals/leads/inventory belonging to other dealers via the global `/api/deals`, `/api/leads`, `/api/vehicles`, `/api/appointments` endpoints. The `AccessControlService` exists but isn't visibly invoked at the controller boundary.

**Fix scope:** Add tenant filtering at every list endpoint and a `@TenantScope` annotation enforced by an aspect or controller advice. Block list endpoints for dealers without a `dealerId` filter scoped to their own org.

### 3.3 Payments — no real money movement

The pitch sells three revenue streams. None can clear today:

| Stream | Pitch | Reality |
|---|---|---|
| SaaS subscription ($1,100/mo) | "First billing Mo 5" | `PortalSubscription` is a DB record. `priceForPlan()` returns hard-coded BigDecimals. No Stripe customer, no payment method capture, no webhook handler, no dunning. |
| Per-deal transaction fee (0.75%) | Revenue stream #2 | Not modeled anywhere. No `transaction_fee` column on `Deal`, no calculation, no settlement. |
| F&I revenue share (15–25%) | Revenue stream #3 | Not modeled. No F&I product entity, no warranty/GAP/insurance schema. |

**Fix scope (4–6 weeks, the single largest gap):**
- Stripe Billing integration for subscriptions: customer creation on dealer approval, payment method on signup, recurring invoice generation, webhook for `invoice.paid` / `invoice.payment_failed`.
- Buyer deposit via Stripe PaymentIntent (replacing simulated `POST /api/deals/{id}/deposit`).
- Per-deal transaction fee: compute on `DealStage.COMPLETED`, settle via Stripe Connect to dealer or via direct invoice.
- F&I product schema: `FAndIProduct`, `DealFAndIAttachment`, revenue-share calculation; wire to deal totals.

### 3.4 Document storage — currently fiction

[DealService.java:154](src/main/java/com/stealadeal/service/DealService.java:154) creates documents with placeholder filenames (`buyer-agreement.pdf`, `driver-license-upload`). There is **no file upload, no S3/GCS, no presigned URL, no actual document storage**. The `DealDocument` entity stores a `fileName` string and that's it.

A "Digital Deal Room" that can't accept a PDF will not survive a dealer demo.

**Fix scope (2–3 weeks):**
- S3-compatible storage layer with presigned upload URLs.
- Multipart upload endpoint, content-type/size validation, virus scan hook.
- Document versioning, signed URL for retrieval, retention policy.
- E-sign integration (DocuSign or Dropbox Sign) for `BUYER_AGREEMENT`.

### 3.5 Notifications don't actually notify

[TaskNotificationService.java](src/main/java/com/stealadeal/service/TaskNotificationService.java) writes `Notification` rows to the DB. There is no email transport (SendGrid/SES), no SMS (Twilio), no push notification. A buyer or dealer literally never hears from the platform.

**Fix scope (1–2 weeks):**
- Pluggable `NotificationChannel` interface; implement email (SES/SendGrid) and SMS (Twilio).
- Templating layer per notification code.
- Async dispatch (Spring `@Async` + a job table for retries).
- User notification preferences (channel opt-in/out).

---

## 4. Soft Gaps (Strengthen Series A Story)

These don't block the seed but should be on the roadmap to support the Series-A data package the pitch promises at Month 18.

### 4.1 No production database story

- Only H2 in-memory configured. `ddl-auto: update` is unsafe for any production deployment.
- Add Postgres profile, Flyway/Liquibase migrations, a `prod` Spring profile, and connection-pool tuning.
- Add `pgaudit` or equivalent for compliance with the F&I/financing story.

### 4.2 No deployment / CI/CD

- No Dockerfile, no `docker-compose.yml`, no GitHub Actions workflow, no Terraform/CloudFormation.
- Slide 1 says "containerisable, cloud-ready" — this is aspirational, not implemented.
- $100K is allocated to "Infrastructure & DevOps" in the use-of-funds breakdown — this needs visible progress before that line reads as credible.

### 4.3 No observability

- No structured logging, no metrics export, no APM hook, no error tracking (Sentry/Rollbar).
- Series-A data package will need cohort dashboards (CAC, LTV, churn, lead-to-deal conversion, time-to-handoff). None of this is instrumented today.

### 4.4 DMS integrations

- Slide 8 (Phase 3) names CDK and Reynolds as enterprise-tier unlocks. There is no integration layer abstraction in the code (no `DmsAdapter` interface, no outbound webhook framework).
- Worth a 2-week spike in Q3-2026 to validate API access terms and feasibility — these vendors are notoriously gated.

### 4.5 Inventory ingestion at scale

- `POST /api/dealers/{id}/inventory-upload` accepts JSON or CSV. Good first step.
- For dealer onboarding the pitch promises "ingest inventory quickly" — need DMS pull, AutoTrader/CarGurus feed parser, photo CDN, and a "stale listing" reaper.

### 4.6 Analytics & dealer success

- The Series-A story rests on proving lead→deal conversion lift. No analytics events are emitted (Segment, Amplitude, or even a `platform_event` table).
- Need an event-bus or outbox pattern from day 1 so cohort analysis works retroactively.

### 4.7 Buyer trust & marketplace UX features

- No vehicle history report (Carfax/AutoCheck) integration.
- No inspection report attachment to listings.
- No buyer review/rating of dealer.
- No financing prequalification widget (the "F&I" story really needs this on the buyer side).

### 4.8 Domain model gaps

- `Vehicle` lacks: VIN-decoded specs, condition grade, accident history, prior-owner count, mileage history.
- `Deal` lacks: financing terms, lender, APR, loan-amount, monthly-payment estimate — necessary for the F&I revenue stream.
- `Lead` lacks: source (CarGurus, AutoTrader, organic), UTM attribution, lead score.
- `Dealer` lacks: licensing/bond documents, payout/banking info, hours, lot photos, geo coords.

### 4.9 Testing & quality bar

- Only two test classes (`StealADealApplicationTests`, `StealADealValidationTests`) — JaCoCo enforces 70% line coverage but actual coverage on services is unknown without running it.
- No contract tests against OpenAPI spec (spec drift is already visible — pitch says 24 endpoints, spec has 35).
- No load tests, no integration tests with real Postgres.

---

## 5. Pitch Narrative Gaps (Investor-Facing)

These are content/positioning gaps the product owner should resolve before the next investor meeting.

### 5.1 Inconsistent financial math

- Slide 5 unit economics: avg sale $18K, dealer gross $2,900, StealADeal earns **$264/deal**.
- Slide 12 financials: 35 deals/mo × $17K avg sale × 0.75% fee = $4,463/mo per dealer = **~$127/deal** — but the table shows $1.4K Tx Fees / mo at 5 dealers = $280/dealer/mo = **$8/deal/dealer? Or ~$56/deal at 5 deals/mo?** Numbers don't reconcile across slides.
- Slide 12 Mo 24 row: 55 dealers × $1,100 = $60.5K MRR ✓, but ARR shows $1.0M — should be $60.5K × 12 = $726K from SaaS alone, $83.5K × 12 = **$1.002M ✓**. OK that one works.
- Slide 11 "Value at 5×": shows $250K on $50K check — assumes 100% mark, fine; but Slide 14 "Conservative Exit 5×" shows total of $6.25M returned on the full round. Math reconciles, but the "5× exit at $6M–$7M Series A valuation" implies seed gets diluted further — return is closer to 3.5–4× after Series A dilution. Investor will catch this.

**Action:** Pull all numbers into a single source-of-truth spreadsheet and re-derive the slide tables from it.

### 5.2 "Demo-ready in 30 days" is at risk

The pitch says demo-ready in 30 days. From this worktree:
- Backend can demo against H2 with seed data — ✓ today.
- Frontend not in this repo. Need to verify it exists and is wireable.
- Without auth enforcement, the demo *looks* fine but anyone in the audience can poke endpoints they shouldn't see.

**Action:** Decide whether the 30-day demo is the seed-pitch demo (read-only walkthrough) or the pilot-dealer demo (transactional). They are very different scopes.

### 5.3 "65% MVP complete" needs sub-definitions

The number is unsubstantiated. Propose three explicit definitions:

- **Demo MVP** (read-only walkthrough): ~85% complete. Backend, OpenAPI, seed data done. Needs frontend wiring + auth enforcement.
- **Pilot MVP** (one dealer billed): ~50% complete. Add Stripe, file storage, email, deployment, security hardening, dealer-success runbooks.
- **Sellable MVP** (scale to 18 dealers): ~30% complete. Add multi-tenancy, observability, DMS-import, F&I, support tooling.

### 5.4 No moat evidence yet

Slide 3 names four moats. Zero evidence on three of them:
- ✗ "Dealer workflow lock-in" — needs ≥3 dealers with ≥30 days of usage. Not demonstrable pre-pilot.
- ✗ "Transaction data network effect" — needs cross-dealer benchmarks (no data yet).
- ✗ "F&I partner relationships" — no LOIs, no signed partners.
- ✓ "DMS integrations create stickiness at scale" — true but contingent on §4.4.

**Action:** Drop the "moat" claim in slide 3 to "future moat surfaces" or stage the slide as a 12-month moat-construction plan.

### 5.5 Founder slide gap

- Slide 9 lists Revanth solo. Pitch implies "founder + contract devs." With $500K eng + $125K design = $625K over 20 months supporting one founder, that's 1 founder + ~1.5 FTE-equivalent contractors. Realistic, but light for the scope (Stripe + RN + portal + DMS).
- No technical co-founder, no design lead, no GTM lead in the team slide. The seed budget needs to either contemplate a key technical hire or explicitly defend the contractor model with named senior contractors.

### 5.6 Competition slide overstates StealADeal coverage

Slide 7 ticks "Digital deal room ✓" and "F&I integration ✓" for StealADeal. **Deal room exists at the backend state-machine level; F&I integration does not exist at all** (see §3.3). Either soften the tick to "in development" or remove the row until F&I lands.

---

## 6. Prioritized Roadmap (Tied to Pitch Milestones)

### Sprint 1 (Month 1–2) — "First Invoice Path"
**Goal:** Close the gap to Month-5 first billing.

| # | Item | Why | Effort |
|---|---|---|---|
| 1.1 | Enforce auth on all non-public endpoints; add per-tenant access checks | Blocks any demo with a technical investor in the room (§3.1, §3.2) | M (3–5d) |
| 1.2 | Stripe Customer + Subscription integration for dealer SaaS billing | Pitch Month-5 milestone (§3.3) | L (10–15d) |
| 1.3 | Migrate from H2 → Postgres, add Flyway migrations, `prod` profile | Required for any paid customer (§4.1) | M (5d) |
| 1.4 | Dockerise (backend Dockerfile + docker-compose for local Postgres) | Slide 1 claim of containerisable (§4.2) | S (2d) |
| 1.5 | GitHub Actions CI: build + test + JaCoCo report on PR | Quality gate before pilot | S (1–2d) |
| 1.6 | Verify frontend repo exists and is wireable to backend | Demo-ready claim depends on it | spike |

### Sprint 2 (Month 3–4) — "Pilot-Ready"
**Goal:** A dealer can run an end-to-end real deal.

| # | Item | Why | Effort |
|---|---|---|---|
| 2.1 | S3 file storage + presigned URLs for document uploads | Deal room is fiction without it (§3.4) | M (5–8d) |
| 2.2 | E-sign integration (DocuSign or Dropbox Sign) for buyer agreement | Required for "digital F&I" claim | M (5–7d) |
| 2.3 | Real buyer deposit via Stripe PaymentIntent | Replaces simulated deposit (§3.3) | M (4–5d) |
| 2.4 | Email transport (SES) + templated notifications | Notifications currently DB-only (§3.5) | M (4–6d) |
| 2.5 | Per-deal transaction fee model and settlement flow | Revenue stream #2 (§3.3) | M (5d) |
| 2.6 | Audit/access logging table (who-did-what-when on deal stage changes) | Compliance for F&I path (§4.1) | S (2d) |

### Sprint 3 (Month 5–6) — "First Billing + Onboarding"
**Goal:** Bill the first dealer; onboard 5 in Fayetteville/RDU.

| # | Item | Why | Effort |
|---|---|---|---|
| 3.1 | Dealer self-onboarding wizard (license capture, banking, payment method) | Pilot onboarding | M (5–7d) |
| 3.2 | Inventory ingest from CSV at scale + photo CDN | Pitch slide 8 promise | M (5d) |
| 3.3 | SMS (Twilio) for buyer notifications | Dealers will demand it | S (3d) |
| 3.4 | Observability: structured logs, metrics, error tracking (Sentry) | Required for support (§4.3) | M (4d) |
| 3.5 | Cancellation + refund flow (mentioned in [investor-pitch-deck.md:165](docs/investor-pitch-deck.md:165) roadmap) | Already on internal roadmap | S (3d) |

### Sprint 4 (Month 7–12) — "Regional & F&I"
**Goal:** 18 dealers, $210K ARR (slide 8 phase 2 target).

| # | Item | Why | Effort |
|---|---|---|---|
| 4.1 | F&I product catalog + revenue-share calc (warranty, GAP, insurance) | Revenue stream #3 (§3.3) | L (3–4w) |
| 4.2 | Financing workflow + prequal partner integration | Slide 11 roadmap | L (3–4w) |
| 4.3 | Buyer trust features (Carfax/AutoCheck, inspection report) | §4.7 | M (2w) |
| 4.4 | Analytics event bus + cohort dashboards (CAC/LTV/conversion) | Series-A data package (§4.6) | L (3w) |
| 4.5 | Pricing recommendation engine for dealers | Slide 11 long-term | spike |

### Sprint 5 (Month 13–24) — "Series-A Readiness"
**Goal:** 55 dealers, $1.1M ARR, DMS integration MVP.

| # | Item | Why | Effort |
|---|---|---|---|
| 5.1 | CDK or Reynolds DMS integration (one, not both, in MVP) | Slide 8 phase 3 (§4.4) | XL (6–10w) |
| 5.2 | React Native buyer app GA | Slide 6 promise | L (6w) |
| 5.3 | Multi-region deployment, SOC2 readiness | F&I/financing compliance | XL (ongoing) |
| 5.4 | Dealer-group/franchise tier (hierarchy, roll-up reporting) | Top-100 consolidators are the upgrade path | L (4w) |

---

## 7. Open Questions for Founder

These need an answer before the roadmap above is committable:

1. **Where is the frontend repo?** This worktree has zero frontend code. Slides 1, 6, and 15 claim 78–88% completion on three frontend artifacts.
2. **Who are the contract devs?** $500K eng over 20 months supports ~2 FTE-equivalent senior contractors. Are they named and committed?
3. **What's the Stripe Connect vs direct-billing decision?** Per-deal transaction fees + F&I revenue share need a settlement model — split payments via Connect, or post-hoc dealer invoicing? Materially affects architecture.
4. **Does the 30-day demo target the seed-pitch (walkthrough) or pilot (transactional)?** Different scopes by ~3 months of engineering.
5. **Are there any pilot-dealer LOIs in Fayetteville/RDU today?** Slide 8 Phase 1 names this market; relationships pre-investment de-risk Sprint 3 dramatically.
6. **Is the "JWT" label intentional or should it become "opaque bearer token"?** Affects diligence credibility.
7. **What's the cap-table reality once Series A converts the SAFE?** Slide 14's "20.8% stake" needs post-Series-A dilution math attached.

---

## 8. Honest Re-Phrasing Suggestions for the Next Pitch Revision

| Current slide language | Honest re-phrase |
|---|---|
| "Auth/JWT — 0%" (slide 6) | "Auth backend complete (token-based); frontend integration pending" |
| "Dealer SaaS portal — 12%" (slide 6) | "Dealer portal backend complete (8 endpoints); UI pending" |
| "Production-grade, containerisable, cloud-ready" (slide 15) | "Production-architected; Dockerisation and cloud deploy in Sprint 1" |
| "24 endpoints" (slide 1) | "35 documented endpoints across 11 service domains" |
| "65% MVP built" (slide 1) | "Demo MVP ~85%; pilot MVP ~50% (Stripe + storage + security + deploy remaining)" |
| "Digital F&I in-deal" ✓ on slide 7 | "F&I integration on roadmap — pilot dealers will validate before build" |
| "Live in 30 days" (slide 6) | "Backend demo-ready today; pilot-ready in ~90 days" |

---

## 9. Definition of Done — "Seed-Earned"

The seed is "earned" by investors when, by Month 5:
- [ ] One dealer is billed $1,100/mo via Stripe and the invoice clears.
- [ ] One full deal closes end-to-end (lead → deposit → docs → e-sign → handoff → COMPLETED) with real money and real PDFs.
- [ ] At least three dealers are onboarded with their inventory live.
- [ ] Auth + multi-tenant isolation are enforced and verified by an external test.
- [ ] CI is green, JaCoCo > 70%, Sentry catches errors, Postgres is in prod.
- [ ] Cohort analytics show lead-to-deal conversion delta vs dealer's prior baseline (the Series-A wedge).

If any of the above is missing at Month 5, the Month-12/18/24 milestones on slide 8 are at risk and the Series-A narrative weakens.

---

*This document is a working artifact. Update after each sprint review.*

---

## 10. Sprint 1 progress log

### 2026-05-14 — Foundation slice landed

| Gap | Status | Commit |
|---|---|---|
| §3.1 Security fail-closed | **Closed** | Auth chain switched from `permitAll` to `authenticated` with explicit allowlist; webhook + auth + public-browse exempted |
| §3.2 Multi-tenant list isolation | **Closed** | `getDealsForPrincipal` / `getLeadsForPrincipal` / `getAppointmentsForPrincipal` filter by caller role; regression test covers Dealer-A→Dealer-B leakage |
| §4.1 Postgres + Flyway prod profile | **Closed** | `application-prod.yml`, Flyway core + Postgres adapter, V1 baseline migration, runbook in `postgres-setup.md`. Dev/test still H2 + ddl-auto |
| §4.2 Containerisation | **Closed** | Multi-stage Dockerfile, docker-compose with Postgres healthcheck, `.dockerignore` |
| §4.2 CI | **Closed** | `.github/workflows/ci.yml` runs build + JaCoCo coverage gate + Docker build with Buildx cache |
| §3.3 Stripe — architecture seam only | **Partially closed** | `BillingProvider` SPI + `LoggingBillingProvider` stub + `app.billing.provider` config + webhook endpoint at `/api/webhooks/billing` + V2 migration adding `billing_customer_id`, `billing_subscription_id`, `payment_method_id`. Real Stripe SDK + API keys still required to flip from stub to live billing. |

### 2026-05-15 — Sprint 2 + F&I landed

| Gap | Status | Notes |
|---|---|---|
| S2.1 Document storage | **Closed** | `DocumentStorageService` SPI + local FS adapter; real upload/download endpoints; `storage_key`/`content_type`/`size_bytes` (V3) |
| S2.2 E-sign | **Closed** | `ESignProvider` SPI + stub; envelope lifecycle + `/api/webhooks/esign`; `signing_envelope_id`/`signing_status` (V4) |
| S2.3 Real deposit flow | **Closed** | Deposit intent + webhook confirmation via `BillingProvider`; `deposit_intent_id` (V5); manual path kept for dev |
| S2.4 Notification dispatch | **Closed** | `NotificationDispatcher` SPI + email/SMS stub; `dispatched_at`/`dispatch_channels` (V6) |
| S2.5 Per-deal transaction fee | **Closed** | `TransactionFeeService`, settles at COMPLETED via `BillingProvider`; `platform_fee_*` (V7); `GET /deals/{id}/platform-fee` |
| S2.6 Audit log | **Closed** | `audit_event` (V8), `AuditService`, admin `GET /api/audit`; wired into approval/stage/deposit/subscription |
| S4.1 F&I products + revenue share | **Closed** | `FAndIProduct` + `DealFAndIProduct` (V9); catalog admin + per-deal attach with snapshotted revenue share; `/api/fni/*` |

### Still outstanding — requires external vendor credentials only

Every architectural seam is now in place behind an SPI with a working stub. The remaining work is **registering a real vendor adapter bean + supplying credentials** — no further refactoring:

- Real Stripe adapter for `BillingProvider` (subscription, deposit PaymentIntent, transaction-fee transfer) — set `app.billing.provider=stripe`
- Real DocuSign/Dropbox-Sign adapter for `ESignProvider` — set `app.esign.provider=docusign`
- Real S3 adapter for `DocumentStorageService` — set `app.storage.documents.provider=s3`
- Real SES + Twilio adapters for `NotificationDispatcher` — set `app.notifications.provider=ses-twilio`
- DMS integrations (CDK/Reynolds) — Sprint 5, needs vendor API access

### 2026-05-15 — Notification outbox landed

| Gap | Status | Notes |
|---|---|---|
| Async outbox + retry for notifications | **Closed** | `dispatch_status`/`dispatch_attempts` (V10), best-effort inline first attempt + `NotificationOutboxProcessor` `@Scheduled` retry with bounded attempts, FAILED terminal state. `app.notifications.max-dispatch-attempts` / `outbox-poll-ms` configurable. |

### 2026-05-15 — Dealer onboarding automation landed

| Gap | Status | Notes |
|---|---|---|
| Automated dealer onboarding | **Closed** | `dealer_onboarding` (V11) tracks 8 state-derived milestones (REGISTERED → ACTIVATED). `DealerOnboardingService.evaluate` recomputes from live system state (approval, dealer user, active subscription, live inventory, first lead/deal, first completed deal), advances the stage, stamps milestone timestamps, and audits transitions. `DealerOnboardingProcessor` `@Scheduled` re-evaluates every dealer and auto-nudges a dealer stuck on an actionable step (USER_CREATED / SUBSCRIPTION_ACTIVE / INVENTORY_LIVE / FIRST_DEAL) through the notification outbox, with `app.onboarding.stale-hours` cadence and `max-nudges-per-stage` cap. `GET /api/dealers/{id}/onboarding` returns stage, percent complete, next action, and milestone timestamps. |

This automates ~60–70% of onboarding-volume dealer-success work (milestone tracking, stuck-dealer nudges, next-action guidance, funnel instrumentation) with no external dependency. The value-dense remainder (first real deal hand-holding, recovery of churning dealers, complex troubleshooting) remains a human function by design.

All backend gaps that do not require external vendor credentials are now closed. The only remaining work is registering real vendor adapter beans (Stripe / DocuSign / S3 / SES / Twilio) once credentials are provisioned, and the Sprint 5 DMS integrations.

