-- Run with psql from the directory containing the reviewed fdptm-ranking.csv.
\set ON_ERROR_STOP on
BEGIN;
CREATE TEMP TABLE fdptm_import (
    name varchar(255) NOT NULL CHECK (btrim(name) <> ''),
    club varchar(255),
    category varchar(50) NOT NULL CHECK (btrim(category) <> ''),
    ranking_date date NOT NULL,
    ranking_position integer NOT NULL CHECK (ranking_position > 0),
    PRIMARY KEY (ranking_date, category, ranking_position)
) ON COMMIT DROP;
\copy fdptm_import (name, club, category, ranking_date, ranking_position) FROM 'fdptm-ranking.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')
INSERT INTO federated_ranking_entry (name, club, category, ranking_date, ranking_position)
SELECT name, club, category, ranking_date, ranking_position FROM fdptm_import
ON CONFLICT (ranking_date, category, ranking_position) DO NOTHING;
-- Keep entries used by existing reviews immutable.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM fdptm_import AS imported
        JOIN federated_ranking_entry AS existing USING (ranking_date, category, ranking_position)
        WHERE (imported.name, imported.club) IS DISTINCT FROM (existing.name, existing.club)
    ) THEN
        RAISE EXCEPTION 'La versión del padrón ya contiene datos diferentes';
    END IF;
END $$;
COMMIT;
