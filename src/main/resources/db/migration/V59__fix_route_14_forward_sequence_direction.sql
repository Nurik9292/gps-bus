UPDATE route_stops
SET stop_sequence = (
    SELECT MAX(rs2.stop_sequence) + 1
    FROM route_stops rs2
    WHERE rs2.route_id = 'route-legacy-17' AND rs2.direction = 0
) - stop_sequence
WHERE route_id = 'route-legacy-17' AND direction = 0;

UPDATE route_stops rs
SET distance_from_start_meters = ROUND(
    (1.0 - ST_LineLocatePoint(
        br.geometry_forward,
        ST_SetSRID(ST_MakePoint(bs.longitude, bs.latitude), 4326)
    )) * br.total_distance_forward_meters
)::INTEGER
FROM bus_routes br, bus_stops bs
WHERE rs.route_id = 'route-legacy-17'
  AND rs.direction = 0
  AND br.id = rs.route_id
  AND bs.id = rs.stop_id;
