-- Model images may also be site-relative paths served by the frontend (e.g. /cars/duster.jpg), not only https URLs.
ALTER TABLE vehicle_models DROP CONSTRAINT ck_vehicle_models_image_url;
ALTER TABLE vehicle_models ADD CONSTRAINT ck_vehicle_models_image_url CHECK (
    image_url IS NULL OR image_url ~ '^https://\S+$' OR image_url ~ '^/[A-Za-z0-9._/-]+$');
