# StealADeal — Go-Live Plan

Step-by-step path to production for the Spring Boot backend (single
deployable: API + bundled SPA). Status as of this document: the
code-only go-live blockers are **closed in this change**; the
remaining items are infrastructure/credential prerequisites with
named owners.

Legend: ✅ done in code · ⛳ needs external setup (creds/infra) · ☐ task

---

## 0. Release gate (must all be true before prod traffic)

- ✅ Demo seed cannot run in prod (`app.seed.demo.enabled=false` in
  `application-prod.yml`; previously `@Profile("!test")` meant demo
  dealers + known-password accounts would have been written to the
  production DB).
- ✅ SPA deep links / refresh resolve (SpaForwardingController forwards
  non-API, non-asset GETs to the shell).
- ✅ Liveness/readiness probes exist (`/actuator/health`,
  `/actuator/health/readiness`, `/actuator/health/liveness`), public,
  details hidden.
- ✅ CORS is env-driven in prod (`CORS_ALLOWED_ORIGINS`), defaults to
  `https://stealadeal.io` instead of localhost.
- ✅ Security fail-closed + per-tenant scoping + method security
  (delivered earlier; regression-tested).
- ✅ Flyway migrations V1–V13 own the prod schema; Hibernate
  `ddl-auto=validate`.
- ⛳ Postgres reachable; `BOOTSTRAP_ADMIN_PASSWORD` set to a strong
  secret (no default — app fails fast without it).
- ⛳ TLS terminates in front of the app (LB/ingress); app listens HTTP.
- ☐ Smoke + load pass in staging (section 5).

---

## Phase 1 — Provision infrastructure  ⛳ (owner: infra/devops)

1. **Postgres** (managed: RDS/Cloud SQL/Neon). Create db `stealadeal`,
   a least-privilege app role. Capture `DATABASE_URL`,
   `DATABASE_USERNAME`, `DATABASE_PASSWORD`.
2. **Secrets store** (SSM/Secrets Manager/Doppler) for:
   `BOOTSTRAP_ADMIN_PASSWORD`, DB creds, `CORS_ALLOWED_ORIGINS`, and
   the integration keys from Phase 4.
3. **Runtime**: container platform (ECS/Fly/Render/K8s). The image is
   already built by CI (`docker-build` job) — wire a registry push +
   deploy. Health check path `/actuator/health/readiness`.
4. **TLS/!**: terminate HTTPS at the LB; forward to container `:8282`.
   Set `CORS_ALLOWED_ORIGINS` to the real web origin(s).
5. **DNS**: `api.stealadeal.io` (or serve SPA + API same origin — the
   bundled SPA makes single-origin trivial and removes CORS entirely).

## Phase 2 — Database migration  ⛳

1. Point a throwaway env at the prod Postgres with
   `SPRING_PROFILES_ACTIVE=prod`.
2. Boot once: Flyway applies V1–V13 (`baseline-on-migrate=true`).
   **This has never been run against real Postgres — it is the single
   highest execution risk.** Verify `flyway_schema_history` shows 13
   applied, and Hibernate `validate` passes (app stays up).
3. If validate fails, the mismatch is between a hand-written migration
   and an entity — fix forward with a new `V14__*.sql`, never edit an
   applied migration.
4. Take a baseline snapshot/backup; confirm restore works.

Runbook: [postgres-setup.md](postgres-setup.md).

## Phase 3 — First deploy (no public traffic)  ☐

1. Deploy with env from Phase 1. Confirm:
   - `GET /actuator/health` → `{"status":"UP"}`
   - `GET /actuator/health/readiness` → UP
   - `GET /api/vehicles` → `[]` (empty — demo seed is OFF in prod ✅)
   - `GET /` → SPA shell; deep link `GET /something` → SPA shell
2. Create the real first admin via `BOOTSTRAP_ADMIN_*` (auto on boot).
   Rotate/disable the default email.
3. Create the first real dealer through the API/portal; verify the
   onboarding tracker advances and the audit trail records it.

## Phase 4 — Swap stubs for real providers  ⛳ (per integration)

Every integration is an SPI with a working stub; going live for real
money/comms means registering the real adapter + keys. No refactor
required — each is a drop-in bean + `app.*.provider` switch.

| Capability | Switch | Needs |
|---|---|---|
| Billing (subscription, deposit PaymentIntent, tx-fee) | `app.billing.provider=stripe` | Stripe keys + webhook secret; implement `StripeBillingProvider` |
| E-sign (buyer agreement) | `app.esign.provider=docuseal` | **Verified end-to-end against a live DocuSeal** (free edition). Free DocuSeal can't API-ingest a per-deal PDF, so signing uses a fixed UI-built template + pushed field values (see docs/docuseal-esign.md). Remaining go-live step: build the real counsel-approved template in the DocuSeal UI with the documented field names, set `DOCUSEAL_TEMPLATE_ID` + `DOCUSEAL_BASE_URL`/`DOCUSEAL_API_TOKEN`/`DOCUSEAL_WEBHOOK_SECRET` via secrets manager, and register the webhook |
| Buyer agreement document | n/a (in-app) | **Implemented** — `BuyerAgreementPdfRenderer` (OpenPDF) renders the agreement PDF from live deal data; `POST /api/deals/{id}/documents/buyer-agreement/generate` stores it as the BUYER_AGREEMENT document, then the existing sign flow sends it to DocuSeal. Legal-text wording should be reviewed by counsel before launch |
| Document storage | `app.storage.documents.provider=s3` | S3 bucket + IAM; implement `S3DocumentStorage` |
| Notifications (email/SMS) | `app.notifications.provider=ses-twilio` | SES + Twilio creds; implement channels |
| VIN decode | `app.vin.provider=nhtsa` | None — free public API (rate-limit aware) |

