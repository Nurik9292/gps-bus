-- V51: Auto-densify route geometry on INSERT/UPDATE.
--
-- Without this trigger, newly created or updated routes may have large gaps
-- between geometry points (up to 500m+), causing imprecise GPS snap-to-route
-- and jumpy prediction. This trigger ensures all geometry is densified to
-- max 50m segments automatically, keeping both PostGIS and WKT columns in sync.

CREATE OR REPLACE FUNCTION densify_route_geometry()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.geometry_forward IS NOT NULL THEN
        NEW.geometry_forward := ST_Segmentize(NEW.geometry_forward::geography, 50)::geometry;
        NEW.route_geometry_forward := ST_AsText(NEW.geometry_forward);
    END IF;

    IF NEW.geometry_backward IS NOT NULL THEN
        NEW.geometry_backward := ST_Segmentize(NEW.geometry_backward::geography, 50)::geometry;
        NEW.route_geometry_backward := ST_AsText(NEW.geometry_backward);
    END IF;

    -- Recalculate total distance if it is missing or zero.
    -- ST_Length on geography returns metres on the ellipsoid.
    IF NEW.geometry_forward IS NOT NULL
        AND (NEW.total_distance_forward_meters IS NULL OR NEW.total_distance_forward_meters = 0) THEN
        NEW.total_distance_forward_meters := ROUND(ST_Length(NEW.geometry_forward::geography))::INTEGER;
    END IF;

    IF NEW.geometry_backward IS NOT NULL
        AND (NEW.total_distance_backward_meters IS NULL OR NEW.total_distance_backward_meters = 0) THEN
        NEW.total_distance_backward_meters := ROUND(ST_Length(NEW.geometry_backward::geography))::INTEGER;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_densify_route_geometry
    BEFORE INSERT OR UPDATE ON bus_routes
    FOR EACH ROW EXECUTE FUNCTION densify_route_geometry();

COMMENT ON FUNCTION densify_route_geometry() IS
    'Densifies route geometry to max 50m segments on INSERT/UPDATE. '
    'Keeps geometry_* (PostGIS) and route_geometry_* (WKT) in sync. '
    'Also fills total_distance_*_meters if missing.';
