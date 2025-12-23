
CREATE TABLE garages (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    name_tm VARCHAR(100),
    name_en VARCHAR(100),
    latitude NUMERIC(10,8) NOT NULL,
    longitude NUMERIC(11,8) NOT NULL,
    radius_meters INTEGER DEFAULT 200,
    city_id VARCHAR(36),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,

    CONSTRAINT chk_garages_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_garages_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_garages_radius CHECK (radius_meters > 0 AND radius_meters <= 1000)
);

CREATE INDEX idx_garages_active ON garages(is_active) WHERE is_active = true;
CREATE INDEX idx_garages_city ON garages(city_id) WHERE city_id IS NOT NULL;

CREATE TRIGGER update_garages_updated_at
    BEFORE UPDATE ON garages
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();


INSERT INTO garages (
    id,
    name,
    name_tm,
    name_en,
    latitude,
    longitude,
    radius_meters,
    is_active
) VALUES (
    gen_random_uuid(),
    'Комплекс автотранспортных средств Чоганлы',
    'Çoganlы awtoulag toplumы',
    'Chogantly Vehicle Complex',
    38.041499,
    58.400982,
    180,
    true
);

COMMENT ON TABLE garages IS 'Garages and vehicle depots with geofence zones for automatic entry/exit detection';
COMMENT ON COLUMN garages.name IS 'Garage name in Russian';
COMMENT ON COLUMN garages.name_tm IS 'Garage name in Turkmen';
COMMENT ON COLUMN garages.name_en IS 'Garage name in English';
COMMENT ON COLUMN garages.latitude IS 'Latitude of garage center point (WGS84)';
COMMENT ON COLUMN garages.longitude IS 'Longitude of garage center point (WGS84)';
COMMENT ON COLUMN garages.radius_meters IS 'Geofence radius in meters for detecting vehicle entry/exit';
COMMENT ON COLUMN garages.city_id IS 'Optional reference to city (for multi-city support)';
