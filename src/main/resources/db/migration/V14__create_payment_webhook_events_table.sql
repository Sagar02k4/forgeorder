CREATE TABLE payment_webhook_events (
                                        provider_event_id VARCHAR(255) PRIMARY KEY,
                                        provider VARCHAR(50) NOT NULL,
                                        event_type VARCHAR(50) NOT NULL,
                                        provider_payment_id VARCHAR(255) NOT NULL,
                                        event_created_at TIMESTAMPTZ NOT NULL,
                                        payload TEXT NOT NULL,
                                        signature VARCHAR(255) NOT NULL,
                                        processed_at TIMESTAMPTZ NOT NULL
);