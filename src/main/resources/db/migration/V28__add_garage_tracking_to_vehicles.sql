
ALTER TABLE vehicles
    ADD COLUMN IF NOT EXISTS last_garage_id VARCHAR(36),
    ADD COLUMN IF NOT EXISTS garage_entry_time TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS garage_exit_time TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS is_in_garage BOOLEAN DEFAULT false;

ALTER TABLE vehicles
    ADD COLUMN IF NOT EXISTS route_source VARCHAR(20) DEFAULT 'UNKNOWN',
    ADD COLUMN IF NOT EXISTS route_confidence INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS gps_detection_enabled BOOLEAN DEFAULT true;

ALTER TABLE vehicles
    ADD COLUMN IF NOT EXISTS assigned_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS manual_assignment_reason VARCHAR(500);

ALTER TABLE vehicles
    ADD CONSTRAINT fk_vehicles_last_garage
    FOREIGN KEY (last_garage_id) REFERENCES garages(id)
    ON DELETE SET NULL;


ALTER TABLE vehicles
    DROP CONSTRAINT IF EXISTS chk_vehicles_route_source;

ALTER TABLE vehicles
    ADD CONSTRAINT chk_vehicles_route_source
    CHECK (route_source IN (
        'SHIFT_ASSIGNMENT',
        'GPS_DETECTED',
        'MANUAL',
        'EXTERNAL_API',
        'UNKNOWN'
    ));

ALTER TABLE vehicles
    ADD CONSTRAINT chk_vehicles_route_confidence
    CHECK (route_confidence >= 0 AND route_confidence <= 100);

CREATE INDEX idx_vehicles_in_garage
    ON vehicles(is_in_garage)
    WHERE is_in_garage = true;

CREATE INDEX idx_vehicles_route_source
    ON vehicles(route_source);

CREATE INDEX idx_vehicles_last_garage
    ON vehicles(last_garage_id)
    WHERE last_garage_id IS NOT NULL;

CREATE INDEX idx_vehicles_assigned_by
    ON vehicles(assigned_by)
    WHERE assigned_by IS NOT NULL;


UPDATE vehicles
SET route_source = 'UNKNOWN',
    route_confidence = 0
WHERE assigned_route_id IS NOT NULL
  AND (route_source IS NULL OR route_source = '');

COMMENT ON COLUMN vehicles.last_garage_id IS 'Last garage where vehicle was located (FK to garages.id)';
COMMENT ON COLUMN vehicles.garage_entry_time IS 'Timestamp when vehicle entered the garage zone';
COMMENT ON COLUMN vehicles.garage_exit_time IS 'Timestamp when vehicle exited the garage zone';
COMMENT ON COLUMN vehicles.is_in_garage IS 'Is vehicle currently inside garage geofence zone';

COMMENT ON COLUMN vehicles.route_source IS 'Source of route assignment: SHIFT_ASSIGNMENT (planned), GPS_DETECTED (auto), MANUAL (dispatcher), EXTERNAL_API (deprecated), UNKNOWN';
COMMENT ON COLUMN vehicles.route_confidence IS 'Confidence level 0-100% for GPS detected routes (100% for manual/shift assignments)';
COMMENT ON COLUMN vehicles.gps_detection_enabled IS 'Allow GPS-based automatic route detection for this vehicle';

COMMENT ON COLUMN vehicles.assigned_by IS 'Username who manually assigned the route (only for route_source=MANUAL)';
COMMENT ON COLUMN vehicles.manual_assignment_reason IS 'Reason for manual route assignment (optional)';
