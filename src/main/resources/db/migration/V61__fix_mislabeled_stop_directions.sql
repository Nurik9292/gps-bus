WITH per_stop AS (
    SELECT br.id AS route_id, rs.id AS rs_id, rs.stop_id, rs.direction AS old_dir,
        ST_Distance(br.geometry_forward::geography,
            ST_SetSRID(ST_MakePoint(bs.longitude, bs.latitude), 4326)::geography) AS d_F,
        ST_Distance(br.geometry_backward::geography,
            ST_SetSRID(ST_MakePoint(bs.longitude, bs.latitude), 4326)::geography) AS d_B
    FROM bus_routes br
    JOIN route_stops rs ON rs.route_id = br.id
    JOIN bus_stops bs ON bs.id = rs.stop_id
    WHERE br.is_active = true
      AND br.geometry_forward IS NOT NULL
      AND br.geometry_backward IS NOT NULL
      AND ST_Y(ST_StartPoint(br.geometry_forward)) BETWEEN 36 AND 42
      AND NOT ST_Equals(br.geometry_forward, br.geometry_backward)
      AND br.route_number NOT IN
          ('1A','25','2A','3','37','3A','41','42','47','55','67','8','96','84','1AL','68')
),
mislabeled AS (
    SELECT route_id, rs_id, stop_id, old_dir, d_F, d_B,
        CASE WHEN d_F < d_B THEN 0 ELSE 1 END AS new_dir
    FROM per_stop
    WHERE ABS(d_F - d_B) > 30
      AND ((old_dir = 0 AND d_B < d_F) OR (old_dir = 1 AND d_F < d_B))
      AND LEAST(d_F, d_B) < 100
)
DELETE FROM route_stops
WHERE id IN (
    SELECT m.rs_id FROM mislabeled m
    WHERE EXISTS (
        SELECT 1 FROM route_stops rs2
        WHERE rs2.route_id = m.route_id
          AND rs2.stop_id = m.stop_id
          AND rs2.direction = m.new_dir
    )
);

WITH per_stop AS (
    SELECT br.id AS route_id, rs.id AS rs_id, rs.stop_id, rs.direction AS old_dir,
        ST_Distance(br.geometry_forward::geography,
            ST_SetSRID(ST_MakePoint(bs.longitude, bs.latitude), 4326)::geography) AS d_F,
        ST_Distance(br.geometry_backward::geography,
            ST_SetSRID(ST_MakePoint(bs.longitude, bs.latitude), 4326)::geography) AS d_B
    FROM bus_routes br
    JOIN route_stops rs ON rs.route_id = br.id
    JOIN bus_stops bs ON bs.id = rs.stop_id
    WHERE br.is_active = true
      AND br.geometry_forward IS NOT NULL
      AND br.geometry_backward IS NOT NULL
      AND ST_Y(ST_StartPoint(br.geometry_forward)) BETWEEN 36 AND 42
      AND NOT ST_Equals(br.geometry_forward, br.geometry_backward)
      AND br.route_number NOT IN
          ('1A','25','2A','3','37','3A','41','42','47','55','67','8','96','84','1AL','68')
),
mislabeled AS (
    SELECT route_id, rs_id, stop_id, old_dir, d_F, d_B,
        CASE WHEN d_F < d_B THEN 0 ELSE 1 END AS new_dir
    FROM per_stop
    WHERE ABS(d_F - d_B) > 30
      AND ((old_dir = 0 AND d_B < d_F) OR (old_dir = 1 AND d_F < d_B))
      AND LEAST(d_F, d_B) < 100
)
UPDATE route_stops rs
SET direction = m.new_dir
FROM mislabeled m
WHERE rs.id = m.rs_id;
