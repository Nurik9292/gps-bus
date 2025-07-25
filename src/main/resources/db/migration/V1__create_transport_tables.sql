CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE vehicles (
                          id VARCHAR(36) PRIMARY KEY,
                          device_id VARCHAR(100) NOT NULL UNIQUE,
                          license_plate VARCHAR(20) NOT NULL UNIQUE,
                          current_latitude DOUBLE PRECISION,
                          current_longitude DOUBLE PRECISION,
                          speed_kmh DOUBLE PRECISION DEFAULT 0.0,
                          is_in_motion BOOLEAN DEFAULT false,
                          last_position_update TIMESTAMP WITH TIME ZONE,
                          assigned_route_id VARCHAR(36),
                          is_active BOOLEAN DEFAULT true,
                          created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          version BIGINT DEFAULT 0
);

CREATE INDEX idx_vehicles_device_id ON vehicles(device_id);
CREATE INDEX idx_vehicles_license_plate ON vehicles(license_plate);
CREATE INDEX idx_vehicles_assigned_route ON vehicles(assigned_route_id);
CREATE INDEX idx_vehicles_active ON vehicles(is_active);
CREATE INDEX idx_vehicles_in_motion ON vehicles(is_in_motion, is_active);
CREATE INDEX idx_vehicles_last_update ON vehicles(last_position_update);

CREATE INDEX idx_vehicles_location ON vehicles USING GIST (
    ST_Point(current_longitude, current_latitude)
    ) WHERE current_latitude IS NOT NULL AND current_longitude IS NOT NULL AND is_active = true;

CREATE INDEX idx_vehicles_active_recent ON vehicles(is_active, last_position_update)
    WHERE is_active = true;

ALTER TABLE vehicles ADD CONSTRAINT chk_vehicles_coordinates
    CHECK (
        (current_latitude IS NULL AND current_longitude IS NULL) OR
        (current_latitude IS NOT NULL AND current_longitude IS NOT NULL AND
         current_latitude BETWEEN 35.0 AND 43.0 AND
         current_longitude BETWEEN 52.0 AND 67.0)
        );

ALTER TABLE vehicles ADD CONSTRAINT chk_vehicles_speed
    CHECK (speed_kmh >= 0 AND speed_kmh <= 200);

ALTER TABLE vehicles ADD CONSTRAINT chk_vehicles_license_plate_format
    CHECK (license_plate ~ '^\d{4}\s[A-Z]{3}$');




CREATE TABLE bus_routes (
                            id VARCHAR(36) PRIMARY KEY,
                            route_number VARCHAR(10) NOT NULL UNIQUE,
                            route_name VARCHAR(200) NOT NULL,
                            route_name_tm VARCHAR(200), -- Название на туркменском
                            route_color VARCHAR(7) DEFAULT '#1976D2', -- HEX цвет маршрута
                            is_active BOOLEAN DEFAULT true,
                            fare_price DECIMAL(8,2) DEFAULT 1.00, -- Стоимость проезда в манатах
                            estimated_duration_minutes INTEGER, -- Примерное время полного маршрута
                            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            version BIGINT DEFAULT 0
);

-- Индексы для bus_routes
CREATE INDEX idx_bus_routes_number ON bus_routes(route_number);
CREATE INDEX idx_bus_routes_active ON bus_routes(is_active);

ALTER TABLE bus_routes ADD CONSTRAINT chk_bus_routes_number_format
    CHECK (route_number ~ '^\d{1,3}[A-Z]?$'); -- Форматы: "73", "12A"

ALTER TABLE bus_routes ADD CONSTRAINT chk_bus_routes_color_format
    CHECK (route_color ~ '^#[0-9A-F]{6}$'); -- HEX цвет

ALTER TABLE bus_routes ADD CONSTRAINT chk_bus_routes_fare_positive
    CHECK (fare_price > 0);


-- Таблица остановок (BusStop Aggregate)
CREATE TABLE bus_stops (
                           id VARCHAR(36) PRIMARY KEY,
                           stop_code VARCHAR(20) UNIQUE, -- Код остановки для пассажиров
                           stop_name VARCHAR(200) NOT NULL,
                           stop_name_tm VARCHAR(200), -- Название на туркменском
                           latitude DOUBLE PRECISION NOT NULL,
                           longitude DOUBLE PRECISION NOT NULL,
                           is_major_stop BOOLEAN DEFAULT false, -- Крупная остановка (транспортный узел)
                           has_shelter BOOLEAN DEFAULT false, -- Есть ли навес
                           is_accessible BOOLEAN DEFAULT false, -- Доступность для инвалидов
                           is_active BOOLEAN DEFAULT true,
                           created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           version BIGINT DEFAULT 0
);

-- Индексы для bus_stops
CREATE INDEX idx_bus_stops_code ON bus_stops(stop_code);
CREATE INDEX idx_bus_stops_name ON bus_stops(stop_name);
CREATE INDEX idx_bus_stops_active ON bus_stops(is_active);
CREATE INDEX idx_bus_stops_major ON bus_stops(is_major_stop, is_active);

-- Геопространственный индекс для остановок
CREATE INDEX idx_bus_stops_location ON bus_stops USING GIST (
    ST_Point(longitude, latitude)
    ) WHERE is_active = true;

-- Валидационные ограничения для остановок
ALTER TABLE bus_stops ADD CONSTRAINT chk_bus_stops_coordinates
    CHECK (
        latitude BETWEEN 35.0 AND 43.0 AND
        longitude BETWEEN 52.0 AND 67.0
        );


-- Таблица связи маршрутов и остановок (многие ко многим)
CREATE TABLE route_stops (
                             id VARCHAR(36) PRIMARY KEY,
                             route_id VARCHAR(36) NOT NULL,
                             stop_id VARCHAR(36) NOT NULL,
                             stop_sequence INTEGER NOT NULL, -- Порядок остановки в маршруте
                             direction INTEGER NOT NULL DEFAULT 0, -- 0 = прямое направление, 1 = обратное
                             estimated_travel_time_minutes INTEGER, -- Время до следующей остановки
                             distance_from_start_meters INTEGER, -- Расстояние от начала маршрута до этой остановки
                             created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

                             CONSTRAINT fk_route_stops_route FOREIGN KEY (route_id) REFERENCES bus_routes(id) ON DELETE CASCADE,
                             CONSTRAINT fk_route_stops_stop FOREIGN KEY (stop_id) REFERENCES bus_stops(id) ON DELETE CASCADE,
                             CONSTRAINT uk_route_stops_sequence UNIQUE (route_id, direction, stop_sequence),
                             CONSTRAINT uk_route_stops_stop_direction UNIQUE (route_id, stop_id, direction)
);

