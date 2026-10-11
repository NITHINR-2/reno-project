-- Phase 6B: frozen quotation documents and public verification.
-- On issue a quotation gets a random, unguessable verification token, an immutable JSON snapshot of everything the
-- PDF shows, and the SHA-256 of the PDF rendered from that snapshot. Later downloads are re-rendered from the snapshot.

ALTER TABLE quotations
    ADD COLUMN verification_token VARCHAR(64),
    ADD COLUMN snapshot_json      JSONB,
    ADD COLUMN pdf_sha256         CHAR(64);

ALTER TABLE quotations
    ADD CONSTRAINT uq_quotations_verification_token UNIQUE (verification_token),
    ADD CONSTRAINT ck_quotations_pdf_sha256 CHECK (pdf_sha256 IS NULL OR pdf_sha256 ~ '^[0-9a-f]{64}$'),
    -- A document is frozen as a whole: snapshot and hash are written together, once.
    ADD CONSTRAINT ck_quotations_snapshot_hash CHECK ((snapshot_json IS NULL) = (pdf_sha256 IS NULL)),
    -- Drafts are never verifiable; snapshots exist only for issued documents.
    ADD CONSTRAINT ck_quotations_document_issued CHECK (
        status IN ('ISSUED', 'SUPERSEDED') OR (verification_token IS NULL AND snapshot_json IS NULL));

-- Quotations issued before this migration get a token now (2 x 122 random bits from gen_random_uuid, hex).
-- Their snapshot and PDF hash are frozen by the application on first download, from the issued columns, which never
-- change after issue.
UPDATE quotations
SET verification_token = replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', '')
WHERE status IN ('ISSUED', 'SUPERSEDED');

-- The snapshot may never change once written.
CREATE FUNCTION quotations_freeze_snapshot() RETURNS trigger AS $$
BEGIN
    IF OLD.snapshot_json IS NOT NULL AND (NEW.snapshot_json IS DISTINCT FROM OLD.snapshot_json
            OR NEW.pdf_sha256 IS DISTINCT FROM OLD.pdf_sha256
            OR NEW.verification_token IS DISTINCT FROM OLD.verification_token) THEN
        RAISE EXCEPTION 'quotation % document snapshot is immutable', OLD.quotation_number
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_quotations_freeze_snapshot
    BEFORE UPDATE ON quotations
    FOR EACH ROW EXECUTE FUNCTION quotations_freeze_snapshot();
