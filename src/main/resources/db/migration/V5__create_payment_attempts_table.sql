CREATE TABLE payment_attempts (
                                  id UUID PRIMARY KEY,
                                  order_id UUID NOT NULL REFERENCES orders(id),
                                  provider VARCHAR(50) NOT NULL,
                                  provider_payment_id VARCHAR(255) UNIQUE,
                                  idempotency_key VARCHAR(255) NOT NULL UNIQUE,
                                  amount NUMERIC(19,4) NOT NULL,
                                  status VARCHAR(20) NOT NULL,
                                  decline_reason VARCHAR(255),
                                  raw_response TEXT,
                                  created_at TIMESTAMPTZ NOT NULL,
                                  updated_at TIMESTAMPTZ NOT NULL,
                                  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_payment_attempts_order_id ON payment_attempts(order_id);