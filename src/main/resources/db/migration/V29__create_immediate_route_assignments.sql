
CREATE TABLE immediate_route_assignments (
    id VARCHAR(36) PRIMARY KEY,
    vehicle_id VARCHAR(36) NOT NULL,
    route_id VARCHAR(36) NOT NULL,
    assigned_by VARCHAR(100) NOT NULL,
    reason VARCHAR(500),
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,

    CONSTRAINT fk_immediate_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(id) ON DELETE CASCADE,
    CONSTRAINT fk_immediate_route FOREIGN KEY (route_id) REFERENCES bus_routes(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_immediate_assignment_unique
    ON immediate_route_assignments(vehicle_id)
    WHERE is_active = true;

CREATE INDEX idx_immediate_assignments_vehicle ON immediate_route_assignments(vehicle_id);
CREATE INDEX idx_immediate_assignments_route ON immediate_route_assignments(route_id);
CREATE INDEX idx_immediate_assignments_assigned_by ON immediate_route_assignments(assigned_by);
CREATE INDEX idx_immediate_assignments_active ON immediate_route_assignments(is_active) WHERE is_active = true;
CREATE INDEX idx_immediate_assignments_expires ON immediate_route_assignments(expires_at) WHERE expires_at IS NOT NULL;

CREATE TRIGGER update_immediate_route_assignments_updated_at
    BEFORE UPDATE ON immediate_route_assignments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE immediate_route_assignments IS 'Immediate manual route assignments by dispatchers (highest priority)';
COMMENT ON COLUMN immediate_route_assignments.assigned_by IS 'Username of admin who made the assignment';
COMMENT ON COLUMN immediate_route_assignments.reason IS 'Reason for manual assignment (e.g., replacement for broken bus)';
COMMENT ON COLUMN immediate_route_assignments.expires_at IS 'When this assignment expires (NULL = permanent until changed)';
