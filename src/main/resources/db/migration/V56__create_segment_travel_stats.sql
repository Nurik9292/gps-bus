CREATE TABLE IF NOT EXISTS segment_travel_stats (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    route_number          VARCHAR(32)  NOT NULL,
    direction             INTEGER      NOT NULL,
    from_stop_id          VARCHAR(100) NOT NULL,
    to_stop_id            VARCHAR(100) NOT NULL,
    hour_of_day           SMALLINT     NOT NULL,
    is_weekend            BOOLEAN      NOT NULL,
    avg_travel_seconds    DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    sample_count          BIGINT       NOT NULL DEFAULT 0,
    last_observed_at      TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT segment_travel_stats_direction_chk CHECK (direction IN (0, 1)),
    CONSTRAINT segment_travel_stats_hour_chk CHECK (hour_of_day >= 0 AND hour_of_day <= 23),
    CONSTRAINT segment_travel_stats_unique UNIQUE
        (route_number, direction, from_stop_id, to_stop_id, hour_of_day, is_weekend)
);

CREATE INDEX IF NOT EXISTS idx_segment_travel_stats_route_dir
    ON segment_travel_stats (route_number, direction);

CREATE INDEX IF NOT EXISTS idx_segment_travel_stats_last_observed
    ON segment_travel_stats (last_observed_at DESC);

COMMENT ON TABLE segment_travel_stats IS
    'Rolling average travel time (seconds) between consecutive bus stops, per (route, direction, fromStop, toStop, hourOfDay, isWeekend). Used by ETA prediction to replace live-speed-only estimates with historical timing.';
COMMENT ON COLUMN segment_travel_stats.avg_travel_seconds IS
    'Incremental rolling average of observed travel durations. Starts at 0 (fallback to live-speed formula until sample_count >= 3).';
COMMENT ON COLUMN segment_travel_stats.sample_count IS
    'Number of observations used to compute avg_travel_seconds. Threshold for trusting historical timing is 3.';
