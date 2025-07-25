CREATE TABLE route_stops (
                             id VARCHAR(36) PRIMARY KEY,
                             route_id VARCHAR(36) NOT NULL,
                             stop_id VARCHAR(36) NOT NULL,
                             stop_sequence INTEGER NOT NULL,
                             direction INTEGER NOT NULL DEFAULT 0,
                             estimated_travel_time_minutes INTEGER,
                             distance_from_start_meters INTEGER,
                             created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

                             CONSTRAINT fk_route_stops_route FOREIGN KEY (route_id) REFERENCES bus_routes(id) ON DELETE CASCADE,
                             CONSTRAINT fk_route_stops_stop FOREIGN KEY (stop_id) REFERENCES bus_stops(id) ON DELETE CASCADE,
                             CONSTRAINT uk_route_stops_sequence UNIQUE (route_id, direction, stop_sequence),
                             CONSTRAINT uk_route_stops_stop_direction UNIQUE (route_id, stop_id, direction)
);

CREATE INDEX idx_route_stops_route ON route_stops(route_id, direction, stop_sequence);
CREATE INDEX idx_route_stops_stop ON route_stops(stop_id);

-- Ограничения для route_stops
ALTER TABLE route_stops ADD CONSTRAINT chk_route_stops_direction
    CHECK (direction IN (0, 1));

ALTER TABLE route_stops ADD CONSTRAINT chk_route_stops_sequence_positive
    CHECK (stop_sequence > 0);

-- Добавляем внешний ключ для vehicles.assigned_route_id
ALTER TABLE vehicles ADD CONSTRAINT fk_vehicles_assigned_route
    FOREIGN KEY (assigned_route_id) REFERENCES bus_routes(id) ON DELETE SET NULL;

-- Таблица расписания
CREATE TABLE route_schedules (
                                 id VARCHAR(36) PRIMARY KEY,
                                 route_id VARCHAR(36) NOT NULL,
                                 direction INTEGER NOT NULL DEFAULT 0,
                                 departure_time TIME NOT NULL,
                                 days_of_week INTEGER[] NOT NULL DEFAULT '{1,2,3,4,5,6,7}',
                                 is_active BOOLEAN DEFAULT true,
                                 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                 CONSTRAINT fk_route_schedules_route FOREIGN KEY (route_id) REFERENCES bus_routes(id) ON DELETE CASCADE
);

-- Индексы для расписания
CREATE INDEX idx_route_schedules_route ON route_schedules(route_id, direction);
CREATE INDEX idx_route_schedules_time ON route_schedules(departure_time);
CREATE INDEX idx_route_schedules_active ON route_schedules(is_active);