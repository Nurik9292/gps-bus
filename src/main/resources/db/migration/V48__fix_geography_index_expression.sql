-- Fix idx_bus_stops_geography: recreate with ST_SetSRID expression
-- so it matches the findAllWalkingEdges query in R2dbcTransitGraphDataRepository.
-- Without this, PostGIS cannot use the GiST index for ST_DWithin calls
-- (functional index expression must match the query expression exactly).
DROP INDEX IF EXISTS idx_bus_stops_geography;

CREATE INDEX IF NOT EXISTS idx_bus_stops_geography
    ON bus_stops
    USING GIST (CAST(ST_SetSRID(ST_MakePoint(longitude::float, latitude::float), 4326) AS geography))
    WHERE is_active = true;

COMMENT ON INDEX idx_bus_stops_geography IS
    'GiST spatial index for ST_DWithin walking-edge queries in transit graph build (must match query expression exactly)';

-- Create idx_bus_routes_id_active_duration if it was missing due to V47 syntax error
CREATE INDEX IF NOT EXISTS idx_bus_routes_id_active_duration
    ON bus_routes (id, is_active, estimated_duration_minutes)
    WHERE is_active = true;

COMMENT ON INDEX idx_bus_routes_id_active_duration IS
    'Covering index for transit graph JOIN: br.id = rs.route_id WHERE br.is_active = true';
