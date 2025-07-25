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

-- Геопространственный индекс для поиска автобусов
CREATE INDEX idx_vehicles_location ON vehicles USING GIST (
    ST_Point(current_longitude, current_latitude)
    ) WHERE current_latitude IS NOT NULL AND current_longitude IS NOT NULL AND is_active = true;

CREATE INDEX idx_vehicles_active_recent ON vehicles(is_active, last_position_update)
    WHERE is_active = true;

-- Ограничения для vehicles
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

-- Триггер для автоматического обновления updated_at
CREATE TRIGGER update_vehicles_updated_at
    BEFORE UPDATE ON vehicles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();