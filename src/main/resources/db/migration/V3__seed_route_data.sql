INSERT INTO route_stations (route_id, station_id, sequence_number)
SELECT
    r.id,
    s.id,
    1
FROM routes r
JOIN stations s ON s.code = 'NDLS'
WHERE r.route_code = 'DEL-LKO';

INSERT INTO route_stations (route_id, station_id, sequence_number)
SELECT
    r.id,
    s.id,
    2
FROM routes r
JOIN stations s ON s.code = 'GZB'
WHERE r.route_code = 'DEL-LKO';

INSERT INTO route_stations (route_id, station_id, sequence_number)
SELECT
    r.id,
    s.id,
    3
FROM routes r
JOIN stations s ON s.code = 'CNB'
WHERE r.route_code = 'DEL-LKO';

INSERT INTO route_stations (route_id, station_id, sequence_number)
SELECT
    r.id,
    s.id,
    4
FROM routes r
JOIN stations s ON s.code = 'LKO'
WHERE r.route_code = 'DEL-LKO';