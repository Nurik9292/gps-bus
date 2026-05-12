ALTER TABLE cities
    ADD COLUMN latitude  DOUBLE PRECISION,
    ADD COLUMN longitude DOUBLE PRECISION;

ALTER TABLE cities
    ADD CONSTRAINT cities_lat_range  CHECK (latitude  IS NULL OR (latitude  BETWEEN -90  AND  90)),
    ADD CONSTRAINT cities_lon_range  CHECK (longitude IS NULL OR (longitude BETWEEN -180 AND 180));

UPDATE cities SET latitude = 37.9064,            longitude = 58.3707            WHERE id = 'city-001';
UPDATE cities SET latitude = 39.973111,          longitude = 52.855503          WHERE id = 'city-004';
UPDATE cities SET latitude = 38.06984316741881,  longitude = 58.06577399177728  WHERE id = 'city-006';
UPDATE cities SET latitude = 39.973111,          longitude = 52.855503          WHERE id = '90210ec4-33ab-4928-a676-b4262c8dc4ab';
