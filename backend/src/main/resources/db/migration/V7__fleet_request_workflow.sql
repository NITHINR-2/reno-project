-- Phase 2: stock-mapping marker, production invariant and request-number sequence.

-- "Qualify (stock mapped)" must be provable even when no stock was available, so mapping is recorded explicitly.
ALTER TABLE fleet_requests
    ADD COLUMN stock_mapped_at    TIMESTAMPTZ,
    ADD COLUMN stock_mapped_by_id BIGINT REFERENCES users (id);
CREATE INDEX ix_fleet_requests_stock_mapped_by ON fleet_requests (stock_mapped_by_id);

ALTER TABLE fleet_requests
    ADD CONSTRAINT ck_fleet_requests_stock_mapped CHECK ((stock_mapped_at IS NULL) = (stock_mapped_by_id IS NULL));

-- productionRequired = max(0, quantity - allocated); allocated <= quantity is already enforced, so this is exact.
UPDATE fleet_requests SET production_required = quantity - allocated_qty
WHERE production_required <> quantity - allocated_qty;
ALTER TABLE fleet_requests
    ADD CONSTRAINT ck_fleet_requests_production_formula CHECK (production_required = quantity - allocated_qty);

-- New request numbers are FLT-<year>-<n>; starting at 1000 keeps generated numbers clear of hand-seeded ones (e.g. 0891).
CREATE SEQUENCE fleet_request_number_seq START WITH 1000;