-- Индексы для route_stops
CREATE INDEX idx_route_stops_route ON route_stops(route_id, direction, stop_sequence);
CREATE INDEX idx_route_stops_stop ON route_stops(stop_id);

-- Валидационные ограничения
ALTER TABLE route_stops ADD CONSTRAINT chk_route_stops_direction
    CHECK (direction IN (0, 1));

ALTER TABLE route_stops ADD CONSTRAINT chk_route_stops_sequence_positive
    CHECK (stop_sequence > 0);

-- Добавляем внешний ключ для vehicles.assigned_route_id
ALTER TABLE vehicles ADD CONSTRAINT fk_vehicles_assigned_route
    FOREIGN KEY (assigned_route_id) REFERENCES bus_routes(id) ON DELETE SET NULL;

-- Таблица расписания (для будущего использования)
CREATE TABLE route_schedules (
                                 id VARCHAR(36) PRIMARY KEY,
                                 route_id VARCHAR(36) NOT NULL,
                                 direction INTEGER NOT NULL DEFAULT 0,
                                 departure_time TIME NOT NULL,
                                 days_of_week INTEGER[] NOT NULL DEFAULT '{1,2,3,4,5,6,7}', -- 1=Monday, 7=Sunday
                                 is_active BOOLEAN DEFAULT true,
                                 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                 CONSTRAINT fk_route_schedules_route FOREIGN KEY (route_id) REFERENCES bus_routes(id) ON DELETE CASCADE
);

-- Индексы для расписания
CREATE INDEX idx_route_schedules_route ON route_schedules(route_id, direction);
CREATE INDEX idx_route_schedules_time ON route_schedules(departure_time);
CREATE INDEX idx_route_schedules_active ON route_schedules(is_active);

-- Функция для автоматического обновления updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$ language 'plpgsql';

-- Триггеры для автоматического обновления updated_at
CREATE TRIGGER update_vehicles_updated_at
    BEFORE UPDATE ON vehicles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bus_routes_updated_at
    BEFORE UPDATE ON bus_routes
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bus_stops_updated_at
    BEFORE UPDATE ON bus_stops
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Функция для поиска остановок в радиусе (для Use Cases)
CREATE OR REPLACE FUNCTION find_stops_within_radius(
    center_lat DOUBLE PRECISION,
    center_lon DOUBLE PRECISION,
    radius_meters INTEGER DEFAULT 800
)
RETURNS TABLE (
    stop_id VARCHAR(36),
    stop_name VARCHAR(200),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    distance_meters DOUBLE PRECISION
) AS $
BEGIN
RETURN QUERY
SELECT
    bs.id,
    bs.stop_name,
    bs.latitude,
    bs.longitude,
    ST_Distance(
            ST_GeogFromText('POINT(' || center_lon || ' ' || center_lat || ')'),
            ST_GeogFromText('POINT(' || bs.longitude || ' ' || bs.latitude || ')')
    ) as distance_meters
FROM bus_stops bs
WHERE bs.is_active = true
  AND ST_DWithin(
        ST_GeogFromText('POINT(' || center_lon || ' ' || center_lat || ')'),
        ST_GeogFromText('POINT(' || bs.longitude || ' ' || bs.latitude || ')'),
        radius_meters
      )
ORDER BY distance_meters;
END;
$ LANGUAGE plpgsql;

-- Функция для поиска автобусов в радиусе
CREATE OR REPLACE FUNCTION find_vehicles_within_radius(
    center_lat DOUBLE PRECISION,
    center_lon DOUBLE PRECISION,
    radius_meters INTEGER DEFAULT 1000
)
RETURNS TABLE (
    vehicle_id VARCHAR(36),
    license_plate VARCHAR(20),
    current_latitude DOUBLE PRECISION,
    current_longitude DOUBLE PRECISION,
    speed_kmh DOUBLE PRECISION,
    is_in_motion BOOLEAN,
    assigned_route_id VARCHAR(36),
    distance_meters DOUBLE PRECISION
) AS $
BEGIN
RETURN QUERY
SELECT
    v.id,
    v.license_plate,
    v.current_latitude,
    v.current_longitude,
    v.speed_kmh,
    v.is_in_motion,
    v.assigned_route_id,
    ST_Distance(
            ST_GeogFromText('POINT(' || center_lon || ' ' || center_lat || ')'),
            ST_GeogFromText('POINT(' || v.current_longitude || ' ' || v.current_latitude || ')')
    ) as distance_meters
FROM vehicles v
WHERE v.is_active = true
  AND v.current_latitude IS NOT NULL
  AND v.current_longitude IS NOT NULL
  AND v.last_position_update > (CURRENT_TIMESTAMP - INTERVAL '5 minutes')
  AND ST_DWithin(
        ST_GeogFromText('POINT(' || center_lon || ' ' || center_lat || ')'),
        ST_GeogFromText('POINT(' || v.current_longitude || ' ' || v.current_latitude || ')'),
        radius_meters
      )
ORDER BY distance_meters;
END;
$ LANGUAGE plpgsql;

-- НОВАЯ ФУНКЦИЯ: Поиск маршрутов, проходящих через определенную область
CREATE OR REPLACE FUNCTION find_routes_intersecting_area(
    center_lat DOUBLE PRECISION,
    center_lon DOUBLE PRECISION,
    radius_meters INTEGER DEFAULT 1000
)
RETURNS TABLE (
    route_id VARCHAR(36),
    route_number VARCHAR(10),
    route_name VARCHAR(200),
    route_color VARCHAR(7),
    direction INTEGER,
    intersection_point GEOMETRY,
    distance_to_center DOUBLE PRECISION
) AS $
BEGIN
RETURN QUERY
    WITH search_circle AS (
        SELECT ST_Buffer(
            ST_GeogFromText('POINT(' || center_lon || ' ' || center_lat || ')')::geography,
            radius_meters
        )::geometry as geom
    )
