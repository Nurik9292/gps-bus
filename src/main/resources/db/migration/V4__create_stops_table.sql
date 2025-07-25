CREATE TABLE bus_stops (
                           id VARCHAR(36) PRIMARY KEY,
                           stop_name VARCHAR(200) NOT NULL,
                           stop_name_tm VARCHAR(200),
                           latitude DOUBLE PRECISION NOT NULL,
                           longitude DOUBLE PRECISION NOT NULL,
                           stop_code VARCHAR(20) UNIQUE,
                           is_active BOOLEAN DEFAULT true,
                           city_id VARCHAR(36),
                           created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           version BIGINT DEFAULT 0
);

-- Индексы для bus_stops
CREATE INDEX idx_bus_stops_name ON bus_stops(stop_name);
CREATE INDEX idx_bus_stops_code ON bus_stops(stop_code);
CREATE INDEX idx_bus_stops_active ON bus_stops(is_active);
CREATE INDEX idx_bus_stops_city ON bus_stops(city_id);

-- Геопространственный индекс для поиска остановок
CREATE INDEX idx_bus_stops_location ON bus_stops USING GIST (
    ST_Point(longitude, latitude)
    ) WHERE is_active = true;

-- Ограничения для bus_stops
ALTER TABLE bus_stops ADD CONSTRAINT chk_bus_stops_coordinates
    CHECK (
        latitude BETWEEN 35.0 AND 43.0 AND
        longitude BETWEEN 52.0 AND 67.0
        );

-- Триггер для автоматического обновления updated_at
CREATE TRIGGER update_bus_stops_updated_at
    BEFORE UPDATE ON bus_stops
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();