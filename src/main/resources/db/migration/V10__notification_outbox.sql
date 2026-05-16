-- Durable outbox for notification delivery: a notification is persisted
-- first and dispatched on a best-effort inline attempt; anything not
-- delivered stays PENDING and is retried by a scheduled processor with
-- a bounded attempt count.

ALTER TABLE notification
    ADD COLUMN dispatch_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN dispatch_attempts INTEGER NOT NULL DEFAULT 0;

-- Rows that already carry a dispatched_at from before this migration
-- were delivered synchronously; mark them terminal so the processor
-- does not re-send them.
UPDATE notification SET dispatch_status = 'DISPATCHED' WHERE dispatched_at IS NOT NULL;

CREATE INDEX idx_notification_dispatch_status ON notification (dispatch_status, dispatch_attempts);