SELECT
    br.id,
    br.route_number,
    br.route_name,
    br.route_color,
    0 as direction,
    ST_ClosestPoint(br.geometry_forward, ST_Point(center_lon, center_lat)) as intersection_point,
    ST_Distance(
            ST_GeogFromText('POINT(' || center_lon || ' ' || center_lat || ')'),
            ST_ClosestPoint(br.geometry_forward, ST_Point(center_lon, center_lat))::geography
    ) as distance_to_center
FROM bus_routes br, search_circle sc
WHERE br.is_active = true
  AND br.geometry_forward IS NOT NULL
  AND ST_Intersects(br.geometry_forward, sc.geom)

UNION ALL

SELECT
    br.id,
    br.route_number,
    br.route_name,
    br.route_color,
    1 as direction,
    ST_ClosestPoint(br.geometry_backward, ST_Point(center_lon, center_lat)) as intersection_point,
    ST_Distance(
            ST_GeogFromText('POINT(' || center_lon || ' ' || center_lat || ')'),
            ST_ClosestPoint(br.geometry_backward, ST_Point(center_lon, center_lat))::geography
    ) as distance_to_center
FROM bus_routes br, search_circle sc
WHERE br.is_active = true
  AND br.geometry_backward IS NOT NULL
  AND ST_Intersects(br.geometry_backward, sc.geom)

ORDER BY distance_to_center;
END;
$ LANGUAGE plpgsql;

-- Функция для получения остановок маршрута по порядку
CREATE OR REPLACE FUNCTION get_route_stops_ordered(
    p_route_id VARCHAR(36),
    p_direction INTEGER DEFAULT 0
)
RETURNS TABLE (
    stop_id VARCHAR(36),
    stop_name VARCHAR(200),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    stop_sequence INTEGER,
    estimated_travel_time_minutes INTEGER,
    distance_from_start_meters INTEGER
) AS $
BEGIN
RETURN QUERY
SELECT
    bs.id,
    bs.stop_name,
    bs.latitude,
    bs.longitude,
    rs.stop_sequence,
    rs.estimated_travel_time_minutes,
    rs.distance_from_start_meters
FROM route_stops rs
         JOIN bus_stops bs ON rs.stop_id = bs.id
WHERE rs.route_id = p_route_id
  AND rs.direction = p_direction
  AND bs.is_active = true
ORDER BY rs.stop_sequence;
END;
$ LANGUAGE plpgsql;


  -- НОВАЯ ФУНКЦИЯ: Получение полной информации о маршруте с геометрией
CREATE OR REPLACE FUNCTION get_route_with_geometry(
    p_route_number VARCHAR(10)
)
RETURNS TABLE (
    route_id VARCHAR(36),
    route_number VARCHAR(10),
    route_name VARCHAR(200),
    route_color VARCHAR(7),
    geometry_forward_geojson TEXT,
    geometry_backward_geojson TEXT,
    total_distance_forward_km DECIMAL,
    total_distance_backward_km DECIMAL,
    active_vehicles_count BIGINT
) AS $
BEGIN
RETURN QUERY
SELECT
    br.id,
    br.route_number,
    br.route_name,
    br.route_color,
    br.route_geometry_forward,
    br.route_geometry_backward,
    ROUND(br.total_distance_forward_meters::DECIMAL / 1000, 2) as total_distance_forward_km,
    ROUND(br.total_distance_backward_meters::DECIMAL / 1000, 2) as total_distance_backward_km,
    COUNT(v.id) FILTER (WHERE v.is_active = true) as active_vehicles_count
FROM bus_routes br
         LEFT JOIN vehicles v ON br.id = v.assigned_route_id
WHERE br.route_number = p_route_number
  AND br.is_active = true
GROUP BY br.id, br.route_number, br.route_name, br.route_color,
         br.route_geometry_forward, br.route_geometry_backward,
         br.total_distance_forward_meters, br.total_distance_backward_meters;
END;
$ LANGUAGE plpgsql;

-- Вставка начальных данных для тестирования

-- Автобусные маршруты Ашхабада с реальной геометрией
INSERT INTO bus_routes (id, route_number, route_name, route_name_tm, route_color, fare_price, estimated_duration_minutes) VALUES
                                                                                                                              ('route-001', '1', 'Центральный рынок - Гипподром', 'Merkezi bazar - Ýaryş meýdany', '#E53935', 1.00, 45),
                                                                                                                              ('route-002', '7', 'Аэропорт - Центр города', 'Howa menzili - Şäher merkezi', '#1976D2', 2.00, 60),
                                                                                                                              ('route-003', '12', 'Махтумкули - Университет', 'Magtymguly - Uniwersitet', '#388E3C', 1.00, 35),
                                                                                                                              ('route-004', '25', 'Жилой массив - Арчабиль', 'Ýaşaýyş toplumy - Arçabil', '#F57F17', 1.50, 50),
                                                                                                                              ('route-005', '29', 'Толкучка - Серхетабат', 'Tolkuçka - Serhetabat', '#9C27B0', 1.00, 55);

