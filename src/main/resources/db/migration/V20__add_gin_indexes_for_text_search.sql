CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_bus_stops_stop_name_gin_trgm
    ON bus_stops USING gin (stop_name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_bus_stops_name_en_gin_trgm
    ON bus_stops USING gin (name_en gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_bus_stops_name_tm_gin_trgm
    ON bus_stops USING gin (name_tm gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_bus_routes_route_number_gin_trgm
    ON bus_routes USING gin (route_number gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_bus_routes_route_name_gin_trgm
    ON bus_routes USING gin (route_name gin_trgm_ops);

COMMENT ON INDEX idx_bus_stops_stop_name_gin_trgm IS
    'GIN trigram index for fast ILIKE searches on stop_name. Supports pattern matching with wildcards.';

COMMENT ON INDEX idx_bus_stops_name_en_gin_trgm IS
    'GIN trigram index for fast ILIKE searches on English stop names.';

COMMENT ON INDEX idx_bus_stops_name_tm_gin_trgm IS
    'GIN trigram index for fast ILIKE searches on Turkmen stop names.';

COMMENT ON INDEX idx_bus_routes_route_number_gin_trgm IS
    'GIN trigram index for fast ILIKE searches on route numbers.';

COMMENT ON INDEX idx_bus_routes_route_name_gin_trgm IS
    'GIN trigram index for fast ILIKE searches on route names.';
