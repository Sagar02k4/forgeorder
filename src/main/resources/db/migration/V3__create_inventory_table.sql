CREATE TABLE inventory (
                           product_id UUID PRIMARY KEY,
                           available_quantity INTEGER NOT NULL CHECK (available_quantity >= 0),
                           reserved_quantity INTEGER NOT NULL CHECK (reserved_quantity >= 0),
                           version BIGINT NOT NULL DEFAULT 0,
                           updated_at TIMESTAMPTZ NOT NULL
);