ALTER TABLE vehicles ADD COLUMN city_id VARCHAR(36);

ALTER TABLE vehicles
    ADD CONSTRAINT fk_vehicles_city
    FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE SET NULL;

CREATE INDEX idx_vehicles_city ON vehicles(city_id) WHERE city_id IS NOT NULL;

UPDATE vehicles v
   SET city_id = br.city_id
  FROM bus_routes br
 WHERE v.assigned_route_id = br.id
   AND v.city_id IS NULL
   AND br.city_id IS NOT NULL;

UPDATE vehicles
   SET city_id = 'city-001'
 WHERE city_id IS NULL;

COMMENT ON COLUMN vehicles.city_id IS 'City this vehicle physically operates in. Drives GPS tenant routing via cities.gps_tenant_id.';
