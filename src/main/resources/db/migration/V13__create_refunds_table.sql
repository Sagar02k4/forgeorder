CREATE TABLE refunds (
                         id UUID PRIMARY KEY,
                         order_id UUID NOT NULL REFERENCES orders(id),
                         payment_attempt_id UUID NOT NULL REFERENCES payment_attempts(id),
                         idempotency_key VARCHAR(255) NOT NULL UNIQUE,
                         amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
                         status VARCHAR(20) NOT NULL,
                         provider_refund_id VARCHAR(255),
                         created_at TIMESTAMPTZ NOT NULL,
                         updated_at TIMESTAMPTZ NOT NULL,
                         version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_refunds_order_id ON refunds(order_id);