ALTER TABLE vehicle_shift_assignments
    ADD COLUMN IF NOT EXISTS assigned_by VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_vehicle_shift_assignments_assigned_by
    ON vehicle_shift_assignments(assigned_by)
    WHERE assigned_by IS NOT NULL;

COMMENT ON COLUMN vehicle_shift_assignments.assigned_by IS 'Username of admin who created this assignment';
