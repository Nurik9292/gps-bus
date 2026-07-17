UPDATE bus_routes
SET city_id = 'city-003',
    updated_at = now()
WHERE route_number ~ '^[0-9]+M$';
