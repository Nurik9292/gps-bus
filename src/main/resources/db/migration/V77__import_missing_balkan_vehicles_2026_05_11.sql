INSERT INTO vehicles (
    id, device_id, license_plate, gps_provider, city_id,
    is_active, is_in_motion, speed_kmh, course,
    is_in_garage, route_source, route_confidence, gps_detection_enabled,
    version, created_at, updated_at
) VALUES
    (gen_random_uuid()::varchar(36), '860906042090123', '1505 BNE', 'TUGDK', 'city-004', true, false, 0.0, 0.0, false, 'UNKNOWN', 0, true, 0, NOW(), NOW()),
    (gen_random_uuid()::varchar(36), '860906043311312', '1604 BNE', 'TUGDK', 'city-004', true, false, 0.0, 0.0, false, 'UNKNOWN', 0, true, 0, NOW(), NOW()),
    (gen_random_uuid()::varchar(36), '860906044270863', '1660 BNE', 'TUGDK', 'city-004', true, false, 0.0, 0.0, false, 'UNKNOWN', 0, true, 0, NOW(), NOW()),
    (gen_random_uuid()::varchar(36), '860906044275029', '1908 BNE', 'TUGDK', 'city-004', true, false, 0.0, 0.0, false, 'UNKNOWN', 0, true, 0, NOW(), NOW()),
    (gen_random_uuid()::varchar(36), '860906044238407', '2028 BNE', 'TUGDK', 'city-004', true, false, 0.0, 0.0, false, 'UNKNOWN', 0, true, 0, NOW(), NOW()),
    (gen_random_uuid()::varchar(36), '860906044241211', '4034 BNE', 'TUGDK', 'city-004', true, false, 0.0, 0.0, false, 'UNKNOWN', 0, true, 0, NOW(), NOW()),
    (gen_random_uuid()::varchar(36), '860906043322699', '4039 BNB', 'TUGDK', 'city-004', true, false, 0.0, 0.0, false, 'UNKNOWN', 0, true, 0, NOW(), NOW()),
    (gen_random_uuid()::varchar(36), '860906044271937', '9927 BNB', 'TUGDK', 'city-004', true, false, 0.0, 0.0, false, 'UNKNOWN', 0, true, 0, NOW(), NOW())
ON CONFLICT DO NOTHING;
