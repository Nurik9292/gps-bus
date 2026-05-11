CREATE TABLE trips (
    id              VARCHAR(36) PRIMARY KEY,
    route_id        VARCHAR(36) NOT NULL REFERENCES bus_routes(id) ON DELETE CASCADE,
    direction       SMALLINT    NOT NULL CHECK (direction IN (0, 1)),
    service_id      VARCHAR(36) NOT NULL,
    headway_seconds INT,
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT trips_time_window CHECK (end_time > start_time)
);
CREATE INDEX idx_trips_route_dir ON trips(route_id, direction) WHERE is_active;
CREATE INDEX idx_trips_service   ON trips(service_id)         WHERE is_active;

CREATE TABLE stop_times (
    trip_id              VARCHAR(36) NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    stop_sequence        INT NOT NULL,
    stop_id              VARCHAR(36) NOT NULL REFERENCES bus_stops(id) ON DELETE CASCADE,
    arrival_offset_sec   INT NOT NULL,
    departure_offset_sec INT NOT NULL,
    PRIMARY KEY (trip_id, stop_sequence),
    CONSTRAINT stop_times_dwell CHECK (departure_offset_sec >= arrival_offset_sec)
);
CREATE INDEX idx_stop_times_stop     ON stop_times(stop_id);
CREATE INDEX idx_stop_times_trip_seq ON stop_times(trip_id, stop_sequence);

CREATE TABLE stop_transfers (
    from_stop_id    VARCHAR(36) NOT NULL REFERENCES bus_stops(id) ON DELETE CASCADE,
    to_stop_id      VARCHAR(36) NOT NULL REFERENCES bus_stops(id) ON DELETE CASCADE,
    walking_seconds INT NOT NULL CHECK (walking_seconds > 0),
    distance_meters INT NOT NULL CHECK (distance_meters > 0),
    transfer_type   SMALLINT NOT NULL DEFAULT 0,
    PRIMARY KEY (from_stop_id, to_stop_id),
    CONSTRAINT stop_transfers_distinct CHECK (from_stop_id <> to_stop_id)
);
CREATE INDEX idx_stop_transfers_from ON stop_transfers(from_stop_id);
