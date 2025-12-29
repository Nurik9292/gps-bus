CREATE INDEX IF NOT EXISTS idx_vehicles_route_active ON vehicles(assigned_route_id, is_active);
CREATE INDEX IF NOT EXISTS idx_route_stops_composite ON route_stops(route_id, direction, stop_sequence, stop_id);
CREATE INDEX IF NOT EXISTS idx_bus_stops_name_gin ON bus_stops USING gin(to_tsvector('simple', stop_name));

CREATE INDEX IF NOT EXISTS idx_vehicles_active_with_position ON vehicles(last_position_update)
    WHERE is_active = true AND current_latitude IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_banners_active_sorted ON banners(display_order)
    WHERE is_active = true;
