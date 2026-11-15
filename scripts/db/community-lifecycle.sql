BEGIN;

ALTER TABLE communities ADD COLUMN IF NOT EXISTS responsible_admin_id bigint;
ALTER TABLE communities ADD COLUMN IF NOT EXISTS plan_expires_at timestamp;
ALTER TABLE communities ADD COLUMN IF NOT EXISTS deleted_at timestamp;

DO $$
DECLARE
    existing_constraint record;
BEGIN
    FOR existing_constraint IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid
        WHERE c.conrelid = 'communities'::regclass
          AND c.contype = 'c' AND a.attname = 'status' AND c.conkey = ARRAY[a.attnum]
    LOOP
        EXECUTE format('ALTER TABLE communities DROP CONSTRAINT %I', existing_constraint.conname);
    END LOOP;
END $$;

ALTER TABLE communities ADD CONSTRAINT communities_status_check
    CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DELETED'));

UPDATE communities AS community
SET status = 'DELETED', deleted_at = COALESCE(archived_at, now())
WHERE status = 'ARCHIVED' AND plan_expires_at IS NULL;

UPDATE communities AS community
SET responsible_admin_id = (
    SELECT member.player_id FROM community_memberships AS member
    WHERE member.community_id = community.id AND member.role = 'COMMUNITY_ADMIN' AND member.status = 'ACTIVE'
    ORDER BY (member.player_id <> community.creator_id), member.player_id
    LIMIT 1
)
WHERE responsible_admin_id IS NULL AND status = 'ACTIVE';

DROP INDEX IF EXISTS uq_communities_active_name;
CREATE UNIQUE INDEX uq_communities_active_name ON communities (lower(name))
    WHERE status = 'ACTIVE' OR (status = 'ARCHIVED' AND plan_expires_at IS NOT NULL);

COMMIT;
