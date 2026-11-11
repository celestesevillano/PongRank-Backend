BEGIN;

ALTER TABLE communities ADD COLUMN IF NOT EXISTS custom_label varchar(40);

DO $$
DECLARE
    existing_constraint record;
BEGIN
    FOR existing_constraint IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid
        WHERE c.conrelid = 'communities'::regclass
          AND (
              (c.contype = 'u' AND a.attname = 'name' AND c.conkey = ARRAY[a.attnum])
              OR (c.contype = 'c' AND a.attname = 'community_type' AND c.conkey = ARRAY[a.attnum])
          )
    LOOP
        EXECUTE format('ALTER TABLE communities DROP CONSTRAINT %I', existing_constraint.conname);
    END LOOP;
END $$;

ALTER TABLE communities ADD CONSTRAINT communities_type_check
    CHECK (community_type IN ('UNIVERSITY', 'SCHOOL', 'PUBLIC_PARK', 'PLAY_GROUP', 'OTHER'));

CREATE UNIQUE INDEX IF NOT EXISTS uq_communities_active_name
    ON communities (lower(name)) WHERE status = 'ACTIVE';

COMMIT;
