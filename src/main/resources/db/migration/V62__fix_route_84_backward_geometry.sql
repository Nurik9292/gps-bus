UPDATE bus_routes
SET geometry_backward = ST_Reverse(geometry_forward),
    total_distance_backward_meters = total_distance_forward_meters
WHERE route_number = '84';
