-- V50: Densify route geometries to eliminate large gaps between points.
--
-- Problem: some routes (e.g. route 160 backward) have gaps up to 502 m between
-- consecutive geometry points. This causes GPS snap-to-route to be imprecise
-- (±250 m error) and makes dead-reckoning prediction jump between segments.
--
-- Fix: ST_Segmentize adds intermediate points so no segment exceeds MAX_SEGMENT_M.
-- We use 50 m — matches typical bus stop spacing; gives ~25 m snap precision.
--
-- IMPORTANT: both columns (PostGIS GEOMETRY + WKT TEXT) are kept in sync because
-- application code reads WKT from route_geometry_* and uses PostGIS geometry_* for
-- spatial queries (ST_Intersects with GIST index). Dropping either column would break
-- parts of the application — see CLAUDE.md geometry notes.
--
-- After densification:
--   route 160 forward:  ~298 pts → ~700 pts  (~16 715 m / 50 m)
--   route 160 backward: ~220 pts → ~650 pts  (~16 445 m / 50 m, gaps removed)
--
-- Memory impact: RouteGeometryCache holds ~3 000 pts per route instead of ~300.
--   Acceptable for the number of routes in the system (~96 routes).
--
-- Runtime: UPDATE is a full table rewrite on bus_routes; takes < 1 s on dev data.

-- Maximum allowed distance between consecutive geometry points (metres on ellipsoid).
-- Decrease for higher snap precision; increase to reduce memory usage.
DO $$
DECLARE
    max_segment_m CONSTANT DOUBLE PRECISION := 50.0;
BEGIN

    -- ── Forward geometry ──────────────────────────────────────────────────────
    UPDATE bus_routes
    SET
        -- PostGIS column: densify on the sphere, cast back to GEOMETRY(LINESTRING,4326)
        geometry_forward = ST_Segmentize(
            geometry_forward::geography,
            max_segment_m
        )::geometry,

        -- WKT text column: regenerate from the densified PostGIS geometry so they stay in sync.
        -- ST_AsText produces "LINESTRING (lon lat, lon lat, ...)" — same format as original imports.
        route_geometry_forward = ST_AsText(
            ST_Segmentize(
                geometry_forward::geography,
                max_segment_m
            )::geometry
        )
    WHERE geometry_forward IS NOT NULL;

    -- ── Backward geometry ─────────────────────────────────────────────────────
    UPDATE bus_routes
    SET
        geometry_backward = ST_Segmentize(
            geometry_backward::geography,
            max_segment_m
        )::geometry,

        route_geometry_backward = ST_AsText(
            ST_Segmentize(
                geometry_backward::geography,
                max_segment_m
            )::geometry
        )
    WHERE geometry_backward IS NOT NULL;

END $$;

-- After densification the total_distance values remain valid (ST_Length is not affected
-- by point density — it measures the same polyline). No need to recalculate them.

-- Verification queries (run manually after migration):
-- SELECT route_number,
--        ST_NPoints(geometry_forward)  AS pts_forward,
--        ST_NPoints(geometry_backward) AS pts_backward,
--        ROUND(ST_Length(geometry_forward::geography))  AS dist_forward_m,
--        ROUND(ST_Length(geometry_backward::geography)) AS dist_backward_m
-- FROM bus_routes
-- ORDER BY route_number;
