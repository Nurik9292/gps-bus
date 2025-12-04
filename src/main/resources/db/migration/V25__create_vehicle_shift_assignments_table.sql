CREATE TABLE vehicle_shift_assignments (
    id VARCHAR(36) PRIMARY KEY,
    vehicle_id VARCHAR(36) NOT NULL,
    route_id VARCHAR(36) NOT NULL,
    shift_type VARCHAR(20) NOT NULL,  -- FIRST or SECOND
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,

    CONSTRAINT fk_shift_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(id) ON DELETE CASCADE,
    CONSTRAINT fk_shift_route FOREIGN KEY (route_id) REFERENCES bus_routes(id) ON DELETE CASCADE,
    CONSTRAINT chk_shift_type CHECK (shift_type IN ('FIRST', 'SECOND'))
);

CREATE UNIQUE INDEX idx_vehicle_shift_unique
    ON vehicle_shift_assignments(vehicle_id, shift_type)
    WHERE is_active = true;

CREATE INDEX idx_shift_assignments_vehicle ON vehicle_shift_assignments(vehicle_id);

CREATE INDEX idx_shift_assignments_route ON vehicle_shift_assignments(route_id);

CREATE INDEX idx_shift_assignments_active_shift
    ON vehicle_shift_assignments(shift_type, is_active)
    WHERE is_active = true;

CREATE TRIGGER update_vehicle_shift_assignments_updated_at
    BEFORE UPDATE ON vehicle_shift_assignments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE vehicle_shift_assignments IS 'Stores scheduled route assignments for vehicles per shift. FIRST shift: 07:00-14:00, SECOND shift: 14:00-21:00';
COMMENT ON COLUMN vehicle_shift_assignments.shift_type IS 'FIRST = 07:00-14:00, SECOND = 14:00-21:00';
