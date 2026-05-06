UPDATE bus_routes
SET geometry_forward = ST_Reverse(geometry_forward)
WHERE route_number = '14';
