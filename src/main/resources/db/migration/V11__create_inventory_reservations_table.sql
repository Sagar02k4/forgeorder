CREATE TABLE inventory_reservations (
                                        id UUID PRIMARY KEY,
                                        order_id UUID NOT NULL REFERENCES orders(id),
                                        product_id UUID NOT NULL,
                                        quantity INTEGER NOT NULL,
                                        status VARCHAR(20) NOT NULL,
                                        expires_at TIMESTAMPTZ NOT NULL,
                                        created_at TIMESTAMPTZ NOT NULL,
                                        released_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_inventory_reservations_active_order
    ON inventory_reservations(order_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_inventory_reservations_expires_at ON inventory_reservations(expires_at);