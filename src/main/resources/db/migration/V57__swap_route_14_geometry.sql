UPDATE bus_routes
SET geometry_forward = geometry_backward,
    geometry_backward = geometry_forward,
    total_distance_forward_meters = total_distance_backward_meters,
    total_distance_backward_meters = total_distance_forward_meters
WHERE route_number = '14';
