ALTER TABLE vehicles
ADD COLUMN IF NOT EXISTS current_direction SMALLINT DEFAULT NULL;

CREATE INDEX IF NOT EXISTS idx_vehicles_direction ON vehicles(current_direction)
WHERE current_direction IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_vehicles_route_direction ON vehicles(assigned_route_id, current_direction)
WHERE assigned_route_id IS NOT NULL;

ALTER TABLE vehicles
ADD COLUMN IF NOT EXISTS last_stop_sequence INTEGER DEFAULT NULL;

COMMENT ON COLUMN vehicles.current_direction IS 'Current travel direction: 0=forward, 1=backward, NULL=unknown';
COMMENT ON COLUMN vehicles.last_stop_sequence IS 'Last known stop sequence on the route, used for direction detection';
