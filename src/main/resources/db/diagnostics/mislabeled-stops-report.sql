\set HIGH_DIST_THRESHOLD 200
\set WRONG_DIR_OPP_THRESHOLD 50
\set LOW_DIST_THRESHOLD 80
\set SEQ_RANK_DIFF_THRESHOLD 2

\echo
\echo '=== mislabeled-stops audit ==='
\echo 'thresholds:'
\echo '  HIGH_DIST_THRESHOLD     =' :HIGH_DIST_THRESHOLD 'm  (stop further than this from its own polyline)'
\echo '  WRONG_DIR_OPP_THRESHOLD =' :WRONG_DIR_OPP_THRESHOLD 'm  (stop closer than this to the opposite polyline => HIGH_WRONG_DIRECTION)'
\echo '  LOW_DIST_THRESHOLD      =' :LOW_DIST_THRESHOLD 'm  (borderline distance window: > LOW and <= HIGH)'
\echo '  SEQ_RANK_DIFF_THRESHOLD =' :SEQ_RANK_DIFF_THRESHOLD '   (positions out of order along polyline)'
\echo

DROP TABLE IF EXISTS tmp_mislabeled_stops_audit;

CREATE TEMP TABLE tmp_mislabeled_stops_audit AS
WITH stop_geom AS (
    SELECT
        rs.id AS rs_id,
        br.id AS route_id,
        br.route_number,
        rs.direction,
        rs.stop_id,
        rs.stop_sequence,
        bs.stop_name,
        bs.latitude,
        bs.longitude,
        ST_SetSRID(ST_MakePoint(bs.longitude, bs.latitude), 4326) AS stop_pt_geom,
        ST_SetSRID(ST_MakePoint(bs.longitude, bs.latitude), 4326)::geography AS stop_pt_geog,
        CASE rs.direction WHEN 0 THEN br.geometry_forward  ELSE br.geometry_backward END AS own_geom,
        CASE rs.direction WHEN 0 THEN br.geometry_backward ELSE br.geometry_forward  END AS opp_geom
    FROM route_stops rs
    JOIN bus_routes br ON br.id = rs.route_id
    JOIN bus_stops  bs ON bs.id = rs.stop_id
    WHERE br.is_active = true
      AND bs.is_active = true
),
distances AS (
    SELECT
        rs_id, route_id, route_number, direction, stop_id, stop_sequence, stop_name, latitude, longitude,
        ROUND(ST_Distance(stop_pt_geog, own_geom::geography)::numeric, 1) AS dist_own_m,
        CASE WHEN opp_geom IS NULL THEN NULL
             ELSE ROUND(ST_Distance(stop_pt_geog, opp_geom::geography)::numeric, 1) END AS dist_opp_m,
        ST_LineLocatePoint(own_geom, stop_pt_geom) AS frac_own
    FROM stop_geom
    WHERE own_geom IS NOT NULL
),
ranked AS (
    SELECT
        d.*,
        DENSE_RANK() OVER (PARTITION BY route_id, direction ORDER BY stop_sequence)        AS seq_rank,
        DENSE_RANK() OVER (PARTITION BY route_id, direction ORDER BY frac_own, stop_sequence) AS frac_rank,
        COUNT(*)    OVER (PARTITION BY route_id, direction)                                  AS stops_in_dir
    FROM distances d
),
classified AS (
    SELECT
        r.*,
        ABS(r.seq_rank - r.frac_rank) AS rank_diff,
        CASE
            WHEN r.dist_own_m > :HIGH_DIST_THRESHOLD
                 AND r.dist_opp_m IS NOT NULL
                 AND r.dist_opp_m < :WRONG_DIR_OPP_THRESHOLD
                THEN 'HIGH_WRONG_DIRECTION'
            WHEN r.dist_own_m > :HIGH_DIST_THRESHOLD
                THEN 'HIGH_FAR_FROM_ROUTE'
            WHEN r.dist_own_m <= :HIGH_DIST_THRESHOLD
                 AND ABS(r.seq_rank - r.frac_rank) >= :SEQ_RANK_DIFF_THRESHOLD
                THEN 'MEDIUM_BAD_SEQUENCE'
            WHEN r.dist_own_m > :LOW_DIST_THRESHOLD
                 AND r.dist_own_m <= :HIGH_DIST_THRESHOLD
                THEN 'LOW_BORDERLINE'
            ELSE 'OK'
        END AS tier
    FROM ranked r
)
SELECT * FROM classified WHERE tier <> 'OK';

\echo
\echo '--- Summary by tier ---'
SELECT tier, COUNT(*) AS rows
FROM tmp_mislabeled_stops_audit
GROUP BY tier
ORDER BY CASE tier
    WHEN 'HIGH_WRONG_DIRECTION' THEN 1
    WHEN 'HIGH_FAR_FROM_ROUTE'  THEN 2
    WHEN 'MEDIUM_BAD_SEQUENCE'  THEN 3
    WHEN 'LOW_BORDERLINE'       THEN 4
END;

\echo
\echo '--- Top 20 routes by total flagged rows ---'
SELECT route_number,
       COUNT(*) FILTER (WHERE tier = 'HIGH_WRONG_DIRECTION') AS high_wrong_dir,
       COUNT(*) FILTER (WHERE tier = 'HIGH_FAR_FROM_ROUTE')  AS high_far,
       COUNT(*) FILTER (WHERE tier = 'MEDIUM_BAD_SEQUENCE')  AS medium_seq,
       COUNT(*) FILTER (WHERE tier = 'LOW_BORDERLINE')       AS low_border,
       COUNT(*) AS total_flagged
FROM tmp_mislabeled_stops_audit
GROUP BY route_number
ORDER BY total_flagged DESC, route_number
LIMIT 20;

\echo
\echo '--- HIGH_WRONG_DIRECTION (manual review priority) ---'
SELECT route_number,
       direction,
       stop_sequence AS seq,
       stop_id,
       stop_name,
       dist_own_m AS d_own,
       dist_opp_m AS d_opp,
       ROUND(frac_own::numeric, 4) AS frac
FROM tmp_mislabeled_stops_audit
WHERE tier = 'HIGH_WRONG_DIRECTION'
ORDER BY route_number, direction, stop_sequence;

\echo
\echo '--- HIGH_FAR_FROM_ROUTE (stop probably should not be on this route) ---'
SELECT route_number,
       direction,
       stop_sequence AS seq,
       stop_id,
       stop_name,
       dist_own_m AS d_own,
       dist_opp_m AS d_opp
FROM tmp_mislabeled_stops_audit
WHERE tier = 'HIGH_FAR_FROM_ROUTE'
ORDER BY dist_own_m DESC, route_number, direction, stop_sequence;

\echo
\echo '--- MEDIUM_BAD_SEQUENCE (close to polyline, but in wrong order) ---'
SELECT route_number,
       direction,
       stop_sequence AS seq,
       seq_rank,
       frac_rank,
       rank_diff,
       stop_id,
       stop_name,
       dist_own_m AS d_own,
       ROUND(frac_own::numeric, 4) AS frac
FROM tmp_mislabeled_stops_audit
WHERE tier = 'MEDIUM_BAD_SEQUENCE'
ORDER BY rank_diff DESC, route_number, direction, stop_sequence;

\echo
\echo '--- LOW_BORDERLINE counts only (full list available via ad-hoc query) ---'
SELECT COUNT(*) AS low_borderline_total FROM tmp_mislabeled_stops_audit WHERE tier = 'LOW_BORDERLINE';

\echo
\echo '=== end of report ==='
