ALTER TABLE trip_plans
    RENAME COLUMN start_latitude TO origin_latitude;

ALTER TABLE trip_plans
    RENAME COLUMN start_longitude TO origin_longitude;

ALTER TABLE trip_plans
    RENAME COLUMN end_latitude TO destination_latitude;

ALTER TABLE trip_plans
    RENAME COLUMN end_longitude TO destination_longitude;

ALTER TABLE trip_plans
    ADD COLUMN IF NOT EXISTS search_time TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE trip_plans
    ADD COLUMN IF NOT EXISTS options_count INTEGER DEFAULT 0;

ALTER TABLE trip_plans
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE bus_stops ADD COLUMN IF NOT EXISTS is_major_stop BOOLEAN DEFAULT false;
ALTER TABLE bus_stops ADD COLUMN IF NOT EXISTS has_shelter BOOLEAN DEFAULT false;

ALTER TABLE bus_stops ALTER COLUMN is_major_stop SET NOT NULL;

DROP INDEX IF EXISTS idx_trip_plans_coordinates;
CREATE INDEX idx_trip_plans_coordinates
    ON trip_plans(origin_latitude, origin_longitude, destination_latitude, destination_longitude);

CREATE INDEX IF NOT EXISTS idx_bus_stops_major
    ON bus_stops(is_major_stop) WHERE is_major_stop = true;


COMMENT ON COLUMN trip_plans.origin_latitude IS 'Широта точки отправления';
COMMENT ON COLUMN trip_plans.origin_longitude IS 'Долгота точки отправления';
COMMENT ON COLUMN trip_plans.destination_latitude IS 'Широта точки назначения';
COMMENT ON COLUMN trip_plans.destination_longitude IS 'Долгота точки назначения';
COMMENT ON COLUMN trip_plans.options_count IS 'Количество найденных вариантов поездки';
COMMENT ON COLUMN trip_plans.search_time IS 'Время выполнения поиска';


COMMENT ON COLUMN bus_stops.is_major_stop IS 'Является ли остановка крупной (терминал, пересадочный узел)';
