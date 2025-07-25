CREATE TABLE trip_plans (
                            id VARCHAR(36) PRIMARY KEY,
                            start_latitude DOUBLE PRECISION NOT NULL,
                            start_longitude DOUBLE PRECISION NOT NULL,
                            end_latitude DOUBLE PRECISION NOT NULL,
                            end_longitude DOUBLE PRECISION NOT NULL,
                            max_transfers INTEGER DEFAULT 2,
                            max_walking_distance_meters INTEGER DEFAULT 800,
                            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE trip_options (
                              id VARCHAR(36) PRIMARY KEY,
                              trip_plan_id VARCHAR(36) NOT NULL,
                              total_duration_minutes INTEGER NOT NULL,
                              total_walking_time_minutes INTEGER NOT NULL,
                              total_waiting_time_minutes INTEGER NOT NULL,
                              total_travel_time_minutes INTEGER NOT NULL,
                              number_of_transfers INTEGER NOT NULL DEFAULT 0,
                              created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

                              CONSTRAINT fk_trip_options_plan FOREIGN KEY (trip_plan_id) REFERENCES trip_plans(id) ON DELETE CASCADE
);

CREATE TABLE trip_segments (
                               id VARCHAR(36) PRIMARY KEY,
                               trip_option_id VARCHAR(36) NOT NULL,
                               segment_order INTEGER NOT NULL,
                               segment_type VARCHAR(20) NOT NULL, -- 'WALKING', 'BUS_RIDE', 'WAITING'
                               route_id VARCHAR(36),
                               start_stop_id VARCHAR(36),
                               end_stop_id VARCHAR(36),
                               duration_minutes INTEGER NOT NULL,
                               created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

                               CONSTRAINT fk_trip_segments_option FOREIGN KEY (trip_option_id) REFERENCES trip_options(id) ON DELETE CASCADE,
                               CONSTRAINT fk_trip_segments_route FOREIGN KEY (route_id) REFERENCES bus_routes(id),
                               CONSTRAINT fk_trip_segments_start_stop FOREIGN KEY (start_stop_id) REFERENCES bus_stops(id),
                               CONSTRAINT fk_trip_segments_end_stop FOREIGN KEY (end_stop_id) REFERENCES bus_stops(id)
);

-- Индексы для trip planning
CREATE INDEX idx_trip_plans_coordinates ON trip_plans(start_latitude, start_longitude, end_latitude, end_longitude);
CREATE INDEX idx_trip_options_plan ON trip_options(trip_plan_id);
CREATE INDEX idx_trip_segments_option ON trip_segments(trip_option_id, segment_order);