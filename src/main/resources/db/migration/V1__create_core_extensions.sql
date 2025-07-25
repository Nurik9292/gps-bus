CREATE EXTENSION IF NOT EXISTS postgis;

-- Функция для автоматического обновления updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ language 'plpgsql';

-- Функция для поиска остановок в радиусе
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
