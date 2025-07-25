ALTER TABLE bus_routes ADD CONSTRAINT fk_bus_routes_city
    FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE SET NULL;

ALTER TABLE bus_stops ADD CONSTRAINT fk_bus_stops_city
    FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE SET NULL;