CREATE TABLE bus_routes (
                            id VARCHAR(36) PRIMARY KEY,
                            route_number VARCHAR(10) NOT NULL UNIQUE,
                            route_name VARCHAR(200) NOT NULL,
                            name_tm VARCHAR(200),
                            name_en VARCHAR(200),
                            route_color VARCHAR(7) DEFAULT '#1976D2',
                            is_active BOOLEAN DEFAULT true,
                            fare_price DECIMAL(8,2) DEFAULT 1.00,
                            estimated_duration_minutes INTEGER,
                            route_geometry_forward TEXT,
                            route_geometry_backward TEXT,
                            total_distance_forward_meters INTEGER,
                            total_distance_backward_meters INTEGER,
                            geometry_forward GEOMETRY(LINESTRING, 4326),
                            geometry_backward GEOMETRY(LINESTRING, 4326),
                            city_id VARCHAR(36),
                            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            version BIGINT DEFAULT 0
);

CREATE INDEX idx_bus_routes_number ON bus_routes(route_number);
CREATE INDEX idx_bus_routes_active ON bus_routes(is_active);
CREATE INDEX idx_bus_routes_city ON bus_routes(city_id);

CREATE INDEX idx_bus_routes_geometry_forward ON bus_routes USING GIST (geometry_forward)
    WHERE geometry_forward IS NOT NULL;

CREATE INDEX idx_bus_routes_geometry_backward ON bus_routes USING GIST (geometry_backward)
    WHERE geometry_backward IS NOT NULL;

ALTER TABLE bus_routes ADD CONSTRAINT chk_bus_routes_number_format
    CHECK (route_number ~ '^\d+[A-Z]?$');

ALTER TABLE bus_routes ADD CONSTRAINT chk_bus_routes_fare_positive
    CHECK (fare_price > 0);

CREATE TRIGGER update_bus_routes_updated_at
    BEFORE UPDATE ON bus_routes
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
