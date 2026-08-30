ALTER TABLE route_stations
    ADD COLUMN arrival_time TIME,
    ADD COLUMN departure_time TIME,
    ADD COLUMN day INTEGER;