Go-live can proceed with stubs **only** if launch scope excludes real
payments/contracts/comms (e.g., a controlled pilot). Otherwise Stripe
+ storage + e-sign + email are hard prerequisites for the "close a
real deal end-to-end" milestone.

## Phase 5 — Staging verification  ☐

1. Enable stress seed in **staging** and load-test the public catalog:
   ```
   --app.seed.stress.enabled=true --app.seed.stress.vehicle-count=2000
   hey -z 60s -c 100 'https://staging/api/vehicles'
   hey -z 60s -c 100 'https://staging/api/vehicles?make=Toyota&maxPrice=30000'
   ```
   Capture p95/p99, error rate, DB pool saturation. Tune
   `DATABASE_POOL_MAX`. Confirm the migration-added indexes are used
   (`EXPLAIN` the catalog query).
2. End-to-end golden path against staging with **real** providers:
   register → dealer approve → inventory → lead → deal → deposit
   (Stripe test) → docs upload (S3) → e-sign → handoff → COMPLETED →
   tx-fee settled. Verify audit trail + notification dispatch.
3. Confirm scheduled jobs run in prod profile: notification outbox,
   onboarding processor, inventory feed, stale reaper (intervals are
   pinned only in the test profile).
4. **Wipe stress data before prod** (`STRESS`-prefixed VINs +
   `STRESS-0001` dealer) — staging only.

## Phase 6 — Observability & ops  ⛳/☐

- ⛳ Ship logs to a sink; alert on `ERROR`.
- ⛳ Error tracking (Sentry/Rollbar).
- ☐ Dashboards: request latency, DB pool, JVM, scheduled-job
  success counts (the services already log structured outcomes;
  add metrics export — actuator is now on the classpath, so
  `prometheus`/`metrics` exposure is a one-line config follow-up).
- ☐ Alert on `/actuator/health` != UP and on readiness flaps.
- ☐ Backup/restore drill; documented RTO/RPO.

## Phase 7 — Cutover  ☐

1. Final migration dry-run on a prod-clone.
2. Deploy; health green; smoke (section 5.2) on prod with a real
   test dealer; then open DNS.
3. Watch error rate + latency for the first hour.

## Rollback

- App: redeploy previous image tag (stateless; safe).
- DB: migrations are additive (no destructive DDL in V1–V13). A bad
  forward migration is fixed by another forward migration; restore
  from the Phase 2 snapshot only as last resort.
- Provider regression: flip `app.*.provider` back to `stub` and
  redeploy — degrades gracefully without code change.

---

## Gaps closed in this change (code)

| Gap | Severity | Fix |
|---|---|---|
| Demo seed (incl. known-password accounts) ran under `prod` | **Critical** | `@ConditionalOnProperty(app.seed.demo.enabled)`; `false` in prod yml |
| SPA deep link / refresh → 404 | High | `SpaForwardingController` + SecurityConfig regex permit |
| No container/LB health probe | High | `spring-boot-starter-actuator`; public `/actuator/health[/readiness|/liveness]` |
| CORS hard-coded to localhost | Medium | `CORS_ALLOWED_ORIGINS` env, prod default non-localhost |
| F&I entities had no `@Table`; prod `validate` failed on Postgres (acronym class → `dealfandiproduct` vs migration `deal_f_and_i_product`) | **Critical** | Explicit `@Table` on `FAndIProduct`/`DealFAndIProduct`; verified by booting prod profile against Docker Postgres |
| Car photos were external URL strings only (no object storage; broken/expired images, hotlinking) | High | `VehicleImageStorageService` SPI + local adapter (S3 drop-in via `app.storage.vehicle-images.provider`); upload/serve/delete endpoints; hard 10-photo/listing cap enforced on upload **and** every bulk path (create/update/CSV/JSON feed/VIN). Stripe-style provider switch. Follow-up: background re-hosting of feed-supplied external URLs |

Tested: full suite green (deterministic since the per-context test-DB
isolation fix); added coverage for the SPA forward and the public
health endpoint.

## Known residual risks (explicit, not yet mitigated)

1. **Flyway V1–V13 verified on real Postgres (2026-05-16).** Booted
   the prod profile against Postgres 16 (Docker): Flyway "Successfully
   validated 13 migrations", schema up to date, Hibernate `validate`
   passes, app starts, `/actuator/health` UP, `/api/vehicles` empty
   (demo seed correctly OFF in prod), SPA deep link serves the shell.
   This run found and fixed a real blocker: `FAndIProduct` /
   `DealFAndIProduct` had no `@Table`, so Hibernate derived
   `fandiproduct`/`dealfandiproduct` while V9 created
   `f_and_i_product`/`deal_f_and_i_product` — invisible on H2
   (`ddl-auto=update`), fatal on prod `validate`. Pinned explicit
   `@Table` names. Re-run is clean. Remaining: run against the actual
   target managed Postgres (versions/extensions/roles may differ).
2. **All external integrations are stubs** — fine for a no-real-money
   pilot; blocking for full commerce. Phase 4.
3. **Opaque bearer tokens, no rate limiting** — acceptable for pilot
   scale; add a gateway/rate-limit before broad launch.
4. **No metrics/error-tracking wired** — actuator is present; exporters
   are a fast follow, not a code blocker.
