-- V49: Fill total_distance_forward_meters / total_distance_backward_meters
-- from PostGIS geometry for all routes where these values are missing (= 0 or NULL).
--
-- Root cause: when routes were imported, the total_distance columns were not populated.
-- ST_Length(...::geography) returns metres on the ellipsoid (accurate to ~0.5%).
-- Only updates rows where value is missing — safe to run on a live database.

UPDATE bus_routes
SET total_distance_forward_meters = ROUND(ST_Length(geometry_forward::geography))::INTEGER
WHERE geometry_forward IS NOT NULL
  AND (total_distance_forward_meters IS NULL OR total_distance_forward_meters = 0);

UPDATE bus_routes
SET total_distance_backward_meters = ROUND(ST_Length(geometry_backward::geography))::INTEGER
WHERE geometry_backward IS NOT NULL
  AND (total_distance_backward_meters IS NULL OR total_distance_backward_meters = 0);

-- Verify (informational — does not affect migration success):
-- SELECT id, route_number,
--        total_distance_forward_meters,
--        total_distance_backward_meters
-- FROM bus_routes
-- WHERE total_distance_forward_meters IS NOT NULL
-- ORDER BY route_number;
