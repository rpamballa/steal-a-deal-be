-- Track e-signature envelope state per document so the buyer agreement
-- can flow through DocuSign (or any future provider) without losing the
-- envelope reference between requests.

ALTER TABLE deal_document
    ADD COLUMN signing_envelope_id VARCHAR(255),
    ADD COLUMN signing_status VARCHAR(32);

CREATE INDEX idx_deal_document_signing_envelope ON deal_document (signing_envelope_id);
