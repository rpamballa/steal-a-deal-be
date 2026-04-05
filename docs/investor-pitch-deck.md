# StealADeal Investor Pitch Deck

## 1. Title
**StealADeal**

The transaction-first marketplace for buying used cars online with confidence.

Stage: Product in development  
Market: US used vehicle retail  
Model: Marketplace + dealer workflow software

---

## 2. Problem
Buying a used car online is still fragmented and stressful.

- Discovery happens in one place
- Dealer communication happens somewhere else
- Pricing changes late in the process
- Documents and deposits are handled manually
- Buyers still do not know what is blocking completion

For dealers, the process is equally broken.

- Low-intent leads waste sales capacity
- Deal progression is tracked across calls, texts, and spreadsheets
- Handoff readiness is unclear
- Online transactions break down before delivery

---

## 3. Solution
StealADeal brings the entire used-car purchase journey into one transaction flow.

- Browse live inventory with rich listing media
- Move from lead to deal inside a single workflow
- Capture deposits and reserve vehicles
- Track required documents and approval states
- Schedule fulfillment directly on the deal
- Show explicit blockers to completion for buyers and dealers

Core thesis: the winner in online used-car retail is not just a better listing site. It is the platform that actually closes the transaction.

---

## 4. Product
StealADeal combines consumer buying UX with dealer execution tooling.

Buyer-side:
- Search inventory
- Review pricing and images
- Submit lead or start a deal
- Upload documents
- Track readiness and fulfillment

Dealer-side:
- Manage inventory
- Track deal stage progression
- Review documents
- Schedule pickup or delivery
- See activity timeline and stalled deals

Admin-side:
- Manage dealers and approvals
- Moderate listings
- Monitor transaction funnel health

---

## 5. Why Now
Three shifts make this market ready.

- Consumers are increasingly comfortable starting large purchases online
- Dealers need better conversion on increasingly expensive digital leads
- AI and workflow software now make fragmented dealership operations simpler to standardize

The used car market is massive, but the online transaction layer remains underbuilt.

---

## 6. Market Opportunity
We are targeting the software and transaction layer of used car commerce.

- US used vehicle market: very large, durable, and recurring
- Initial wedge: independent and regional dealers who need online conversion, not just traffic
- Expansion path: deposits, financing workflow, protection products, logistics, and dealer software subscriptions

StealADeal can monetize both software usage and transaction success.

---

## 7. Business Model
Hybrid revenue model.

- Dealer subscription for workflow and inventory tools
- Per-deal transaction fee on completed sales
- Premium add-ons over time:
  - financing workflow
  - trade-in workflow
  - featured inventory placement
  - delivery coordination

This creates recurring SaaS revenue plus scalable marketplace upside.

---

## 8. Traction So Far
Current status is pre-launch product execution with the transaction core already taking shape.

Built backend capabilities:
- dealer onboarding and approval
- vehicle inventory APIs
- lead capture
- appointment scheduling
- internal deal workflow
- pricing breakdown
- simulated deposit flow
- document tracking
- fulfillment scheduling
- activity timeline
- readiness/blocker computation

What this means:
- the product is already being built around conversion-to-close, not just browsing
- the backend architecture reflects a real operational transaction funnel

---

## 9. Go-To-Market
Start with independent and regional used car dealers who already spend on listings and lead generation.

Phase 1:
- onboard a small set of dealer partners
- ingest inventory quickly
- prove higher lead-to-deal conversion and faster handoff readiness

Phase 2:
- use dealer case studies to expand by metro and dealer group
- add buyer trust features and financing workflow

Primary value proposition to dealers:
- fewer dropped deals
- clearer workflow
- better sales team efficiency

---

## 10. Competitive Advantage
StealADeal is designed around transaction completion, not just listing volume.

- Deal-native workflow instead of lead-only CRM
- Readiness model that explains blockers explicitly
- Fulfillment scheduling directly attached to the deal
- Unified buyer, dealer, and admin operational flow

Over time, the moat comes from:
- workflow data
- conversion insights
- dealer operational embedding

---

## 11. Roadmap
Near term:
- buyer auth and dealer auth
- richer dashboard views
- cancellation and refund simulation
- dealer queue views for stalled and ready deals

Next:
- financing workflow
- e-sign integrations
- payment integrations
- audit and compliance tooling
- dealer analytics and pricing recommendations

Long term:
- full transaction orchestration platform for used vehicle commerce

---

## 12. Financial Story
The business compounds through both SaaS and transaction revenue.

- low initial customer count needed to validate dealer pain
- recurring software revenue improves retention
- transaction revenue scales with dealer inventory throughput
- expansion revenue comes from add-on workflows

Key early metrics to track:
- inventory-to-lead conversion
- lead-to-deal conversion
- deal-to-completion conversion
- time to handoff readiness
- revenue per dealer

---

## 13. Funding Ask
We are raising to accelerate product completion, pilot execution, and dealer onboarding.

Use of funds:
- product and engineering
- dealer onboarding and integrations
- design and frontend completion
- pilot operations and analytics

Milestones this round will fund:
- complete MVP transaction flow
- launch pilot dealers
- validate conversion improvements
- establish repeatable dealer acquisition motion

---

## 14. Vision
StealADeal becomes the operating system for online used car transactions.

Not just where buyers discover inventory.
Where dealers actually close deals.

---

## Appendix: Current Product Snapshot
Current backend supports:

- `GET/POST /api/dealers`
- `GET/PUT /api/dealers/{dealerId}`
- `PATCH /api/dealers/{dealerId}/approval`
- `GET/POST /api/vehicles`
- `GET/PUT /api/vehicles/{vehicleId}`
- `POST /api/vehicles/{vehicleId}/leads`
- `GET /api/leads`
- `PATCH /api/leads/{leadId}/status`
- `POST /api/vehicles/{vehicleId}/appointments`
- `GET /api/appointments`
- `PATCH /api/appointments/{appointmentId}/status`
- `GET/POST /api/deals`
- `GET /api/deals/{dealId}`
- `PATCH /api/deals/{dealId}/stage`
- `POST /api/deals/{dealId}/deposit`
- `GET/POST /api/deals/{dealId}/documents`
- `PATCH /api/deals/{dealId}/documents/{documentId}/status`
- `PATCH /api/deals/{dealId}/fulfillment`
- `GET /api/deals/{dealId}/activity`
- `GET /api/deals/{dealId}/readiness`
