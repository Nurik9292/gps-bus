ALTER TABLE segment_travel_stats ADD COLUMN IF NOT EXISTS route_id VARCHAR(100);
ALTER TABLE stop_dwell_stats ADD COLUMN IF NOT EXISTS route_id VARCHAR(100);
ALTER TABLE terminal_dwell_stats ADD COLUMN IF NOT EXISTS route_id VARCHAR(100);

UPDATE segment_travel_stats s SET route_id = br.id
FROM bus_routes br
WHERE s.route_id IS NULL AND br.route_number = s.route_number AND br.city_id = 'city-001';

UPDATE stop_dwell_stats s SET route_id = br.id
FROM bus_routes br
WHERE s.route_id IS NULL AND br.route_number = s.route_number AND br.city_id = 'city-001';

UPDATE terminal_dwell_stats s SET route_id = br.id
FROM bus_routes br
WHERE s.route_id IS NULL AND br.route_number = s.route_number AND br.city_id = 'city-001';

UPDATE segment_travel_stats s SET route_id = only_match.id
FROM (SELECT route_number, MIN(id) AS id FROM bus_routes
      GROUP BY route_number HAVING COUNT(*) = 1) only_match
WHERE s.route_id IS NULL AND only_match.route_number = s.route_number;

UPDATE stop_dwell_stats s SET route_id = only_match.id
FROM (SELECT route_number, MIN(id) AS id FROM bus_routes
      GROUP BY route_number HAVING COUNT(*) = 1) only_match
WHERE s.route_id IS NULL AND only_match.route_number = s.route_number;

UPDATE terminal_dwell_stats s SET route_id = only_match.id
FROM (SELECT route_number, MIN(id) AS id FROM bus_routes
      GROUP BY route_number HAVING COUNT(*) = 1) only_match
WHERE s.route_id IS NULL AND only_match.route_number = s.route_number;

DELETE FROM segment_travel_stats WHERE route_id IS NULL;
DELETE FROM stop_dwell_stats WHERE route_id IS NULL;
DELETE FROM terminal_dwell_stats WHERE route_id IS NULL;

ALTER TABLE segment_travel_stats ALTER COLUMN route_id SET NOT NULL;
ALTER TABLE stop_dwell_stats ALTER COLUMN route_id SET NOT NULL;
ALTER TABLE terminal_dwell_stats ALTER COLUMN route_id SET NOT NULL;

ALTER TABLE segment_travel_stats DROP CONSTRAINT IF EXISTS segment_travel_stats_unique;
ALTER TABLE stop_dwell_stats DROP CONSTRAINT IF EXISTS stop_dwell_stats_unique;
ALTER TABLE terminal_dwell_stats DROP CONSTRAINT IF EXISTS terminal_dwell_stats_unique;

CREATE UNIQUE INDEX IF NOT EXISTS segment_travel_stats_route_id_unique
    ON segment_travel_stats (route_id, direction, from_stop_id, to_stop_id, hour_of_day, is_weekend);

CREATE UNIQUE INDEX IF NOT EXISTS stop_dwell_stats_route_id_unique
    ON stop_dwell_stats (stop_id, route_id, direction);

CREATE UNIQUE INDEX IF NOT EXISTS terminal_dwell_stats_route_id_unique
    ON terminal_dwell_stats (route_id, direction, hour_of_day, is_weekend);

CREATE INDEX IF NOT EXISTS idx_segment_travel_stats_route_id
    ON segment_travel_stats (route_id, direction);

CREATE INDEX IF NOT EXISTS idx_terminal_dwell_stats_route_id
    ON terminal_dwell_stats (route_id, direction);

COMMENT ON COLUMN segment_travel_stats.route_id IS
    'Canonical route key (bus_routes.id). route_number is display-only: same numbers exist in different cities.';
COMMENT ON COLUMN stop_dwell_stats.route_id IS
    'Canonical route key (bus_routes.id). route_number is display-only: same numbers exist in different cities.';
COMMENT ON COLUMN terminal_dwell_stats.route_id IS
    'Canonical route key (bus_routes.id). route_number is display-only: same numbers exist in different cities.';
