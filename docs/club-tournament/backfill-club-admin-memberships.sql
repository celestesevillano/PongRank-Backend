-- =============================================================================
-- PongRank - Backfill for clubs APPROVED before the automatic admin membership existed.
-- NOT EXECUTED. Requires Emiliano's authorization before running on real data.
--
-- Safe and idempotent:
--   * Only INSERTS; never updates or deletes existing rows.
--   * Skips clubs whose admin already has an APPROVED membership in that club.
--   * Skips admins that already have an active (PENDING/APPROVED) membership in another club
--     (single-club rule): those cases are listed in step 3 for a manual decision.
--   * joined_at is unknown for legacy clubs: now() is used and flagged in this comment.
-- =============================================================================

-- 1) Preview: clubs that would receive the admin membership
SELECT c.id AS club_id, c.name, c.admin_id
FROM clubs c
WHERE c.status = 'APPROVED'
  AND NOT EXISTS (SELECT 1 FROM club_memberships m
                  WHERE m.club_id = c.id AND m.player_id = c.admin_id AND m.status = 'APPROVED')
  AND NOT EXISTS (SELECT 1 FROM club_memberships m
                  WHERE m.player_id = c.admin_id AND m.status IN ('PENDING', 'APPROVED') AND m.club_id <> c.id);

-- 2) Insert (run inside a transaction and review the count before COMMIT)
BEGIN;
INSERT INTO club_memberships (player_id, club_id, status, role, joined_at, created_at, updated_at)
SELECT c.admin_id, c.id, 'APPROVED', 'CLUB_ADMIN', now(), now(), now()
FROM clubs c
WHERE c.status = 'APPROVED'
  AND NOT EXISTS (SELECT 1 FROM club_memberships m
                  WHERE m.club_id = c.id AND m.player_id = c.admin_id AND m.status = 'APPROVED')
  AND NOT EXISTS (SELECT 1 FROM club_memberships m
                  WHERE m.player_id = c.admin_id AND m.status IN ('PENDING', 'APPROVED') AND m.club_id <> c.id);
-- COMMIT;   -- uncomment after checking the "INSERT 0 n" count
-- ROLLBACK; -- or undo

-- 3) Conflicts that need a manual decision (admin already active in another club)
SELECT c.id AS club_id, c.name, c.admin_id, m.club_id AS other_club_id, m.status
FROM clubs c
JOIN club_memberships m ON m.player_id = c.admin_id AND m.status IN ('PENDING', 'APPROVED') AND m.club_id <> c.id
WHERE c.status = 'APPROVED'
  AND NOT EXISTS (SELECT 1 FROM club_memberships x
                  WHERE x.club_id = c.id AND x.player_id = c.admin_id AND x.status = 'APPROVED');
