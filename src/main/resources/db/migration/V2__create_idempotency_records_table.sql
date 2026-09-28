CREATE TABLE idempotency_records (
                                     idempotency_key VARCHAR(255) PRIMARY KEY,
                                     operation_type VARCHAR(100) NOT NULL,
                                     actor_id VARCHAR(100),
                                     request_hash VARCHAR(255) NOT NULL,
                                     status VARCHAR(20) NOT NULL,
                                     response_status_code INTEGER,
                                     response_body TEXT,
                                     created_at TIMESTAMPTZ NOT NULL,
                                     expires_at TIMESTAMPTZ NOT NULL,
                                     version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_idempotency_records_expires_at ON idempotency_records(expires_at);