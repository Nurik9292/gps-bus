
ALTER TABLE vehicles
    DROP COLUMN IF EXISTS assigned_by;

ALTER TABLE vehicles
    DROP COLUMN IF EXISTS manual_assignment_reason;


DROP INDEX IF EXISTS idx_vehicles_assigned_by;


DO $$
BEGIN
    RAISE NOTICE 'Successfully removed duplicate columns (assigned_by, manual_assignment_reason) from vehicles table';
    RAISE NOTICE 'Assignment metadata now stored exclusively in route_assignments table';
END $$;
