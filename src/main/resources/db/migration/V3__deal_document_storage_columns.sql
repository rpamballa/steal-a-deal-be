-- Persist storage metadata so deal documents can carry real uploaded
-- bytes (local filesystem today, S3 or equivalent later) instead of
-- placeholder fileName strings.

ALTER TABLE deal_document
    ADD COLUMN storage_key VARCHAR(255),
    ADD COLUMN content_type VARCHAR(255),
    ADD COLUMN size_bytes BIGINT;

CREATE INDEX idx_deal_document_storage_key ON deal_document (storage_key);
