ALTER TABLE cities ADD COLUMN gps_tenant_id VARCHAR(32);

CREATE INDEX idx_cities_gps_tenant ON cities(gps_tenant_id) WHERE gps_tenant_id IS NOT NULL;

UPDATE cities SET gps_tenant_id = 'ASHGABAT' WHERE id = 'city-001';
UPDATE cities SET gps_tenant_id = 'BALKAN'   WHERE name_tm = 'Awaza';

COMMENT ON COLUMN cities.gps_tenant_id IS 'GPS tenant identifier mapping the city to a Tugdk API token. Cities sharing one regional Tugdk token share the same tenant id.';
