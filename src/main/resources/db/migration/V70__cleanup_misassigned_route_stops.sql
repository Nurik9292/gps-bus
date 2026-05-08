DELETE FROM route_stops
 WHERE id IN (
    'b8e62b08-35e6-486a-988e-616a356a1c9e',
    '367858a5-90f8-42f9-9582-0cf85cccfa5e'
 );

DELETE FROM route_stops rs
 USING bus_routes br
 WHERE rs.route_id = br.id
   AND br.route_number = '3'
   AND rs.direction = 0
   AND rs.stop_sequence BETWEEN 15 AND 38;

DELETE FROM route_stops
 WHERE id IN (
    '7298deb4-9807-4e53-95bb-7d4b1bf04a8f',
    '28f7c9ff-f016-4c31-b3f5-6de43260128f',
    '0a8b5f69-c42d-4afc-ba5c-6719799d87bb',
    '186c7377-ca46-4b78-a2c4-1570a193e8d2',
    '174274ef-afcd-4449-85bd-db45fc7eb25d',
    'dd907f04-bcd4-4c09-bf1d-2b983ddf7f9a'
 );
