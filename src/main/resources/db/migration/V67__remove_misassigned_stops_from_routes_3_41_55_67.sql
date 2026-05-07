DELETE FROM route_stops
WHERE id IN (
    SELECT rs.id
    FROM route_stops rs
    JOIN bus_stops bs ON bs.id = rs.stop_id
    JOIN bus_routes br ON br.id = rs.route_id
    WHERE br.route_number IN ('3', '41', '55', '67')
      AND LEAST(
          ST_Distance(br.geometry_forward::geography,
              ST_SetSRID(ST_MakePoint(bs.longitude, bs.latitude), 4326)::geography),
          ST_Distance(br.geometry_backward::geography,
              ST_SetSRID(ST_MakePoint(bs.longitude, bs.latitude), 4326)::geography)
      ) > 100
);
