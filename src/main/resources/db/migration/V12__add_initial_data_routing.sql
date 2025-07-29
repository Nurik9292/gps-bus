-- 🚌 СОЗДАНИЕ ГЕОМЕТРИИ МАРШРУТОВ И ОСТАНОВОК АШХАБАДА

-- ===== 1. СОЗДАНИЕ ОСТАНОВОК АШХАБАДА =====

INSERT INTO bus_stops (id, stop_name, stop_code, latitude, longitude, is_active, city_id) VALUES

-- Маршрут 29: Автовокзал - Арчабил
('stop-arch-neutrality', 'Арка Нейтралитета', 'AN001', 37.9601, 58.3261, true, 'city-001'),
('stop-independence-square', 'Площадь Независимости', 'IS002', 37.9550, 58.3300, true, 'city-001'),
('stop-mahtumkuli-avenue', 'Проспект Махтумкули', 'MA003', 37.9500, 58.3350, true,  'city-001'),
('stop-central-market', 'Центральный рынок', 'CM004', 37.9454, 58.3833, true, 'city-001'),
('stop-tolkuchka', 'Толкучка', 'TK005', 37.9200, 58.4100, true, 'city-001'),

-- Маршрут 12: Центр - Беркарарлык
('stop-berkararlyk', 'Беркарарлык', 'BK006', 37.8900, 58.4200, true,  'city-001'),
('stop-kopetdag-plaza', 'Копетдаг Плаза', 'KP007', 37.9300, 58.3700, true, 'city-001'),
('stop-russian-bazar', 'Русский базар', 'RB008', 37.9400, 58.3600, true, 'city-001' ),

-- Маршрут 7A: Кольцевой
('stop-hippodrome', 'Гипподром', 'HP009', 37.9123, 58.3456, true, 'city-001'),
('stop-sports-complex', 'Спорткомплекс', 'SC010', 37.9000, 58.3500, true,  'city-001'),
('stop-ministry-district', 'Район министерств', 'MD011', 37.9350, 58.3200, true,  'city-001'),

-- Маршрут 15: Азади - Бабатаг
('stop-azadi', 'Азади', 'AZ012', 37.8800, 58.3000, true, 'city-001'),
('stop-babatag', 'Бабатаг', 'BT013', 37.8500, 58.2800, true, 'city-001'),
('stop-airport-road', 'Дорога в аэропорт', 'AR014', 37.9735, 58.3607,  true, 'city-001');

-- ===== 2. СОЗДАНИЕ ГЕОМЕТРИИ МАРШРУТОВ =====

-- Маршрут 29: Автовокзал - Арчабил (прямое направление)
UPDATE bus_routes
SET
    geometry_forward = ST_GeomFromText(
            'LINESTRING(58.3261 37.9601, 58.3300 37.9550, 58.3350 37.9500, 58.3833 37.9454, 58.4100 37.9200)',
            4326
                       ),
    geometry_backward = ST_GeomFromText(
            'LINESTRING(58.4100 37.9200, 58.3833 37.9454, 58.3350 37.9500, 58.3300 37.9550, 58.3261 37.9601)',
            4326
                        ),
    route_geometry_forward = 'LINESTRING(58.3261 37.9601, 58.3300 37.9550, 58.3350 37.9500, 58.3833 37.9454, 58.4100 37.9200)',
    route_geometry_backward = 'LINESTRING(58.4100 37.9200, 58.3833 37.9454, 58.3350 37.9500, 58.3300 37.9550, 58.3261 37.9601)',
    total_distance_forward_meters = 8500,
    total_distance_backward_meters = 8500,
    estimated_duration_minutes = 25
WHERE route_number = '29';

-- Маршрут 12: Центр - Беркарарлык
UPDATE bus_routes
SET
    geometry_forward = ST_GeomFromText(
            'LINESTRING(58.3833 37.9454, 58.3700 37.9300, 58.3600 37.9400, 58.4200 37.8900)',
            4326
                       ),
    geometry_backward = ST_GeomFromText(
            'LINESTRING(58.4200 37.8900, 58.3600 37.9400, 58.3700 37.9300, 58.3833 37.9454)',
            4326
                        ),
    route_geometry_forward = 'LINESTRING(58.3833 37.9454, 58.3700 37.9300, 58.3600 37.9400, 58.4200 37.8900)',
    route_geometry_backward = 'LINESTRING(58.4200 37.8900, 58.3600 37.9400, 58.3700 37.9300, 58.3833 37.9454)',
    total_distance_forward_meters = 6200,
    total_distance_backward_meters = 6200,
    estimated_duration_minutes = 18
WHERE route_number = '12';

-- Маршрут 7A: Кольцевой
UPDATE bus_routes
SET
    geometry_forward = ST_GeomFromText(
            'LINESTRING(58.3456 37.9123, 58.3500 37.9000, 58.3200 37.9350, 58.3456 37.9123)',
            4326
                       ),
    geometry_backward = ST_GeomFromText(
            'LINESTRING(58.3456 37.9123, 58.3200 37.9350, 58.3500 37.9000, 58.3456 37.9123)',
            4326
                        ),
    route_geometry_forward = 'LINESTRING(58.3456 37.9123, 58.3500 37.9000, 58.3200 37.9350, 58.3456 37.9123)',
    route_geometry_backward = 'LINESTRING(58.3456 37.9123, 58.3200 37.9350, 58.3500 37.9000, 58.3456 37.9123)',
    total_distance_forward_meters = 4800,
    total_distance_backward_meters = 4800,
    estimated_duration_minutes = 15
WHERE route_number = '7A';