-- Автобусные остановки Ашхабада (реальные координаты)
INSERT INTO bus_stops (id, stop_code, stop_name, stop_name_tm, latitude, longitude, is_major_stop, has_shelter) VALUES
                                                                                                                    ('stop-001', 'ASH001', 'Центральный рынок', 'Merkezi bazar', 37.9601, 58.3261, true, true),
                                                                                                                    ('stop-002', 'ASH002', 'Площадь Независимости', 'Garaşsyzlyk meýdany', 37.9255, 58.3836, true, true),
                                                                                                                    ('stop-003', 'ASH003', 'Гипподром', 'Ýaryş meýdany', 37.8987, 58.3951, true, true),
                                                                                                                    ('stop-004', 'ASH004', 'Аэропорт имени Огузхана', 'Oguzhan adyndaky howa menzili', 37.9868, 58.3609, true, true),
                                                                                                                    ('stop-005', 'ASH005', 'Университет Туркменистана', 'Türkmenistanyň uniwersiteti', 37.9089, 58.3831, true, true),
                                                                                                                    ('stop-006', 'ASH006', 'Арчабиль', 'Arçabil', 37.8756, 58.4123, true, true),
                                                                                                                    ('stop-007', 'ASH007', 'Махтумкули проспект', 'Magtymguly şaýoly', 37.9178, 58.3662, false, true),
                                                                                                                    ('stop-008', 'ASH008', 'Жилой массив Бериев', 'Beriýew ýaşaýyş toplumy', 37.8912, 58.4287, false, false),
                                                                                                                    ('stop-009', 'ASH009', 'Нейтралитет арка', 'Bitaraplyk arkasy', 37.9081, 58.3897, true, true),
                                                                                                                    ('stop-010', 'ASH010', 'Театр имени Молланепеса', 'Mollanepes adyndaky teatr', 37.9156, 58.3945, false, true),
                                                                                                                    ('stop-011', 'ASH011', 'Толкучка', 'Tolkuçka', 37.9345, 58.3012, true, true),
                                                                                                                    ('stop-012', 'ASH012', 'Серхетабат район', 'Serhetabat etraby', 37.8645, 58.4567, true, true);

-- Пример геометрии маршрута 29: Толкучка - Серхетабат
-- Прямое направление (упрощенная геометрия)
UPDATE bus_routes SET
                      route_geometry_forward = '{"type":"LineString","coordinates":[[58.3012,37.9345],[58.3156,37.9289],[58.3278,37.9234],[58.3401,37.9178],[58.3512,37.9123],[58.3634,37.9067],[58.3756,37.9012],[58.3889,37.8956],[58.4012,37.8901],[58.4134,37.8845],[58.4256,37.8789],[58.4378,37.8734],[58.4456,37.8678],[58.4567,37.8645]]}',
                      total_distance_forward_meters = 8750,
                      route_geometry_backward = '{"type":"LineString","coordinates":[[58.4567,37.8645],[58.4456,37.8678],[58.4378,37.8734],[58.4256,37.8789],[58.4134,37.8845],[58.4012,37.8901],[58.3889,37.8956],[58.3756,37.9012],[58.3634,37.9067],[58.3512,37.9123],[58.3401,37.9178],[58.3278,37.9234],[58.3156,37.9289],[58.3012,37.9345]]}',
                      total_distance_backward_meters = 8750
WHERE route_number = '29';

-- Пример геометрии маршрута 1: Центральный рынок - Гипподром
UPDATE bus_routes SET
                      route_geometry_forward = '{"type":"LineString","coordinates":[[58.3261,37.9601],[58.3367,37.9534],[58.3473,37.9467],[58.3579,37.9401],[58.3662,37.9178],[58.3745,37.9089],[58.3828,37.9034],[58.3836,37.9255],[58.3897,37.9081],[58.3951,37.8987]]}',
                      total_distance_forward_meters = 6200,
                      route_geometry_backward = '{"type":"LineString","coordinates":[[58.3951,37.8987],[58.3897,37.9081],[58.3836,37.9255],[58.3828,37.9034],[58.3745,37.9089],[58.3662,37.9178],[58.3579,37.9401],[58.3473,37.9467],[58.3367,37.9534],[58.3261,37.9601]]}',
                      total_distance_backward_meters = 6200
WHERE route_number = '1';

-- Связь маршрутов с остановками
-- Маршрут 29: Толкучка - Серхетабат
INSERT INTO route_stops (id, route_id, stop_id, stop_sequence, direction, estimated_travel_time_minutes, distance_from_start_meters) VALUES
                                                                                                                                         ('rs-029-001', 'route-005', 'stop-011', 1, 0, 15, 0),      -- Толкучка (начало)
                                                                                                                                         ('rs-029-002', 'route-005', 'stop-001', 2, 0, 12, 2500),   -- Центральный рынок
                                                                                                                                         ('rs-029-003', 'route-005', 'stop-007', 3, 0, 18, 4200),   -- Махтумкули проспект
                                                                                                                                         ('rs-029-004', 'route-005', 'stop-006', 4, 0, 10, 6800),   -- Арчабиль
                                                                                                                                         ('rs-029-005', 'route-005', 'stop-012', 5, 0, 0, 8750),    -- Серхетабат (конечная)

-- Обратное направление маршрута 29
                                                                                                                                         ('rs-029-006', 'route-005', 'stop-012', 1, 1, 10, 0),      -- Серхетабат (начало)
                                                                                                                                         ('rs-029-007', 'route-005', 'stop-006', 2, 1, 18, 1950),   -- Арчабиль
                                                                                                                                         ('rs-029-008', 'route-005', 'stop-007', 3, 1, 12, 4550),   -- Махтумкули проспект
                                                                                                                                         ('rs-029-009', 'route-005', 'stop-001', 4, 1, 15, 6250),   -- Центральный рынок
                                                                                                                                         ('rs-029-010', 'route-005', 'stop-011', 5, 1, 0, 8750),    -- Толкучка (конечная)

-- Маршрут 1: Центральный рынок - Гипподром
                                                                                                                                         ('rs-001', 'route-001', 'stop-001', 1, 0, 8, 0),      -- Центральный рынок
                                                                                                                                         ('rs-002', 'route-001', 'stop-007', 2, 0, 12, 1800),  -- Махтумкули проспект
                                                                                                                                         ('rs-003', 'route-001', 'stop-002', 3, 0, 10, 3200),  -- Площадь Независимости
                                                                                                                                         ('rs-004', 'route-001', 'stop-009', 4, 0, 15, 4600),  -- Нейтралитет арка
                                                                                                                                         ('rs-005', 'route-001', 'stop-003', 5, 0, 0, 6200),   -- Гипподром (конечная)

-- Обратное направление маршрута 1
                                                                                                                                         ('rs-006', 'route-001', 'stop-003', 1, 1, 15, 0),     -- Гипподром
                                                                                                                                         ('rs-007', 'route-001', 'stop-009', 2, 1, 10, 1600),  -- Нейтралитет арка
                                                                                                                                         ('rs-008', 'route-001', 'stop-002', 3, 1, 12, 3000),  -- Площадь Независимости
                                                                                                                                         ('rs-009', 'route-001', 'stop-007', 4, 1, 8, 4400),   -- Махтумкули проспект
                                                                                                                                         ('rs-010', 'route-001', 'stop-001', 5, 1, 0, 6200);   -- Центральный рынок (конечная)

