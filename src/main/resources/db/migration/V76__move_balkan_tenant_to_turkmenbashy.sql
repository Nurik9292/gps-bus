UPDATE vehicles
   SET city_id = 'city-004'
 WHERE city_id = '90210ec4-33ab-4928-a676-b4262c8dc4ab';

UPDATE cities
   SET gps_tenant_id = NULL
 WHERE id = '90210ec4-33ab-4928-a676-b4262c8dc4ab'
   AND gps_tenant_id IS NOT NULL;

UPDATE cities
   SET gps_tenant_id = 'BALKAN'
 WHERE id = 'city-004'
   AND (gps_tenant_id IS NULL OR gps_tenant_id <> 'BALKAN');
