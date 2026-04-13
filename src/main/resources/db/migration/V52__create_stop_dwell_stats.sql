-- Historical dwell-time statistics per bus stop.
-- Collected by DwellTimeCollectorService, consumed by VehiclePositionPredictionService
-- for accurate prediction pause duration at stops and ETA calculations.
--
-- Statistics are keyed by (stop_id, route_number, direction) because the same physical stop
-- can have different dwell times depending on which route is serving it and which direction.

CREATE TABLE IF NOT EXISTS stop_dwell_stats (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stop_id             VARCHAR(100) NOT NULL,
    route_number        VARCHAR(32)  NOT NULL,
    direction           INTEGER      NOT NULL,
    avg_dwell_seconds   DOUBLE PRECISION NOT NULL DEFAULT 15.0,
    min_dwell_seconds   DOUBLE PRECISION,
    max_dwell_seconds   DOUBLE PRECISION,
    sample_count        BIGINT       NOT NULL DEFAULT 0,
    last_dwell_at       TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT stop_dwell_stats_direction_chk CHECK (direction IN (0, 1)),
    CONSTRAINT stop_dwell_stats_unique UNIQUE (stop_id, route_number, direction)
);

CREATE INDEX IF NOT EXISTS idx_stop_dwell_stats_stop_route
    ON stop_dwell_stats (stop_id, route_number, direction);

CREATE INDEX IF NOT EXISTS idx_stop_dwell_stats_last_dwell
    ON stop_dwell_stats (last_dwell_at DESC);

COMMENT ON TABLE stop_dwell_stats IS
    'Rolling average dwell time (seconds) at bus stops, per (stop, route, direction). Used by prediction engine to pause marker advance during typical dwell periods.';
COMMENT ON COLUMN stop_dwell_stats.avg_dwell_seconds IS
    'Incremental rolling average of observed dwell durations. Starts at 15s default.';
COMMENT ON COLUMN stop_dwell_stats.sample_count IS
    'Number of dwell observations used to compute avg_dwell_seconds.';