-- Тестовые автобусы с туркменскими номерами
INSERT INTO vehicles (id, device_id, license_plate, current_latitude, current_longitude, speed_kmh, is_in_motion, last_position_update, assigned_route_id, is_active) VALUES
                                                                                                                                                                          ('vehicle-001', 'GPS_DEVICE_1001', '1234 ABD', 37.9601, 58.3261, 0.0, false, CURRENT_TIMESTAMP - INTERVAL '2 minutes', 'route-001', true),
                                                                                                                                                                          ('vehicle-002', 'GPS_DEVICE_1002', '5678 AGH', 37.9255, 58.3836, 25.5, true, CURRENT_TIMESTAMP - INTERVAL '30 seconds', 'route-001', true),
                                                                                                                                                                          ('vehicle-003', 'GPS_DEVICE_1003', '9012 ATM', 37.9868, 58.3609, 0.0, false, CURRENT_TIMESTAMP - INTERVAL '1 minute', 'route-002', true),
                                                                                                                                                                          ('vehicle-004', 'GPS_DEVICE_1004', '3456 AWM', 37.9089, 58.3831, 18.2, true, CURRENT_TIMESTAMP - INTERVAL '45 seconds', 'route-003', true),
                                                                                                                                                                          ('vehicle-005', 'GPS_DEVICE_1005', '7890 AZN', 37.9345, 58.3012, 32.1, true, CURRENT_TIMESTAMP - INTERVAL '1 minute', 'route-005', true),
                                                                                                                                                                          ('vehicle-006', 'GPS_DEVICE_1006', '2468 BTM', 37.8645, 58.4567, 15.8, true, CURRENT_TIMESTAMP - INTERVAL '2 minutes', 'route-005', true);

-- Создаем view для удобного получения информации об автобусах с маршрутами
CREATE VIEW vehicles_with_routes AS
SELECT
    v.id,
    v.device_id,
    v.license_plate,
    v.current_latitude,
    v.current_longitude,
    v.speed_kmh,
    v.is_in_motion,
    v.last_position_update,
    v.is_active,
    br.route_number,
    br.route_name,
    br.route_color,
    CASE
        WHEN v.last_position_update > (CURRENT_TIMESTAMP - INTERVAL '5 minutes') THEN true
        ELSE false
        END as has_recent_position
FROM vehicles v
         LEFT JOIN bus_routes br ON v.assigned_route_id = br.id;

-- View для статистики по маршрутам
CREATE VIEW route_statistics AS
SELECT
    br.id as route_id,
    br.route_number,
    br.route_name,
    br.route_color,
    br.total_distance_forward_meters,
    br.total_distance_backward_meters,
    COUNT(v.id) as total_vehicles,
    COUNT(CASE WHEN v.is_active THEN 1 END) as active_vehicles,
    COUNT(CASE WHEN v.is_in_motion AND v.is_active THEN 1 END) as vehicles_in_motion,
    COUNT(CASE WHEN v.last_position_update > (CURRENT_TIMESTAMP - INTERVAL '5 minutes') AND v.is_active THEN 1 END) as vehicles_with_recent_position
FROM bus_routes br
         LEFT JOIN vehicles v ON br.id = v.assigned_route_id
WHERE br.is_active = true
GROUP BY br.id, br.route_number, br.route_name, br.route_color, br.total_distance_forward_meters, br.total_distance_backward_meters
ORDER BY br.route_number;

-- Комментарии к таблицам для документации
COMMENT ON TABLE vehicles IS 'Автобусы с GPS трекингом и назначенными маршрутами';
COMMENT ON TABLE bus_routes IS 'Автобусные маршруты города с геометрией пути';
COMMENT ON TABLE bus_stops IS 'Автобусные остановки с геокоординатами';
COMMENT ON TABLE route_stops IS 'Связь маршрутов с остановками и их порядок следования';
COMMENT ON TABLE route_schedules IS 'Расписание отправления автобусов по маршрутам';

COMMENT ON COLUMN vehicles.device_id IS 'ID GPS устройства из внешнего API';
COMMENT ON COLUMN vehicles.assigned_route_id IS 'Текущий назначенный маршрут автобуса';
COMMENT ON COLUMN vehicles.last_position_update IS 'Время последнего обновления GPS позиции';

COMMENT ON COLUMN bus_routes.route_geometry_forward IS 'GeoJSON LineString геометрия прямого направления';
COMMENT ON COLUMN bus_routes.route_geometry_backward IS 'GeoJSON LineString геометрия обратного направления';
COMMENT ON COLUMN bus_routes.geometry_forward IS 'PostGIS геометрия для быстрых геопространственных запросов';
COMMENT ON COLUMN bus_routes.total_distance_forward_meters IS 'Общая длина маршрута в прямом направлении в метрах';

COMMENT ON COLUMN route_stops.stop_sequence IS 'Порядковый номер остановки в маршруте';
COMMENT ON COLUMN route_stops.direction IS '0 = прямое направление, 1 = обратное направление';
COMMENT ON COLUMN route_stops.distance_from_start_meters IS 'Расстояние от начала маршрута до остановки в метрах';active = true;

-- Валидационные ограничения для остановок
ALTER TABLE bus_stops ADD CONSTRAINT chk_bus_stops_coordinates
    CHECK (
        latitude BETWEEN 35.0 AND 43.0 AND
        longitude BETWEEN 52.0 AND 67.0
        );

-- Таблица связи маршрутов и остановок (многие ко многим)
CREATE TABLE route_stops (
                             id VARCHAR(36) PRIMARY KEY,
                             route_id VARCHAR(36) NOT NULL,
                             stop_id VARCHAR(36) NOT NULL,
                             stop_sequence INTEGER NOT NULL, -- Порядок остановки в маршруте
                             direction INTEGER NOT NULL DEFAULT 0, -- 0 = прямое направление, 1 = обратное
                             estimated_travel_time_minutes INTEGER, -- Время до следующей остановки
                             created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

                             CONSTRAINT fk_route_stops_route FOREIGN KEY (route_id) REFERENCES bus_routes(id) ON DELETE CASCADE,
                             CONSTRAINT fk_route_stops_stop FOREIGN KEY (stop_id) REFERENCES bus_stops(id) ON DELETE CASCADE,
                             CONSTRAINT uk_route_stops_sequence UNIQUE (route_id, direction, stop_sequence),
                             CONSTRAINT uk_route_stops_stop_direction UNIQUE (route_id, stop_id, direction)
);

