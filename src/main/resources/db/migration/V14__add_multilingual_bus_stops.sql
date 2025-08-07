-- Добавляем multilingual поля
ALTER TABLE bus_stops
    ADD COLUMN IF NOT EXISTS name_en VARCHAR(100),
    ADD COLUMN IF NOT EXISTS name_tm VARCHAR(100);

-- Добавляем индексы для поиска
CREATE INDEX IF NOT EXISTS idx_bus_stops_name_en ON bus_stops(name_en) WHERE name_en IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_bus_stops_name_tm ON bus_stops(name_tm) WHERE name_tm IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_bus_stops_multilingual_search ON bus_stops
    USING gin(to_tsvector('simple', coalesce(stop_name, '') || ' ' || coalesce(name_en, '') || ' ' || coalesce(name_tm, '')));


COMMENT ON COLUMN bus_stops.stop_name IS 'Primary stop name (Russian)';
COMMENT ON COLUMN bus_stops.name_en IS 'English translation of stop name';
COMMENT ON COLUMN bus_stops.name_tm IS 'Turkmen translation of stop name';
