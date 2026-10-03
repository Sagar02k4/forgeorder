CREATE TABLE outbox_events (
                               id UUID PRIMARY KEY,
                               aggregate_type VARCHAR(50) NOT NULL,
                               aggregate_id UUID NOT NULL,
                               event_type VARCHAR(100) NOT NULL,
                               payload TEXT NOT NULL,
                               status VARCHAR(20) NOT NULL,
                               retry_count INTEGER NOT NULL DEFAULT 0,
                               occurred_at TIMESTAMPTZ NOT NULL,
                               published_at TIMESTAMPTZ,
                               version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_events_status ON outbox_events(status);