-- Индексы для route_stops
CREATE INDEX idx_route_stops_route ON route_stops(route_id, direction, stop_sequence);
CREATE INDEX idx_route_stops_stop ON route_stops(stop_id);

-- Валидационные ограничения
ALTER TABLE route_stops ADD CONSTRAINT chk_route_stops_direction
    CHECK (direction IN (0, 1));

ALTER TABLE route_stops ADD CONSTRAINT chk_route_stops_sequence_positive
    CHECK (stop_sequence > 0);

-- Добавляем внешний ключ для vehicles.assigned_route_id
ALTER TABLE vehicles ADD CONSTRAINT fk_vehicles_assigned_route
    FOREIGN KEY (assigned_route_id) REFERENCES bus_routes(id) ON DELETE SET NULL;

-- Таблица расписания (для будущего использования)
CREATE TABLE route_schedules (
                                 id VARCHAR(36) PRIMARY KEY,
                                 route_id VARCHAR(36) NOT NULL,
                                 direction INTEGER NOT NULL DEFAULT 0,
                                 departure_time TIME NOT NULL,
                                 days_of_week INTEGER[] NOT NULL DEFAULT '{1,2,3,4,5,6,7}', -- 1=Monday, 7=Sunday
                                 is_active BOOLEAN DEFAULT true,
                                 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                 CONSTRAINT fk_route_schedules_route FOREIGN KEY (route_id) REFERENCES bus_routes(id) ON DELETE CASCADE
);

-- Индексы для расписания
CREATE INDEX idx_route_schedules_route ON route_schedules(route_id, direction);
CREATE INDEX idx_route_schedules_time ON route_schedules(departure_time);
CREATE INDEX idx_route_schedules_active ON route_schedules(is_active);

-- Функция для автоматического обновления updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ language 'plpgsql';

-- Триггеры для автоматического обновления updated_at
CREATE TRIGGER update_vehicles_updated_at
    BEFORE UPDATE ON vehicles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bus_routes_updated_at
    BEFORE UPDATE ON bus_routes
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bus_stops_updated_at
    BEFORE UPDATE ON bus_stops
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Функция для поиска остановок в радиусе (для Use Cases)
CREATE OR REPLACE FUNCTION find_stops_within_radius(
    center_lat DOUBLE PRECISION,
    center_lon DOUBLE PRECISION,
    radius_meters INTEGER DEFAULT 800
)
RETURNS TABLE (
    stop_id VARCHAR(36),
    stop_name VARCHAR(200),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    distance_meters DOUBLE PRECISION
) AS $$
BEGIN
RETURN QUERY
SELECT
    bs.id,
    bs.stop_name,
    bs.latitude,
    bs.longitude,
    ST_Distance(
            ST_GeogFromText('POINT(' || center_lon || ' ' || center_lat || ')'),
            ST_GeogFromText('POINT(' || bs.longitude || ' ' || bs.latitude || ')')
    ) as distance_meters
FROM bus_stops bs
WHERE bs.is_active = true
  AND ST_DWithin(
        ST_GeogFromText('POINT(' || center_lon || ' ' || center_lat || ')'),
        ST_GeogFromText('POINT(' || bs.longitude || ' ' || bs.latitude || ')'),
        radius_meters
      )
ORDER BY distance_meters;
END;
$$ LANGUAGE plpgsql;

-- Функция для поиска автобусов в радиусе
CREATE OR REPLACE FUNCTION find_vehicles_within_radius(
    center_lat DOUBLE PRECISION,
    center_lon DOUBLE PRECISION,
    radius_meters INTEGER DEFAULT 1000
)
RETURNS TABLE (
    vehicle_id VARCHAR(36),
    license_plate VARCHAR(20),
    current_latitude DOUBLE PRECISION,
    current_longitude DOUBLE PRECISION,
    speed_kmh DOUBLE PRECISION,
    is_in_motion BOOLEAN,
    assigned_route_id VARCHAR(36),
    distance_meters DOUBLE PRECISION
) AS $
BEGIN
RETURN QUERY
SELECT
    v.id,
    v.license_plate,
    v.current_latitude,
    v.current_longitude,
    v.speed_kmh,
    v.is_in_motion,
    v.assigned_route_id,
    ST_Distance(
            ST_GeogFromText('POINT(' || center_lon || ' ' || center_lat || ')'),
            ST_GeogFromText('POINT(' || v.current_longitude || ' ' || v.current_latitude || ')')
    ) as distance_meters
FROM vehicles v
WHERE v.is_active = true
  AND v.current_latitude IS NOT NULL
  AND v.current_longitude IS NOT NULL
  AND v.last_position_update > (CURRENT_TIMESTAMP - INTERVAL '5 minutes')
  AND ST_DWithin(
        ST_GeogFromText('POINT(' || center_lon || ' ' || center_lat || ')'),
        ST_GeogFromText('POINT(' || v.current_longitude || ' ' || v.current_latitude || ')'),
        radius_meters
      )
ORDER BY distance_meters;
END;
$ LANGUAGE plpgsql;

-- Функция для получения остановок маршрута по порядку
CREATE OR REPLACE FUNCTION get_route_stops_ordered(
    p_route_id VARCHAR(36),
    p_direction INTEGER DEFAULT 0
)
RETURNS TABLE (
    stop_id VARCHAR(36),
    stop_name VARCHAR(200),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    stop_sequence INTEGER,
    estimated_travel_time_minutes INTEGER
) AS $
BEGIN
RETURN QUERY
SELECT
    bs.id,
    bs.stop_name,
    bs.latitude,
    bs.longitude,
    rs.stop_sequence,
    rs.estimated_travel_time_minutes
FROM route_stops rs
         JOIN bus_stops bs ON rs.stop_id = bs.id
WHERE rs.route_id = p_route_id
  AND rs.direction = p_direction
  AND bs.is_active = true
