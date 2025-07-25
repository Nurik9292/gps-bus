INSERT INTO bus_stop (id, name, latitude, longitude, location, created_at, updated_at, version) VALUES
                                                                                                    ('stop-001', 'Махтумкули базары', 37.9601, 58.3261, ST_SetSRID(ST_MakePoint(58.3261, 37.9601), 4326), NOW(), NOW(), 0),
                                                                                                    ('stop-002', 'Түркменбашы проспекти', 37.9455, 58.3833, ST_SetSRID(ST_MakePoint(58.3833, 37.9455), 4326), NOW(), NOW(), 0),
                                                                                                    ('stop-003', 'Нейтралитет арчы', 37.9255, 58.3931, ST_SetSRID(ST_MakePoint(58.3931, 37.9255), 4326), NOW(), NOW(), 0),
                                                                                                    ('stop-004', 'Университет', 37.9344, 58.3697, ST_SetSRID(ST_MakePoint(58.3697, 37.9344), 4326), NOW(), NOW(), 0),
                                                                                                    ('stop-005', 'Аэропорт', 37.9886, 58.3610, ST_SetSRID(ST_MakePoint(58.3610, 37.9886), 4326), NOW(), NOW(), 0)
    ON CONFLICT (id) DO NOTHING;

INSERT INTO bus_route (id, route_number, name, description, is_active, created_at, updated_at, version) VALUES
                                                                                                            ('route-001', '1', 'Махтумкули базары - Аэропорт', 'Основной маршрут до аэропорта', true, NOW(), NOW(), 0),
                                                                                                            ('route-002', '5', 'Түркменбашы - Нейтралитет', 'Центральный маршрут', true, NOW(), NOW(), 0),
                                                                                                            ('route-003', '12', 'Университет - Махтумкули', 'Студенческий маршрут', true, NOW(), NOW(), 0)
    ON CONFLICT (id) DO NOTHING;

INSERT INTO vehicle (id, plate_number, route_id, is_active, created_at, updated_at, version) VALUES
                                                                                                 ('vehicle-001', '12-53 AGH', 'route-001', true, NOW(), NOW(), 0),
                                                                                                 ('vehicle-002', '45-78 AGH', 'route-002', true, NOW(), NOW(), 0),
                                                                                                 ('vehicle-003', '23-91 AGH', 'route-003', true, NOW(), NOW(), 0),
                                                                                                 ('vehicle-004', '67-34 AGH', 'route-001', true, NOW(), NOW(), 0)
    ON CONFLICT (id) DO NOTHING;