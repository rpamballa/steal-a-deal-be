-- Record outbound delivery of a notification so we can tell which
-- channels (email/SMS/push) a recipient was actually reached on and
-- when, rather than only persisting the in-app row.

ALTER TABLE notification
    ADD COLUMN dispatched_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN dispatch_channels VARCHAR(255);
