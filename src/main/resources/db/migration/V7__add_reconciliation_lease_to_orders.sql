ALTER TABLE orders ADD COLUMN reconciliation_leased_by VARCHAR(100);
ALTER TABLE orders ADD COLUMN reconciliation_leased_until TIMESTAMPTZ;