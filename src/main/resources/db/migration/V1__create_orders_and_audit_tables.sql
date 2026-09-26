CREATE TABLE orders (
                        id UUID PRIMARY KEY,
                        customer_id UUID NOT NULL,
                        status VARCHAR(50) NOT NULL,
                        subtotal NUMERIC(19,4) NOT NULL,
                        tax NUMERIC(19,4) NOT NULL,
                        total_amount NUMERIC(19,4) NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL,
                        updated_at TIMESTAMPTZ NOT NULL,
                        version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_status ON orders(status);

CREATE TABLE order_audit_events (
                                    id UUID PRIMARY KEY,
                                    order_id UUID NOT NULL REFERENCES orders(id),
                                    previous_state VARCHAR(50),
                                    new_state VARCHAR(50) NOT NULL,
                                    actor_type VARCHAR(50) NOT NULL,
                                    actor_id VARCHAR(100),
                                    correlation_id VARCHAR(100) NOT NULL,
                                    causation_id VARCHAR(100),
                                    reason VARCHAR(500) NOT NULL,
                                    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_order_audit_events_order_id ON order_audit_events(order_id);
CREATE INDEX idx_order_audit_events_occurred_at ON order_audit_events(occurred_at);