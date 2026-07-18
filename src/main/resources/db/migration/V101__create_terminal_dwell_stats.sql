CREATE TABLE IF NOT EXISTS terminal_dwell_stats (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    route_number          VARCHAR(32)  NOT NULL,
    direction             INTEGER      NOT NULL,
    hour_of_day           SMALLINT     NOT NULL,
    is_weekend            BOOLEAN      NOT NULL,
    avg_dwell_seconds     DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    sample_count          BIGINT       NOT NULL DEFAULT 0,
    last_observed_at      TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT terminal_dwell_stats_direction_chk CHECK (direction IN (0, 1)),
    CONSTRAINT terminal_dwell_stats_hour_chk CHECK (hour_of_day >= 0 AND hour_of_day <= 23),
    CONSTRAINT terminal_dwell_stats_unique UNIQUE
        (route_number, direction, hour_of_day, is_weekend)
);

CREATE INDEX IF NOT EXISTS idx_terminal_dwell_stats_route_dir
    ON terminal_dwell_stats (route_number, direction);

COMMENT ON TABLE terminal_dwell_stats IS
    'Rolling average terminal turnaround dwell (seconds) per (route, direction, hourOfDay, isWeekend): time from arriving at the final stop of a direction until the trip boundary (departure onto the opposite direction). Feeds departure forecasts for terminal stops.';
