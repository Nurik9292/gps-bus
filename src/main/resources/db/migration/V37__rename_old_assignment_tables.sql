
ALTER TABLE IF EXISTS vehicle_shift_assignments
    RENAME TO vehicle_shift_assignments_deprecated;

ALTER TABLE IF EXISTS immediate_route_assignments
    RENAME TO immediate_route_assignments_deprecated;

COMMENT ON TABLE vehicle_shift_assignments_deprecated IS
    '⚠️ DEPRECATED: Replaced by route_assignments table (V34). Do not use this table. Kept for verification period only. Will be dropped in V38 after production verification.';

COMMENT ON TABLE immediate_route_assignments_deprecated IS
    '⚠️ DEPRECATED: Replaced by route_assignments table (V34). Do not use this table. Kept for verification period only. Will be dropped in V38 after production verification.';


DO $$
BEGIN
    RAISE NOTICE '=================================================================';
    RAISE NOTICE 'Old assignment tables have been renamed to *_deprecated';
    RAISE NOTICE '=================================================================';
    RAISE NOTICE '';
    RAISE NOTICE 'Tables renamed:';
    RAISE NOTICE '  • vehicle_shift_assignments → vehicle_shift_assignments_deprecated';
    RAISE NOTICE '  • immediate_route_assignments → immediate_route_assignments_deprecated';
    RAISE NOTICE '';
    RAISE NOTICE 'IMPORTANT:';
    RAISE NOTICE '  ✅ All new code MUST use route_assignments table';
    RAISE NOTICE '  ❌ Any code referencing old table names will FAIL';
    RAISE NOTICE '  📊 Deprecated tables kept for verification period';
    RAISE NOTICE '  🗑️  Will be dropped in V38 after verification';
    RAISE NOTICE '';
    RAISE NOTICE 'Verification period: 1-2 weeks recommended';
    RAISE NOTICE 'Migration date: %', CURRENT_DATE;
    RAISE NOTICE '=================================================================';
END $$;

