ALTER TABLE route_assignments DROP CONSTRAINT chk_route_assignment_shift_type;

ALTER TABLE route_assignments ADD CONSTRAINT chk_route_assignment_shift_type
    CHECK (shift_type IN ('FIRST', 'SECOND', 'FULL_DAY'));


COMMENT ON COLUMN route_assignments.shift_type IS
    'FIRST = 05:00-14:00, SECOND = 14:00-23:00, FULL_DAY = 05:00-23:00. Combined with effective_date determines exact activation.';
