INSERT INTO sections (
    section_code,
    from_station_id,
    to_station_id,
    distance_km
)
SELECT
    'NDLS-GZB',
    s1.id,
    s2.id,
    25.0
FROM stations s1
JOIN stations s2 ON s2.code = 'GZB'
WHERE s1.code = 'NDLS';

INSERT INTO sections (
    section_code,
    from_station_id,
    to_station_id,
    distance_km
)
SELECT
    'GZB-CNB',
    s1.id,
    s2.id,
    450.0
FROM stations s1
JOIN stations s2 ON s2.code = 'CNB'
WHERE s1.code = 'GZB';

INSERT INTO sections (
    section_code,
    from_station_id,
    to_station_id,
    distance_km
)
SELECT
    'CNB-LKO',
    s1.id,
    s2.id,
    72.0
FROM stations s1
JOIN stations s2 ON s2.code = 'LKO'
WHERE s1.code = 'CNB';