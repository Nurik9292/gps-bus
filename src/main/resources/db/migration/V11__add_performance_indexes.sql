CREATE INDEX CONCURRENTLY idx_vehicles_route_active ON vehicles(assigned_route_id, is_active);
CREATE INDEX CONCURRENTLY idx_route_stops_composite ON route_stops(route_id, direction, stop_sequence, stop_id);
CREATE INDEX CONCURRENTLY idx_bus_stops_name_gin ON bus_stops USING gin(to_tsvector('simple', stop_name));

CREATE INDEX CONCURRENTLY idx_vehicles_active_with_position ON vehicles(last_position_update)
    WHERE is_active = true AND current_latitude IS NOT NULL;

CREATE INDEX CONCURRENTLY idx_banners_active_sorted ON banners(display_order)
    WHERE is_active = true;


COMMENT ON TABLE vehicles IS 'Автобусы с GPS трекингом и назначенными маршрутами';
COMMENT ON TABLE bus_routes IS 'Автобусные маршруты города';
COMMENT ON TABLE bus_stops IS 'Автобусные остановки с геокоординатами';
COMMENT ON TABLE route_stops IS 'Связь маршрутов с остановками и их порядок следования';
COMMENT ON TABLE route_schedules IS 'Расписание отправления автобусов по маршрутам';
COMMENT ON TABLE trip_plans IS 'Планы поездок пользователей';
COMMENT ON TABLE trip_options IS 'Варианты поездки для каждого плана';
COMMENT ON TABLE trip_segments IS 'Сегменты поездки (ходьба, ожидание, поездка на автобусе)';

COMMENT ON COLUMN vehicles.device_id IS 'ID GPS устройства из внешнего API';
COMMENT ON COLUMN vehicles.assigned_route_id IS 'Текущий назначенный маршрут автобуса';
COMMENT ON COLUMN vehicles.last_position_update IS 'Время последнего обновления GPS позиции';
COMMENT ON COLUMN route_stops.stop_sequence IS 'Порядковый номер остановки в маршруте';
COMMENT ON COLUMN route_stops.direction IS '0 = прямое направление, 1 = обратное направление';