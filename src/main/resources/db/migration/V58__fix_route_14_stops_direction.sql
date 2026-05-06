UPDATE route_stops SET direction = 0
WHERE route_id = 'route-legacy-17'
  AND stop_id IN ('stop-legacy-1112', 'stop-legacy-458', 'stop-legacy-599');

UPDATE route_stops SET direction = 1
WHERE route_id = 'route-legacy-17'
  AND stop_id IN ('stop-legacy-137', 'stop-legacy-208');

DELETE FROM route_stops
WHERE route_id = 'route-legacy-17' AND stop_id = 'stop-legacy-949' AND direction = 0;

DELETE FROM route_stops
WHERE route_id = 'route-legacy-17' AND stop_id = 'stop-legacy-63';

INSERT INTO route_stops (id, route_id, stop_id, stop_sequence, direction, created_at)
VALUES (gen_random_uuid()::text, 'route-legacy-17', 'stop-legacy-1076', 9999, 1, CURRENT_TIMESTAMP);

WITH r AS (SELECT geometry_forward gf, geometry_backward gb FROM bus_routes WHERE id='route-legacy-17'),
new_seqs AS (
    SELECT rs.id,
        ROW_NUMBER() OVER (PARTITION BY rs.direction ORDER BY
            CASE WHEN rs.direction = 0 THEN
                ST_LineLocatePoint(r.gf, ST_SetSRID(ST_MakePoint(bs.longitude, bs.latitude), 4326))
            ELSE
                ST_LineLocatePoint(r.gb, ST_SetSRID(ST_MakePoint(bs.longitude, bs.latitude), 4326))
            END
        ) AS new_seq
    FROM route_stops rs JOIN bus_stops bs ON rs.stop_id = bs.id, r
    WHERE rs.route_id = 'route-legacy-17'
)
UPDATE route_stops rs
SET stop_sequence = ns.new_seq
FROM new_seqs ns
WHERE rs.id = ns.id;

UPDATE route_stops rs
SET distance_from_start_meters = ROUND(
    ST_LineLocatePoint(
        CASE WHEN rs.direction = 0 THEN br.geometry_forward ELSE br.geometry_backward END,
        ST_SetSRID(ST_MakePoint(bs.longitude, bs.latitude), 4326)
    ) *
    CASE WHEN rs.direction = 0 THEN br.total_distance_forward_meters ELSE br.total_distance_backward_meters END
)::INTEGER
FROM bus_routes br, bus_stops bs
WHERE rs.route_id = 'route-legacy-17'
  AND br.id = rs.route_id
  AND bs.id = rs.stop_id;
