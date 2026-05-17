# DocuSeal e-sign (free / self-hosted)

The open-source DocuSeal edition has **no API to upload a per-deal
PDF** (`/api/templates/pdf|docx|html` are Pro-only, verified against a
live instance). So buyer-agreement signing uses a **fixed template
built once in the DocuSeal UI**; the backend creates a submission
against that template id and pushes the deal's values into named
fields. DocuSeal renders/holds the document and emails the signer.

Verified end-to-end against a live DocuSeal: create submission with
prefilled values → completion webhook → document APPROVED/SIGNED.

## One-time: build the template in the DocuSeal UI

1. Sign in to your DocuSeal instance → **Templates → New**.
2. Upload your buyer-agreement PDF/DOCX (your real contract /
   state-required disclosures — counsel-reviewed).
3. Add **one submitter** (role e.g. "Buyer").
4. Add fields and name them **exactly** as below (text fields,
   prefilled by the backend, mark read-only), plus a **Signature**
   field for the submitter:

   | Template field name | Filled from the deal |
   |---|---|
   | `buyer_name` | buyer name |
   | `buyer_email` | buyer email |
   | `buyer_phone` | buyer phone |
   | `buyer_address` | full buyer address |
   | `dealer` | selling dealer name |
   | `vehicle` | year make model trim |
   | `vin` | vehicle VIN |
   | `price_total` | deal total (e.g. `$31,107.59`) |
   | `Signature` | signature field (signer signs) |

   Field names must match the keys in
   `DealService.buyerAgreementFieldValues` — that map is the contract.
5. Note the template **id** (numeric, in the template URL / API).

## Configure (secrets manager → env, never in source)

| Env | Value |
|---|---|
| `ESIGN_PROVIDER` | `docuseal` |
| `DOCUSEAL_BASE_URL` | e.g. `https://docuseal.internal` (no trailing slash) |
| `DOCUSEAL_API_TOKEN` | from DocuSeal → Settings → API |
| `DOCUSEAL_WEBHOOK_SECRET` | optional; if set, DocuSeal must send it as the `X-Signature` header |
| `DOCUSEAL_TEMPLATE_ID` | the template id from step 5 |

The adapter calls DocuSeal's REST API under `/api` (e.g.
`POST {BASE_URL}/api/submissions`).

## Webhook

In DocuSeal → Settings → Webhooks, point the URL at
`{your-backend}/api/webhooks/esign`. The adapter maps
`submission.completed`/`form.completed` → SIGNED (document APPROVED),
`form.declined` → DECLINED, `submission.expired` → EXPIRED,
`submission.archived` → CANCELED.

## Flow

1. `POST /api/deals/{id}/documents/{docId}/sign` → backend creates a
   DocuSeal submission from `DOCUSEAL_TEMPLATE_ID` with the deal's
   field values; signer gets the DocuSeal email; document →
   `signingStatus=SENT`.
2. Signer completes in DocuSeal → webhook → document
   `status=APPROVED`, `signingStatus=SIGNED`.

Note: with this approach the generated `BuyerAgreementPdfRenderer` PDF
is no longer the signed artifact for DocuSeal (DocuSeal serves the
template doc). The renderer is retained for non-DocuSeal/record use;
the signed PDF can be fetched from DocuSeal post-completion if you want
to store a copy (future enhancement).
