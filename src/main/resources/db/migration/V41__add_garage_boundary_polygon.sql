ALTER TABLE garages
ADD COLUMN IF NOT EXISTS boundary GEOGRAPHY(POLYGON, 4326);

CREATE INDEX IF NOT EXISTS idx_garages_boundary ON garages USING GIST (boundary);

COMMENT ON COLUMN garages.boundary IS 'Polygon boundary for precise geofencing. When set, takes precedence over radius_meters for containment checks. Stored as WGS84 geography for accurate distance calculations.';

UPDATE garages
SET boundary = ST_Buffer(
    ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography,
    radius_meters
)::geography
WHERE boundary IS NULL AND latitude IS NOT NULL AND longitude IS NOT NULL;
