-- ============================================================================
-- V46: Routing Analytics Materialized Views
-- Purpose: Analytics views for trip planning search data
-- ============================================================================

-- 1. Hourly search statistics
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_search_hourly_stats AS
SELECT
    DATE_TRUNC('hour', tp.created_at) AS hour,
    COUNT(*)::INTEGER AS total_searches,
    COUNT(CASE WHEN EXISTS (
        SELECT 1 FROM trip_options topt WHERE topt.trip_plan_id = tp.id
    ) THEN 1 END)::INTEGER AS successful_searches,
    COUNT(CASE WHEN NOT EXISTS (
        SELECT 1 FROM trip_options topt WHERE topt.trip_plan_id = tp.id
    ) THEN 1 END)::INTEGER AS failed_searches,
    COALESCE(AVG(
        (SELECT COUNT(*) FROM trip_options topt WHERE topt.trip_plan_id = tp.id)
    ), 0)::DOUBLE PRECISION AS avg_options,
    NOW() AS last_refreshed
FROM trip_plans tp
GROUP BY DATE_TRUNC('hour', tp.created_at);

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_search_hourly_stats_hour
    ON mv_search_hourly_stats(hour);


-- 2. Search heatmap (origin and destination points, ~111m grid)
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_search_heatmap AS
SELECT
    lat,
    lon,
    COUNT(*)::INTEGER AS search_count,
    point_type,
    NOW() AS last_refreshed
FROM (
    SELECT
        ROUND(start_latitude::NUMERIC, 3)::DOUBLE PRECISION AS lat,
        ROUND(start_longitude::NUMERIC, 3)::DOUBLE PRECISION AS lon,
        'origin'::TEXT AS point_type
    FROM trip_plans
    UNION ALL
    SELECT
        ROUND(end_latitude::NUMERIC, 3)::DOUBLE PRECISION AS lat,
        ROUND(end_longitude::NUMERIC, 3)::DOUBLE PRECISION AS lon,
        'destination'::TEXT AS point_type
    FROM trip_plans
) AS points
GROUP BY lat, lon, point_type;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_search_heatmap_unique
    ON mv_search_heatmap(lat, lon, point_type);

CREATE INDEX IF NOT EXISTS idx_mv_search_heatmap_type
    ON mv_search_heatmap(point_type);


-- 3. Popular origin-destination pairs (~1.1km clustering via 2 decimal places)
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_popular_od_pairs AS
SELECT
    ROUND(start_latitude::NUMERIC, 2)::DOUBLE PRECISION AS from_lat,
    ROUND(start_longitude::NUMERIC, 2)::DOUBLE PRECISION AS from_lon,
    ROUND(end_latitude::NUMERIC, 2)::DOUBLE PRECISION AS to_lat,
    ROUND(end_longitude::NUMERIC, 2)::DOUBLE PRECISION AS to_lon,
    COUNT(*)::INTEGER AS trip_count,
    COALESCE(AVG(
        (SELECT COUNT(*) FROM trip_options topt WHERE topt.trip_plan_id = tp.id)
    ), 0)::DOUBLE PRECISION AS avg_options,
    NOW() AS last_refreshed
FROM trip_plans tp
GROUP BY
    ROUND(start_latitude::NUMERIC, 2),
    ROUND(start_longitude::NUMERIC, 2),
    ROUND(end_latitude::NUMERIC, 2),
    ROUND(end_longitude::NUMERIC, 2)
HAVING COUNT(*) >= 2;

CREATE UNIQUE INDEX IF NOT EXISTS idx_mv_popular_od_pairs_unique
    ON mv_popular_od_pairs(from_lat, from_lon, to_lat, to_lon);

CREATE INDEX IF NOT EXISTS idx_mv_popular_od_pairs_count
    ON mv_popular_od_pairs(trip_count DESC);


-- Comments
COMMENT ON MATERIALIZED VIEW mv_search_hourly_stats IS
    'Routing analytics: hourly aggregation of trip plan searches. Refresh every 10 minutes.';

COMMENT ON MATERIALIZED VIEW mv_search_heatmap IS
    'Routing analytics: heatmap of search origins and destinations (~111m grid). Refresh every 30 minutes.';

COMMENT ON MATERIALIZED VIEW mv_popular_od_pairs IS
    'Routing analytics: popular origin-destination pairs (~1.1km clustering). Refresh every 30 minutes.';


-- Update refresh_all_materialized_views() to include new views
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

    RAISE NOTICE 'Refreshing mv_search_hourly_stats...';
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_search_hourly_stats;

    RAISE NOTICE 'Refreshing mv_search_heatmap...';
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_search_heatmap;

    RAISE NOTICE 'Refreshing mv_popular_od_pairs...';
    REFRESH MATERIALIZED VIEW CONCURRENTLY mv_popular_od_pairs;

    RAISE NOTICE 'All materialized views refreshed successfully';
END;
$$ LANGUAGE plpgsql;
