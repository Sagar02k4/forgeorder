CREATE TABLE products (
                          id UUID PRIMARY KEY,
                          name VARCHAR(255) NOT NULL,
                          price NUMERIC(19,4) NOT NULL CHECK (price >= 0),
                          tax_rate NUMERIC(5,4) NOT NULL CHECK (tax_rate >= 0),
                          created_at TIMESTAMPTZ NOT NULL,
                          updated_at TIMESTAMPTZ NOT NULL
);

-- Seed products matching our existing test inventory product IDs
INSERT INTO products (id, name, price, tax_rate, created_at, updated_at) VALUES
                                                                             ('22222222-2222-2222-2222-222222222222', 'Test Product A', 100.00, 0.18, now(), now()),
                                                                             ('33333333-3333-3333-3333-333333333333', 'Test Product B', 50.00, 0.18, now(), now()),
                                                                             ('44444444-4444-4444-4444-444444444444', 'Test Product C (Low Stock)', 25.00, 0.18, now(), now());