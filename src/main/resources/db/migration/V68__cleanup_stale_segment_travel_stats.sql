DELETE FROM segment_travel_stats sts
WHERE NOT EXISTS (
    SELECT 1
    FROM bus_routes br
    JOIN route_stops rs1
        ON rs1.route_id = br.id
       AND rs1.direction = sts.direction
       AND rs1.stop_id = sts.from_stop_id
    JOIN route_stops rs2
        ON rs2.route_id = br.id
       AND rs2.direction = sts.direction
       AND rs2.stop_id = sts.to_stop_id
       AND rs2.stop_sequence = rs1.stop_sequence + 1
    WHERE br.route_number = sts.route_number
);

DELETE FROM segment_travel_stats
WHERE route_number IN ('1A', '2A', '3A', '4A');
