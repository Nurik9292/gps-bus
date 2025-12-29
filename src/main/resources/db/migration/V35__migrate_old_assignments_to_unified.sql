
INSERT INTO route_assignments (
    id,
    vehicle_id,
    route_id,
    effective_date,
    shift_type,
    assigned_by,
    reason,
    expires_at,
    is_active,
    created_at,
    updated_at,
    version
)
SELECT
    vsa.id,
    vsa.vehicle_id,
    vsa.route_id,
    CURRENT_DATE AT TIME ZONE 'Asia/Ashgabat' AS effective_date,


    vsa.shift_type,

    COALESCE(vsa.assigned_by, 'system') AS assigned_by,

    NULL AS reason,

    NULL AS expires_at,


    vsa.is_active,


    vsa.created_at,
    vsa.updated_at,
    vsa.version

FROM vehicle_shift_assignments vsa
WHERE vsa.is_active = true;



DO $$
DECLARE
    migrated_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO migrated_count
    FROM route_assignments
    WHERE created_at IN (SELECT created_at FROM vehicle_shift_assignments WHERE is_active = true);

    RAISE NOTICE 'Migrated % active shift assignments to route_assignments', migrated_count;
END $$;


INSERT INTO route_assignments (
    id,
    vehicle_id,
    route_id,
    effective_date,
    shift_type,
    assigned_by,
    reason,
    expires_at,
    is_active,
    created_at,
    updated_at,
    version
)
SELECT
    ira.id,
    ira.vehicle_id,
    ira.route_id,
    CURRENT_DATE AT TIME ZONE 'Asia/Ashgabat' AS effective_date,


    CASE
        WHEN EXTRACT(HOUR FROM CURRENT_TIME AT TIME ZONE 'Asia/Ashgabat') < 7 THEN 'SECOND'
        WHEN EXTRACT(HOUR FROM CURRENT_TIME AT TIME ZONE 'Asia/Ashgabat') < 14 THEN 'FIRST'
        ELSE 'SECOND'
    END AS shift_type,

    ira.assigned_by,

    ira.reason,

    ira.expires_at,

    ira.is_active,

    ira.created_at,
    ira.updated_at,
    ira.version

FROM immediate_route_assignments ira
WHERE ira.is_active = true;

DO $$
DECLARE
    migrated_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO migrated_count
    FROM route_assignments
    WHERE created_at IN (SELECT created_at FROM immediate_route_assignments WHERE is_active = true);

    RAISE NOTICE 'Migrated % active immediate assignments to route_assignments', migrated_count;
END $$;

