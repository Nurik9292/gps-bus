UPDATE bus_routes
   SET is_active = false
 WHERE route_number IN ('67', '68', '55')
   AND is_active = true;

UPDATE route_stops
   SET stop_id = 'stop-legacy-963'
 WHERE stop_id = 'e54077fb-d350-4aac-8977-dbc9e7086757'
   AND NOT EXISTS (
       SELECT 1 FROM route_stops rs2
        WHERE rs2.route_id = route_stops.route_id
          AND rs2.direction = route_stops.direction
          AND rs2.stop_id = 'stop-legacy-963'
   );

DELETE FROM route_stops
 WHERE stop_id = 'e54077fb-d350-4aac-8977-dbc9e7086757';

UPDATE bus_stops
   SET is_active = false
 WHERE id IN ('e54077fb-d350-4aac-8977-dbc9e7086757', 'stop-legacy-312')
   AND is_active = true;

DELETE FROM stop_times
 WHERE trip_id IN (
     SELECT t.id FROM trips t
       JOIN bus_routes br ON br.id = t.route_id
      WHERE br.is_active = false
 );

DELETE FROM trips
 WHERE route_id IN (
     SELECT id FROM bus_routes WHERE is_active = false
 );

DELETE FROM stop_transfers
 WHERE from_stop_id IN (
     SELECT id FROM bus_stops WHERE is_active = false
 )
    OR to_stop_id IN (
     SELECT id FROM bus_stops WHERE is_active = false
 );
