CREATE MATERIALIZED VIEW IF NOT EXISTS mv_popular_direct_routes AS
SELECT
    rs_from.stop_id as from_stop_id,
    rs_to.stop_id as to_stop_id,
    br.id as route_id,
    br.route_number,
    br.route_name,
    br.route_color,
    rs_from.direction,
    ABS(rs_to.stop_sequence - rs_from.stop_sequence) * 2 as estimated_minutes,
    COUNT(DISTINCT v.id) as active_vehicles,
    bs_from.stop_name as from_stop_name,
    bs_from.latitude as from_latitude,
    bs_from.longitude as from_longitude,
    bs_to.stop_name as to_stop_name,
    bs_to.latitude as to_latitude,
    bs_to.longitude as to_longitude,
    NOW() as last_refreshed
FROM route_stops rs_from
JOIN route_stops rs_to
    ON rs_from.route_id = rs_to.route_id
    AND rs_from.direction = rs_to.direction
    AND rs_from.stop_sequence < rs_to.stop_sequence
JOIN bus_routes br ON rs_from.route_id = br.id
JOIN bus_stops bs_from ON rs_from.stop_id = bs_from.id
JOIN bus_stops bs_to ON rs_to.stop_id = bs_to.id
LEFT JOIN vehicles v
    ON v.assigned_route_id = br.id
    AND v.is_active = true
WHERE br.is_active = true
  AND bs_from.is_active = true
  AND bs_to.is_active = true
  AND ABS(rs_to.stop_sequence - rs_from.stop_sequence) <= 20
GROUP BY
    rs_from.stop_id, rs_to.stop_id, br.id, br.route_number,
    br.route_name, br.route_color, rs_from.direction,
    rs_from.stop_sequence, rs_to.stop_sequence,
    bs_from.stop_name, bs_from.latitude, bs_from.longitude,
    bs_to.stop_name, bs_to.latitude, bs_to.longitude;

-- UNIQUE index required for REFRESH MATERIALIZED VIEW CONCURRENTLY
CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_direct_routes_unique_key
    ON mv_popular_direct_routes(from_stop_id, to_stop_id, route_id, direction);

CREATE INDEX IF NOT EXISTS idx_mv_direct_routes_from_to
    ON mv_popular_direct_routes(from_stop_id, to_stop_id);

CREATE INDEX IF NOT EXISTS idx_mv_direct_routes_route
    ON mv_popular_direct_routes(route_id);


CREATE MATERIALIZED VIEW IF NOT EXISTS mv_route_statistics AS
SELECT
    br.id as route_id,
    br.route_number,
    br.route_name,
    br.is_active,
    COUNT(DISTINCT rs.stop_id) as total_stops,
    COUNT(DISTINCT CASE WHEN rs.direction = 0 THEN rs.stop_id END) as forward_stops,
    COUNT(DISTINCT CASE WHEN rs.direction = 1 THEN rs.stop_id END) as backward_stops,
    COUNT(DISTINCT v.id) as total_vehicles,
    COUNT(DISTINCT CASE WHEN v.is_active = true THEN v.id END) as active_vehicles,
    MAX(rs.stop_sequence) as max_sequence,
    NOW() as last_refreshed
FROM bus_routes br
LEFT JOIN route_stops rs ON br.id = rs.route_id
LEFT JOIN bus_stops bs ON rs.stop_id = bs.id
LEFT JOIN vehicles v ON br.id = v.assigned_route_id
GROUP BY br.id, br.route_number, br.route_name, br.is_active;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_route_stats_unique_key
    ON mv_route_statistics(route_id);

CREATE INDEX IF NOT EXISTS idx_mv_route_stats_active
    ON mv_route_statistics(is_active)
    WHERE is_active = true;


CREATE MATERIALIZED VIEW IF NOT EXISTS mv_stop_connections AS
SELECT
    rs1.stop_id as from_stop_id,
    rs2.stop_id as to_stop_id,
    COUNT(DISTINCT rs1.route_id) as route_count,
    ARRAY_AGG(DISTINCT br.route_number ORDER BY br.route_number) as route_numbers,
    MIN(ABS(rs2.stop_sequence - rs1.stop_sequence) * 2) as min_travel_minutes,
    AVG(ABS(rs2.stop_sequence - rs1.stop_sequence) * 2) as avg_travel_minutes,
    bs_from.stop_name as from_stop_name,
    bs_to.stop_name as to_stop_name,
    NOW() as last_refreshed
