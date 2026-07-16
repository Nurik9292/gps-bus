ALTER TABLE bus_routes DROP CONSTRAINT IF EXISTS bus_routes_route_number_key;

UPDATE bus_routes
SET route_number = regexp_replace(route_number, '[^0-9]', '', 'g')
WHERE route_number ~ '^[0-9]+[A-Za-z]+$';

ALTER TABLE bus_routes
    ADD CONSTRAINT bus_routes_city_route_number_uniq UNIQUE (city_id, route_number);

UPDATE vehicles
SET route_number = regexp_replace(route_number, '[^0-9]', '', 'g')
WHERE route_number ~ '^[0-9]+[A-Za-z]+$';

DELETE FROM stop_dwell_stats
WHERE route_number ~ '^[0-9]+[A-Za-z]+$';

DELETE FROM segment_travel_stats
WHERE route_number ~ '^[0-9]+[A-Za-z]+$';

SELECT * FROM catalog_search_rebuild();
