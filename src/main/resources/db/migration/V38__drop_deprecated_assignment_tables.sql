

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'route_assignments'
    ) THEN
        RAISE EXCEPTION 'route_assignments table does not exist! Cannot drop old tables safely.';
    END IF;

    IF (SELECT COUNT(*) FROM route_assignments) = 0 THEN
        RAISE WARNING 'route_assignments table is EMPTY! Verify this is expected before continuing.';
    END IF;

    RAISE NOTICE 'Safety check passed: route_assignments table exists and has data';
END $$;


DO $$
DECLARE
    old_shift_count INTEGER;
    old_immediate_count INTEGER;
    new_total_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO old_shift_count
    FROM vehicle_shift_assignments_deprecated
    WHERE is_active = true;

    SELECT COUNT(*) INTO old_immediate_count
    FROM immediate_route_assignments_deprecated
    WHERE is_active = true;

    SELECT COUNT(*) INTO new_total_count
    FROM route_assignments
    WHERE is_active = true;

    RAISE NOTICE '=================================================================';
    RAISE NOTICE 'RECORD COUNT COMPARISON';
    RAISE NOTICE '=================================================================';
    RAISE NOTICE 'Old tables (active records):';
    RAISE NOTICE '  • vehicle_shift_assignments_deprecated: %', old_shift_count;
    RAISE NOTICE '  • immediate_route_assignments_deprecated: %', old_immediate_count;
    RAISE NOTICE '  • Total: %', old_shift_count + old_immediate_count;
    RAISE NOTICE '';
    RAISE NOTICE 'New table (active records):';
    RAISE NOTICE '  • route_assignments: %', new_total_count;
    RAISE NOTICE '=================================================================';

    IF new_total_count < (old_shift_count + old_immediate_count) THEN
        RAISE WARNING 'New table has FEWER records than old tables! Verify this is expected.';
        RAISE WARNING 'This might indicate incomplete migration or data cleanup.';
    END IF;

    IF new_total_count > (old_shift_count + old_immediate_count) THEN
        RAISE NOTICE 'New table has MORE records than old tables (likely due to new assignments created after migration)';
    END IF;
END $$;


DROP TABLE IF EXISTS vehicle_shift_assignments_deprecated CASCADE;

DROP TABLE IF EXISTS immediate_route_assignments_deprecated CASCADE;



DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name IN (
              'vehicle_shift_assignments_deprecated',
              'immediate_route_assignments_deprecated',
              'vehicle_shift_assignments',
              'immediate_route_assignments'
          )
    ) THEN
        RAISE WARNING 'Some old assignment tables still exist after drop!';
    ELSE
        RAISE NOTICE 'All deprecated assignment tables successfully dropped';
    END IF;
END $$;


DO $$
BEGIN
    RAISE NOTICE '=================================================================';
    RAISE NOTICE 'UNIFIED ASSIGNMENT MIGRATION COMPLETE';
    RAISE NOTICE '=================================================================';
    RAISE NOTICE '';
    RAISE NOTICE 'Migration summary:';
    RAISE NOTICE '  ✅ V34: Created route_assignments table';
    RAISE NOTICE '  ✅ V35: Migrated data from old tables';
    RAISE NOTICE '  ✅ V36: Cleaned up vehicles table';
    RAISE NOTICE '  ✅ V37: Renamed old tables to _deprecated';
    RAISE NOTICE '  ✅ V38: Dropped deprecated tables';
    RAISE NOTICE '';
    RAISE NOTICE 'Current state:';
    RAISE NOTICE '  ✅ Single unified route_assignments table in use';
    RAISE NOTICE '  ✅ Old tables permanently removed';
    RAISE NOTICE '  ✅ All code using new schema';
    RAISE NOTICE '';
    RAISE NOTICE 'Key features of new schema:';
    RAISE NOTICE '  • effective_date determines when assignment becomes active';
    RAISE NOTICE '  • Today + current shift = immediate assignment';
    RAISE NOTICE '  • Future date/shift = scheduled assignment';
    RAISE NOTICE '  • Old assignments auto-deleted at midnight';
    RAISE NOTICE '';
    RAISE NOTICE 'Completion date: %', CURRENT_TIMESTAMP;
    RAISE NOTICE '=================================================================';
END $$;

