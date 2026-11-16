BEGIN;

ALTER TABLE clubs ADD COLUMN IF NOT EXISTS plan_expires_at timestamp;
ALTER TABLE clubs ADD COLUMN IF NOT EXISTS description varchar(500);

DO $$
DECLARE
    existing_constraint record;
BEGIN
    FOR existing_constraint IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid
        WHERE c.conrelid = 'clubs'::regclass
          AND c.contype = 'c' AND a.attname = 'status' AND c.conkey = ARRAY[a.attnum]
    LOOP
        EXECUTE format('ALTER TABLE clubs DROP CONSTRAINT %I', existing_constraint.conname);
    END LOOP;
END $$;

ALTER TABLE clubs ADD CONSTRAINT clubs_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'PAUSED', 'DELETED'));

COMMIT;
