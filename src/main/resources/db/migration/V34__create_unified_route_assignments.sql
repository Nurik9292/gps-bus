
CREATE TABLE route_assignments (

    id VARCHAR(36) PRIMARY KEY,
    vehicle_id VARCHAR(36) NOT NULL,
    route_id VARCHAR(36) NOT NULL,
    effective_date DATE NOT NULL,
    shift_type VARCHAR(20) NOT NULL,
    assigned_by VARCHAR(100) NOT NULL,
    reason VARCHAR(500),
    expires_at TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN DEFAULT true NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    CONSTRAINT fk_route_assignment_vehicle
        FOREIGN KEY (vehicle_id)
        REFERENCES vehicles(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_route_assignment_route
        FOREIGN KEY (route_id)
        REFERENCES bus_routes(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_route_assignment_shift_type
        CHECK (shift_type IN ('FIRST', 'SECOND')),

    CONSTRAINT chk_route_assignment_assigned_by_not_empty
        CHECK (assigned_by IS NOT NULL AND TRIM(assigned_by) <> ''),

    CONSTRAINT chk_route_assignment_expires_within_24h
        CHECK (
            expires_at IS NULL OR
            expires_at <= (effective_date::TIMESTAMP + INTERVAL '1 day' + INTERVAL '23 hours')
        )

);

CREATE UNIQUE INDEX idx_route_assignment_unique_vehicle_date_shift
    ON route_assignments(vehicle_id, effective_date, shift_type)
    WHERE is_active = true;

COMMENT ON INDEX idx_route_assignment_unique_vehicle_date_shift IS
    'Ensures vehicle can have only ONE active assignment per date+shift. Example: vehicle X cannot have two assignments for 2025-12-29 FIRST shift.';

CREATE INDEX idx_route_assignment_vehicle
    ON route_assignments(vehicle_id)
    WHERE is_active = true;

CREATE INDEX idx_route_assignment_route
    ON route_assignments(route_id)
    WHERE is_active = true;

CREATE INDEX idx_route_assignment_effective_date
    ON route_assignments(effective_date, shift_type)
    WHERE is_active = true;

CREATE INDEX idx_route_assignment_current
    ON route_assignments(effective_date, shift_type, is_active)
    WHERE is_active = true;

CREATE INDEX idx_route_assignment_assigned_by
    ON route_assignments(assigned_by)
    WHERE is_active = true;

CREATE INDEX idx_route_assignment_expires
    ON route_assignments(expires_at)
    WHERE expires_at IS NOT NULL AND is_active = true;


CREATE TRIGGER update_route_assignments_updated_at
    BEFORE UPDATE ON route_assignments
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();


COMMENT ON TABLE route_assignments IS
    'Unified route assignment table. Replaces vehicle_shift_assignments and immediate_route_assignments. An assignment for today''s current shift is applied immediately. An assignment for a future date/shift is applied by the scheduler. Old assignments (past dates) are deleted automatically at midnight.';

COMMENT ON COLUMN route_assignments.effective_date IS
    'Date when assignment becomes active. Combined with shift_type determines exact time. Example: 2025-12-29 + FIRST = activates at 07:00 on Dec 29';

COMMENT ON COLUMN route_assignments.shift_type IS
    'FIRST = 07:00-14:00, SECOND = 14:00-23:00. Combined with effective_date determines exact activation.';

COMMENT ON COLUMN route_assignments.assigned_by IS
    'Username of admin who created this assignment. Always required for accountability.';

COMMENT ON COLUMN route_assignments.reason IS
    'Optional reason for assignment. Useful for immediate/emergency changes. Example: "Replacing bus 123 - mechanical failure"';

COMMENT ON COLUMN route_assignments.expires_at IS
    'When assignment should auto-deactivate. NULL = lasts entire shift. NOT NULL = deactivates at specified time (must be within shift or next day). Example: temporary assignment until 16:30.';

COMMENT ON COLUMN route_assignments.is_active IS
    'false = soft-deleted, expired, or manually deactivated. Keeps audit trail.';

COMMENT ON COLUMN route_assignments.version IS
    'Optimistic locking version. Incremented on each update to prevent concurrent modification conflicts.';