-- Маршрут 15: Азади - Бабатаг
UPDATE bus_routes
SET
    geometry_forward = ST_GeomFromText(
            'LINESTRING(58.3000 37.8800, 58.2800 37.8500, 58.3607 37.9735)',
            4326
                       ),
    geometry_backward = ST_GeomFromText(
            'LINESTRING(58.3607 37.9735, 58.2800 37.8500, 58.3000 37.8800)',
            4326
                        ),
    route_geometry_forward = 'LINESTRING(58.3000 37.8800, 58.2800 37.8500, 58.3607 37.9735)',
    route_geometry_backward = 'LINESTRING(58.3607 37.9735, 58.2800 37.8500, 58.3000 37.8800)',
    total_distance_forward_meters = 12000,
    total_distance_backward_meters = 12000,
    estimated_duration_minutes = 35
WHERE route_number = '15';

-- ===== 3. СВЯЗЬ ОСТАНОВОК С МАРШРУТАМИ =====

-- Маршрут 29 остановки (прямое направление)
INSERT INTO route_stops (id, route_id, stop_id, direction, stop_sequence, estimated_travel_time_minutes, distance_from_start_meters) VALUES
                                                                                                                                         (gen_random_uuid()::text, (SELECT id FROM bus_routes WHERE route_number = '29'), 'stop-arch-neutrality', 0, 1, 0, 0),
                                                                                                                                         (gen_random_uuid()::text, (SELECT id FROM bus_routes WHERE route_number = '29'), 'stop-independence-square', 0, 2, 3, 800),
                                                                                                                                         (gen_random_uuid()::text, (SELECT id FROM bus_routes WHERE route_number = '29'), 'stop-mahtumkuli-avenue', 0, 3, 6, 1600),
                                                                                                                                         (gen_random_uuid()::text, (SELECT id FROM bus_routes WHERE route_number = '29'), 'stop-central-market', 0, 4, 15, 4200),
                                                                                                                                         (gen_random_uuid()::text, (SELECT id FROM bus_routes WHERE route_number = '29'), 'stop-tolkuchka', 0, 5, 25, 8500);

-- Маршрут 29 остановки (обратное направление)
INSERT INTO route_stops (id, route_id, stop_id, direction, stop_sequence, estimated_travel_time_minutes, distance_from_start_meters) VALUES
                                                                                                                                         (gen_random_uuid()::text, (SELECT id FROM bus_routes WHERE route_number = '29'), 'stop-tolkuchka', 1, 1, 0, 0),
                                                                                                                                         (gen_random_uuid()::text, (SELECT id FROM bus_routes WHERE route_number = '29'), 'stop-central-market', 1, 2, 10, 4300),
                                                                                                                                         (gen_random_uuid()::text, (SELECT id FROM bus_routes WHERE route_number = '29'), 'stop-mahtumkuli-avenue', 1, 3, 19, 6900),
                                                                                                                                         (gen_random_uuid()::text, (SELECT id FROM bus_routes WHERE route_number = '29'), 'stop-independence-square', 1, 4, 22, 7700),
                                                                                                                                         (gen_random_uuid()::text, (SELECT id FROM bus_routes WHERE route_number = '29'), 'stop-arch-neutrality', 1, 5, 25, 8500);

-- Маршрут 12 остановки
INSERT INTO route_stops (id, route_id, stop_id, direction, stop_sequence, estimated_travel_time_minutes, distance_from_start_meters) VALUES
                                                                                                                                         (gen_random_uuid()::text, (SELECT id FROM bus_routes WHERE route_number = '12'), 'stop-central-market', 0, 1, 0, 0),
                                                                                                                                         (gen_random_uuid()::text, (SELECT id FROM bus_routes WHERE route_number = '12'), 'stop-kopetdag-plaza', 0, 2, 8, 2100),
                                                                                                                                         (gen_random_uuid()::text, (SELECT id FROM bus_routes WHERE route_number = '12'), 'stop-russian-bazar', 0, 3, 12, 3400),
                                                                                                                                         (gen_random_uuid()::text, (SELECT id FROM bus_routes WHERE route_number = '12'), 'stop-berkararlyk', 0, 4, 18, 6200);

-- Маршрут 7A остановки (кольцевой)
INSERT INTO route_stops (id, route_id, stop_id, direction, stop_sequence, estimated_travel_time_minutes, distance_from_start_meters) VALUES
                                                                                                                                         (gen_random_uuid()::text, (SELECT id FROM bus_routes WHERE route_number = '7A'), 'stop-hippodrome', 0, 1, 0, 0),
                                                                                                                                         (gen_random_uuid()::text, (SELECT id FROM bus_routes WHERE route_number = '7A'), 'stop-sports-complex', 0, 2, 5, 1600),
                                                                                                                                         (gen_random_uuid()::text, (SELECT id FROM bus_routes WHERE route_number = '7A'), 'stop-ministry-district', 0, 3, 10, 3200),
                                                                                                                                         (gen_random_uuid()::text, (SELECT id FROM bus_routes WHERE route_number = '7A'), 'stop-hippodrome', 0, 4, 15, 4800);

-- Маршрут 15 остановки
INSERT INTO route_stops (id, route_id, stop_id, direction, stop_sequence, estimated_travel_time_minutes, distance_from_start_meters) VALUES
                                                                                                                                         (gen_random_uuid()::text, (SELECT id FROM bus_routes WHERE route_number = '15'), 'stop-azadi', 0, 1, 0, 0),
                                                                                                                                         (gen_random_uuid()::text, (SELECT id FROM bus_routes WHERE route_number = '15'), 'stop-babatag', 0, 2, 20, 6000),
                                                                                                                                         (gen_random_uuid()::text, (SELECT id FROM bus_routes WHERE route_number = '15'), 'stop-airport-road', 0, 3, 35, 12000);


