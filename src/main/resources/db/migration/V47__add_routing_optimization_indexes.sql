
CREATE INDEX IF NOT EXISTS idx_bus_stops_geography
    ON bus_stops
    USING GIST (CAST(ST_SetSRID(ST_MakePoint(longitude::float, latitude::float), 4326) AS geography))
    WHERE is_active = true;



CREATE INDEX IF NOT EXISTS idx_route_stops_stop_dir_route_seq
    ON route_stops (stop_id, direction, route_id, stop_sequence);

COMMENT ON INDEX idx_route_stops_stop_dir_route_seq IS
    'Covering index for transfer-route JOIN: stop_id=X AND direction=N → immediately gives route_id+stop_sequence';


CREATE INDEX IF NOT EXISTS idx_vehicles_device_id
    ON vehicles (device_id)
    WHERE is_active = true;

COMMENT ON INDEX idx_vehicles_device_id IS
    'Partial index for GPS tick updates: WHERE device_id = :id AND is_active = true';


CREATE INDEX IF NOT EXISTS idx_vehicles_provider_active
    ON vehicles (gps_provider, is_active)
    WHERE is_active = true;

COMMENT ON INDEX idx_vehicles_provider_active IS
    'Index for grouping active vehicle device IDs by GPS provider (runs every 5s)';


CREATE INDEX IF NOT EXISTS idx_bus_stops_stop_name_trgm
    ON bus_stops USING GIN (stop_name gin_trgm_ops)
    WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_bus_stops_name_en_trgm
    ON bus_stops USING GIN (name_en gin_trgm_ops)
    WHERE is_active = true AND name_en IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_bus_stops_name_tm_trgm
    ON bus_stops USING GIN (name_tm gin_trgm_ops)
    WHERE is_active = true AND name_tm IS NOT NULL;

COMMENT ON INDEX idx_bus_stops_stop_name_trgm IS
    'pg_trgm GIN index for ILIKE searches on stop_name (leading-wildcard safe)';



CREATE INDEX IF NOT EXISTS idx_route_stops_route_dir_seq_stop
    ON route_stops (route_id, direction, stop_sequence, stop_id);

COMMENT ON INDEX idx_route_stops_route_dir_seq_stop IS
    'Covering index: (route_id, direction, stop_sequence) join + stop_id filter/return without heap fetch';


CREATE INDEX IF NOT EXISTS idx_bus_routes_id_active_duration
    ON bus_routes (id, is_active, estimated_duration_minutes)
    WHERE is_active = true;

COMMENT ON INDEX idx_bus_routes_id_active_duration IS
    'Covering index for transit graph JOIN: br.id = rs.route_id WHERE br.is_active = true';


CREATE INDEX IF NOT EXISTS idx_vehicles_assigned_route_partial
    ON vehicles (assigned_route_id)
    WHERE is_active = true AND assigned_route_id IS NOT NULL;

COMMENT ON INDEX idx_vehicles_assigned_route_partial IS
    'Lean partial index for route_vehicle_counts CTE (COUNT active vehicles per route)';