ORDER BY rs.stop_sequence;
END;
$ LANGUAGE plpgsql;

-- Вставка начальных данных для тестирования

-- Автобусные маршруты Ашхабада
INSERT INTO bus_routes (id, route_number, route_name, route_name_tm, route_color, fare_price, estimated_duration_minutes) VALUES
                                                                                                                              ('route-001', '1', 'Центральный рынок - Гипподром', 'Merkezi bazar - Ýaryş meýdany', '#E53935', 1.00, 45),
                                                                                                                              ('route-002', '7', 'Аэропорт - Центр города', 'Howa menzili - Şäher merkezi', '#1976D2', 2.00, 60),
                                                                                                                              ('route-003', '12', 'Махтумкули - Университет', 'Magtymguly - Uniwersitet', '#388E3C', 1.00, 35),
                                                                                                                              ('route-004', '25', 'Жилой массив - Арчабиль', 'Ýaşaýyş toplumy - Arçabil', '#F57F17', 1.50, 50);

-- Автобусные остановки Ашхабада (реальные координаты)
INSERT INTO bus_stops (id, stop_code, stop_name, stop_name_tm, latitude, longitude, is_major_stop, has_shelter) VALUES
                                                                                                                    ('stop-001', 'ASH001', 'Центральный рынок', 'Merkezi bazar', 37.9601, 58.3261, true, true),
                                                                                                                    ('stop-002', 'ASH002', 'Площадь Независимости', 'Garaşsyzlyk meýdany', 37.9255, 58.3836, true, true),
                                                                                                                    ('stop-003', 'ASH003', 'Гипподром', 'Ýaryş meýdany', 37.8987, 58.3951, true, true),
                                                                                                                    ('stop-004', 'ASH004', 'Аэропорт имени Огузхана', 'Oguzhan adyndaky howa menzili', 37.9868, 58.3609, true, true),
                                                                                                                    ('stop-005', 'ASH005', 'Университет Туркменистана', 'Türkmenistanyň uniwersiteti', 37.9089, 58.3831, true, true),
                                                                                                                    ('stop-006', 'ASH006', 'Арчабиль', 'Arçabil', 37.8756, 58.4123, true, true),
                                                                                                                    ('stop-007', 'ASH007', 'Махтумкули проспект', 'Magtymguly şaýoly', 37.9178, 58.3662, false, true),
                                                                                                                    ('stop-008', 'ASH008', 'Жилой массив Бериев', 'Beriýew ýaşaýyş toplumy', 37.8912, 58.4287, false, false),
                                                                                                                    ('stop-009', 'ASH009', 'Нейтралитет арка', 'Bitaraplyk arkasy', 37.9081, 58.3897, true, true),
                                                                                                                    ('stop-010', 'ASH010', 'Театр имeni Молланепеса', 'Mollanepes adyndaky teatr', 37.9156, 58.3945, false, true);

-- Связь маршрутов с остановками
-- Маршрут 1: Центральный рынок - Гипподром
INSERT INTO route_stops (id, route_id, stop_id, stop_sequence, direction, estimated_travel_time_minutes) VALUES
                                                                                                             ('rs-001', 'route-001', 'stop-001', 1, 0, 8),  -- Центральный рынок
                                                                                                             ('rs-002', 'route-001', 'stop-007', 2, 0, 12), -- Махтумкули проспект
                                                                                                             ('rs-003', 'route-001', 'stop-002', 3, 0, 10), -- Площадь Независимости
                                                                                                             ('rs-004', 'route-001', 'stop-009', 4, 0, 15), -- Нейтралитет арка
                                                                                                             ('rs-005', 'route-001', 'stop-003', 5, 0, 0),  -- Гипподром (конечная)

-- Обратное направление маршрута 1
                                                                                                             ('rs-006', 'route-001', 'stop-003', 1, 1, 15), -- Гипподром
                                                                                                             ('rs-007', 'route-001', 'stop-009', 2, 1, 10), -- Нейтралитет арка
                                                                                                             ('rs-008', 'route-001', 'stop-002', 3, 1, 12), -- Площадь Независимости
                                                                                                             ('rs-009', 'route-001', 'stop-007', 4, 1, 8),  -- Махтумкули проспект
                                                                                                             ('rs-010', 'route-001', 'stop-001', 5, 1, 0),  -- Центральный рынок (конечная)

-- Маршрут 7: Аэропорт - Центр города
                                                                                                             ('rs-011', 'route-002', 'stop-004', 1, 0, 25), -- Аэропорт
                                                                                                             ('rs-012', 'route-002', 'stop-001', 2, 0, 20), -- Центральный рынок
                                                                                                             ('rs-013', 'route-002', 'stop-002', 3, 0, 15), -- Площадь Независимости
                                                                                                             ('rs-014', 'route-002', 'stop-010', 4, 0, 0),  -- Театр (конечная)

-- Обратное направление маршрута 7
                                                                                                             ('rs-015', 'route-002', 'stop-010', 1, 1, 15), -- Театр
                                                                                                             ('rs-016', 'route-002', 'stop-002', 2, 1, 20), -- Площадь Независимости
                                                                                                             ('rs-017', 'route-002', 'stop-001', 3, 1, 25), -- Центральный рынок
                                                                                                             ('rs-018', 'route-002', 'stop-004', 4, 1, 0);  -- Аэропорт (конечная)

-- Тестовые автобусы с туркменскими номерами
INSERT INTO vehicles (id, device_id, license_plate, current_latitude, current_longitude, speed_kmh, is_in_motion, last_position_update, assigned_route_id, is_active) VALUES
                                                                                                                                                                          ('vehicle-001', 'GPS_DEVICE_1001', '1234 ABD', 37.9601, 58.3261, 0.0, false, CURRENT_TIMESTAMP - INTERVAL '2 minutes', 'route-001', true),
                                                                                                                                                                          ('vehicle-002', 'GPS_DEVICE_1002', '5678 AGH', 37.9255, 58.3836, 25.5, true, CURRENT_TIMESTAMP - INTERVAL '30 seconds', 'route-001', true),
                                                                                                                                                                          ('vehicle-003', 'GPS_DEVICE_1003', '9012 ATM', 37.9868, 58.3609, 0.0, false, CURRENT_TIMESTAMP - INTERVAL '1 minute', 'route-002', true),
                                                                                                                                                                          ('vehicle-004', 'GPS_DEVICE_1004', '3456 AWM', 37.9089, 58.3831, 18.2, true, CURRENT_TIMESTAMP - INTERVAL '45 seconds', 'route-003', true);

