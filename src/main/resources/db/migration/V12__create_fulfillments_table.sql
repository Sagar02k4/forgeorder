CREATE TABLE fulfillments (
                              id UUID PRIMARY KEY,
                              order_id UUID NOT NULL UNIQUE REFERENCES orders(id),
                              shipment_reference VARCHAR(255),
                              status VARCHAR(20) NOT NULL,
                              created_at TIMESTAMPTZ NOT NULL,
                              updated_at TIMESTAMPTZ NOT NULL
);