FROM route_stops rs1
JOIN route_stops rs2
    ON rs1.route_id = rs2.route_id
    AND rs1.direction = rs2.direction
    AND rs1.stop_id != rs2.stop_id
    AND rs1.stop_sequence < rs2.stop_sequence
JOIN bus_routes br ON rs1.route_id = br.id
JOIN bus_stops bs_from ON rs1.stop_id = bs_from.id
JOIN bus_stops bs_to ON rs2.stop_id = bs_to.id
WHERE br.is_active = true
  AND bs_from.is_active = true
  AND bs_to.is_active = true
GROUP BY
    rs1.stop_id, rs2.stop_id,
    bs_from.stop_name, bs_to.stop_name;

-- UNIQUE index required for REFRESH MATERIALIZED VIEW CONCURRENTLY
CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_connections_unique_key
    ON mv_stop_connections(from_stop_id, to_stop_id);

CREATE INDEX IF NOT EXISTS idx_mv_connections_from
    ON mv_stop_connections(from_stop_id);

CREATE INDEX IF NOT EXISTS idx_mv_connections_to
    ON mv_stop_connections(to_stop_id);


CREATE MATERIALIZED VIEW IF NOT EXISTS mv_active_routes_summary AS
SELECT
    br.id as route_id,
    br.route_number,
    br.route_name,
    br.route_color,
    br.estimated_duration_minutes,
    COUNT(DISTINCT CASE WHEN v.is_active = true THEN v.id END) as active_vehicles,
    COUNT(DISTINCT CASE WHEN v.is_in_motion = true THEN v.id END) as moving_vehicles,
    COUNT(DISTINCT rs.stop_id) as total_stops,
    MAX(v.last_position_update) as last_vehicle_update,
    NOW() as last_refreshed
FROM bus_routes br
LEFT JOIN vehicles v ON br.id = v.assigned_route_id
LEFT JOIN route_stops rs ON br.id = rs.route_id
WHERE br.is_active = true
GROUP BY
    br.id, br.route_number, br.route_name,
    br.route_color, br.estimated_duration_minutes;

-- UNIQUE index required for REFRESH MATERIALIZED VIEW CONCURRENTLY
CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_active_routes_unique_key
    ON mv_active_routes_summary(route_id);

-- Create index for fast active route lookups
CREATE INDEX IF NOT EXISTS idx_mv_active_routes_number
    ON mv_active_routes_summary(route_number);


COMMENT ON MATERIALIZED VIEW mv_popular_direct_routes IS
    'Phase 2.4: Caches popular direct route connections. Refresh every 15 minutes.';

COMMENT ON MATERIALIZED VIEW mv_route_statistics IS
    'Phase 2.4: Route usage statistics and analytics. Refresh every 30 minutes.';

COMMENT ON MATERIALIZED VIEW mv_stop_connections IS
    'Phase 2.4: Stop-to-stop connection summary with route counts. Refresh every 20 minutes.';

COMMENT ON MATERIALIZED VIEW mv_active_routes_summary IS
    'Phase 2.4: Active routes with real-time vehicle counts. Refresh every 5 minutes.';


CREATE OR REPLACE FUNCTION refresh_all_materialized_views()
RETURNS void AS $$
BEGIN
    RAISE NOTICE 'Refreshing mv_popular_direct_routes...';
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_popular_direct_routes;

    RAISE NOTICE 'Refreshing mv_route_statistics...';
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_route_statistics;

    RAISE NOTICE 'Refreshing mv_stop_connections...';
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_stop_connections;

    RAISE NOTICE 'Refreshing mv_active_routes_summary...';
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_active_routes_summary;

    RAISE NOTICE 'All materialized views refreshed successfully';
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION refresh_materialized_view(view_name text)
RETURNS void AS $$
BEGIN
    EXECUTE format('REFRESH MATERIALIZED VIEW CONCURRENTLY %I', view_name);
    RAISE NOTICE 'Materialized view % refreshed', view_name;
END;
$$ LANGUAGE plpgsql;


SELECT refresh_all_materialized_views();