-- Базовое расписание для тестирования
INSERT INTO route_schedules (id, route_id, direction, departure_time, days_of_week) VALUES
-- Маршрут 1 - каждые 15 минут в будни
('sched-001', 'route-001', 0, '06:00:00', '{1,2,3,4,5}'),
('sched-002', 'route-001', 0, '06:15:00', '{1,2,3,4,5}'),
('sched-003', 'route-001', 0, '06:30:00', '{1,2,3,4,5}'),
('sched-004', 'route-001', 0, '06:45:00', '{1,2,3,4,5}'),

-- Маршрут 7 - каждые 30 минут
('sched-005', 'route-002', 0, '06:00:00', '{1,2,3,4,5,6,7}'),
('sched-006', 'route-002', 0, '06:30:00', '{1,2,3,4,5,6,7}'),
('sched-007', 'route-002', 0, '07:00:00', '{1,2,3,4,5,6,7}'),
('sched-008', 'route-002', 0, '07:30:00', '{1,2,3,4,5,6,7}');



-- Создаем view для удобного получения информации об автобусах с маршрутами
CREATE VIEW vehicles_with_routes AS
SELECT
    v.id,
    v.device_id,
    v.license_plate,
    v.current_latitude,
    v.current_longitude,
    v.speed_kmh,
    v.is_in_motion,
    v.last_position_update,
    v.is_active,
    br.route_number,
    br.route_name,
    br.route_color,
    CASE
        WHEN v.last_position_update > (CURRENT_TIMESTAMP - INTERVAL '5 minutes') THEN true
        ELSE false
        END as has_recent_position
FROM vehicles v
         LEFT JOIN bus_routes br ON v.assigned_route_id = br.id;

-- View для статистики по маршрутам
CREATE VIEW route_statistics AS
SELECT
    br.id as route_id,
    br.route_number,
    br.route_name,
    COUNT(v.id) as total_vehicles,
    COUNT(CASE WHEN v.is_active THEN 1 END) as active_vehicles,
    COUNT(CASE WHEN v.is_in_motion AND v.is_active THEN 1 END) as vehicles_in_motion,
    COUNT(CASE WHEN v.last_position_update > (CURRENT_TIMESTAMP - INTERVAL '5 minutes') AND v.is_active THEN 1 END) as vehicles_with_recent_position
FROM bus_routes br
         LEFT JOIN vehicles v ON br.id = v.assigned_route_id
WHERE br.is_active = true
GROUP BY br.id, br.route_number, br.route_name
ORDER BY br.route_number;

-- Комментарии к таблицам для документации
COMMENT ON TABLE vehicles IS 'Автобусы с GPS трекингом и назначенными маршрутами';
COMMENT ON TABLE bus_routes IS 'Автобусные маршруты города';
COMMENT ON TABLE bus_stops IS 'Автобусные остановки с геокоординатами';
COMMENT ON TABLE route_stops IS 'Связь маршрутов с остановками и их порядок следования';
COMMENT ON TABLE route_schedules IS 'Расписание отправления автобусов по маршрутам';

COMMENT ON COLUMN vehicles.device_id IS 'ID GPS устройства из внешнего API';
COMMENT ON COLUMN vehicles.assigned_route_id IS 'Текущий назначенный маршрут автобуса';
COMMENT ON COLUMN vehicles.last_position_update IS 'Время последнего обновления GPS позиции';

COMMENT ON COLUMN route_stops.stop_sequence IS 'Порядковый номер остановки в маршруте';
COMMENT ON COLUMN route_stops.direction IS '0 = прямое направление, 1 = обратное направление';
COMMENT ON COLUMN route_stops.estimated_travel_time_minutes IS 'Примерное время до следующей остановки в минутах';); -- Формат туркменских номеров: "1992 AGH"

-- Таблица автобусных маршрутов (BusRoute Aggregate) с геометрией
CREATE TABLE bus_routes (
                            id VARCHAR(36) PRIMARY KEY,
                            route_number VARCHAR(10) NOT NULL UNIQUE,
                            route_name VARCHAR(200) NOT NULL,
                            route_name_tm VARCHAR(200), -- Название на туркменском
                            route_color VARCHAR(7) DEFAULT '#1976D2', -- HEX цвет маршрута
                            is_active BOOLEAN DEFAULT true,
                            fare_price DECIMAL(8,2) DEFAULT 1.00, -- Стоимость проезда в манатах
                            estimated_duration_minutes INTEGER, -- Примерное время полного маршрута

    -- НОВОЕ: Геометрия маршрута (LineString координаты)
                            route_geometry_forward TEXT, -- GeoJSON LineString для прямого направления
                            route_geometry_backward TEXT, -- GeoJSON LineString для обратного направления
                            total_distance_forward_meters INTEGER, -- Общая длина прямого направления
                            total_distance_backward_meters INTEGER, -- Общая длина обратного направления

    -- PostGIS геометрия для быстрых геопространственных запросов
                            geometry_forward GEOMETRY(LINESTRING, 4326), -- WGS84 координаты
                            geometry_backward GEOMETRY(LINESTRING, 4326),

                            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            version BIGINT DEFAULT 0
);

-- Индексы для bus_routes
CREATE INDEX idx_bus_routes_number ON bus_routes(route_number);
CREATE INDEX idx_bus_routes_active ON bus_routes(is_active);

-- Геопространственные индексы для быстрого поиска маршрутов
CREATE INDEX idx_bus_routes_geometry_forward ON bus_routes USING GIST (geometry_forward)
    WHERE geometry_forward IS NOT NULL;

CREATE INDEX idx_bus_routes_geometry_backward ON bus_routes USING GIST (geometry_backward)
    WHERE geometry_backward IS NOT NULL;

ALTER TABLE bus_routes ADD CONSTRAINT chk_bus_routes_number_format
    CHECK (license_plate ~ '^\d{4}\s[A-Z]{3}$');