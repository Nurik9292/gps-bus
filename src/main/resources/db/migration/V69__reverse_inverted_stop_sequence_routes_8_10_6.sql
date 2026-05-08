DO $$
DECLARE
    target RECORD;
    rid VARCHAR(36);
    own_geom geometry;
    first_pt geometry;
    last_pt geometry;
    d_start_first DOUBLE PRECISION;
    d_start_last  DOUBLE PRECISION;
    min_seq INTEGER;
    max_seq INTEGER;
BEGIN
    FOR target IN
        SELECT * FROM (VALUES
            ('8',  0),
            ('8',  1),
            ('10', 0),
            ('10', 1),
            ('6',  1)
        ) AS t(route_number, dir)
    LOOP
        SELECT id INTO rid FROM bus_routes WHERE route_number = target.route_number;
        IF rid IS NULL THEN
            RAISE NOTICE 'V69: route % not found, skipping', target.route_number;
            CONTINUE;
        END IF;

        SELECT CASE target.dir WHEN 0 THEN geometry_forward ELSE geometry_backward END
          INTO own_geom
          FROM bus_routes WHERE id = rid;
        IF own_geom IS NULL THEN
            RAISE NOTICE 'V69: route % dir=% has no geometry, skipping', target.route_number, target.dir;
            CONTINUE;
        END IF;

        SELECT ST_SetSRID(ST_MakePoint(bs.longitude, bs.latitude), 4326) INTO first_pt
          FROM route_stops rs JOIN bus_stops bs ON bs.id = rs.stop_id
          WHERE rs.route_id = rid AND rs.direction = target.dir
          ORDER BY rs.stop_sequence ASC LIMIT 1;

        SELECT ST_SetSRID(ST_MakePoint(bs.longitude, bs.latitude), 4326) INTO last_pt
          FROM route_stops rs JOIN bus_stops bs ON bs.id = rs.stop_id
          WHERE rs.route_id = rid AND rs.direction = target.dir
          ORDER BY rs.stop_sequence DESC LIMIT 1;

        IF first_pt IS NULL OR last_pt IS NULL THEN
            RAISE NOTICE 'V69: route % dir=% has fewer than 2 stops, skipping', target.route_number, target.dir;
            CONTINUE;
        END IF;

        d_start_first := ST_Distance(ST_StartPoint(own_geom)::geography, first_pt::geography);
        d_start_last  := ST_Distance(ST_StartPoint(own_geom)::geography, last_pt::geography);

        IF d_start_last < d_start_first THEN
            RAISE NOTICE 'V69: reversing stop_sequence for route % dir=% (start->first_seq=%m, start->last_seq=%m)',
                target.route_number, target.dir, ROUND(d_start_first), ROUND(d_start_last);

            SELECT MIN(stop_sequence), MAX(stop_sequence)
              INTO min_seq, max_seq
              FROM route_stops
              WHERE route_id = rid AND direction = target.dir;

            UPDATE route_stops
               SET stop_sequence = (max_seq + min_seq) - stop_sequence
             WHERE route_id = rid AND direction = target.dir;
        ELSE
            RAISE NOTICE 'V69: SKIP route % dir=% (already aligned: start->first_seq=%m, start->last_seq=%m)',
                target.route_number, target.dir, ROUND(d_start_first), ROUND(d_start_last);
        END IF;
    END LOOP;
END $$;
