-- Plants and live stock (Phase D3): stock is counted as on_hand and reserved; available = on_hand - reserved.
-- Old columns map exactly: reserved = quantity_allocated, on_hand = quantity_available + quantity_allocated.

ALTER TABLE inventory_stock RENAME COLUMN quantity_allocated TO reserved;
ALTER TABLE inventory_stock ADD COLUMN on_hand INTEGER;
UPDATE inventory_stock SET on_hand = quantity_available + reserved;
ALTER TABLE inventory_stock ALTER COLUMN on_hand SET NOT NULL;
ALTER TABLE inventory_stock ALTER COLUMN on_hand SET DEFAULT 0;
ALTER TABLE inventory_stock DROP CONSTRAINT ck_inventory_stock_available;
ALTER TABLE inventory_stock DROP CONSTRAINT ck_inventory_stock_allocated;
ALTER TABLE inventory_stock DROP COLUMN quantity_available;

-- Last line of defence: nothing is ever reserved beyond what is physically there.
ALTER TABLE inventory_stock ADD CONSTRAINT ck_inventory_stock_reserved CHECK (reserved >= 0 AND reserved <= on_hand);
ALTER TABLE inventory_stock ADD COLUMN available INTEGER GENERATED ALWAYS AS (on_hand - reserved) STORED;

-- Stock is held per colour; NULL colour = unpainted/unspecified (the HQ yard stock). Colour must match exactly.
ALTER TABLE inventory_stock ADD COLUMN colour_id BIGINT REFERENCES vehicle_model_colours (id);
ALTER TABLE inventory_stock DROP CONSTRAINT uq_inventory_stock_location_variant;
CREATE UNIQUE INDEX ux_inventory_stock_location_variant_colour
    ON inventory_stock (location_id, variant_id, COALESCE(colour_id, 0));
CREATE INDEX ix_inventory_stock_colour ON inventory_stock (colour_id);

-- Requests keep the colour name snapshot (V14) and now also the colour row, so reservations match stock by id.
ALTER TABLE fleet_requests ADD COLUMN colour_id BIGINT REFERENCES vehicle_model_colours (id);
CREATE INDEX ix_fleet_requests_colour ON fleet_requests (colour_id);
UPDATE fleet_requests r SET colour_id = c.id
FROM vehicle_variants v JOIN vehicle_model_colours c ON c.model_id = v.model_id
WHERE r.variant_id = v.id AND r.colour IS NOT NULL AND lower(c.name) = lower(r.colour);
-- A colour row implies its name snapshot. (Not the reverse: a colour renamed since V14 may leave colour_id NULL.)
ALTER TABLE fleet_requests ADD CONSTRAINT ck_fleet_requests_colour CHECK (colour_id IS NULL OR colour IS NOT NULL);
