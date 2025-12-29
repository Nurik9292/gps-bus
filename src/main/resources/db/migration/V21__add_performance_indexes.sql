CREATE INDEX IF NOT EXISTS idx_route_stops_route_direction
    ON route_stops (route_id, direction);


CREATE INDEX IF NOT EXISTS idx_route_stops_route_dir_seq
    ON route_stops (route_id, direction, stop_sequence);

CREATE INDEX IF NOT EXISTS idx_route_stops_stop_id
    ON route_stops (stop_id) WHERE stop_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_route_stops_stop_route_dir
    ON route_stops (stop_id, route_id, direction);


CREATE INDEX IF NOT EXISTS idx_vehicles_active_route
    ON vehicles (assigned_route_id, is_active) WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_vehicles_position_update
    ON vehicles (last_position_update) WHERE is_active = true
    AND current_latitude IS NOT NULL AND current_longitude IS NOT NULL;


CREATE INDEX IF NOT EXISTS idx_bus_routes_active
    ON bus_routes (is_active) WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_bus_routes_number_active
    ON bus_routes (route_number, is_active);


CREATE INDEX IF NOT EXISTS idx_bus_stops_active
    ON bus_stops (is_active) WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_bus_stops_major_active
    ON bus_stops (is_major_stop, is_active) WHERE is_major_stop = true AND is_active = true;

COMMENT ON INDEX idx_route_stops_route_direction IS
    'Composite index for efficient route_stops JOIN operations in routing queries';

COMMENT ON INDEX idx_route_stops_stop_id IS
    'Partial index for stop_id lookups in ANY(:stopIds) queries';

COMMENT ON INDEX idx_vehicles_active_route IS
    'Partial index for fast vehicle counting by route (used in all routing queries)';

COMMENT ON INDEX idx_bus_stops_major_active IS
    'Partial index for transfer stop lookups (prefers major stops for better infrastructure)';
