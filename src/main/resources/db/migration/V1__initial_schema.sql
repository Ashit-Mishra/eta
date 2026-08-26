CREATE TABLE stations (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE routes (
    id BIGSERIAL PRIMARY KEY,
    route_code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE route_stations (
    id BIGSERIAL PRIMARY KEY,
    route_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    sequence_number INTEGER NOT NULL,

    CONSTRAINT fk_route_stations_route
        FOREIGN KEY (route_id)
        REFERENCES routes(id),

    CONSTRAINT fk_route_stations_station
        FOREIGN KEY (station_id)
        REFERENCES stations(id),

    CONSTRAINT uk_route_station_sequence
        UNIQUE (route_id, sequence_number)
);

CREATE TABLE sections (
    id BIGSERIAL PRIMARY KEY,
    section_code VARCHAR(50) NOT NULL UNIQUE,
    from_station_id BIGINT NOT NULL,
    to_station_id BIGINT NOT NULL,
    distance_km DOUBLE PRECISION,

    CONSTRAINT fk_section_from_station
        FOREIGN KEY (from_station_id)
        REFERENCES stations(id),

    CONSTRAINT fk_section_to_station
        FOREIGN KEY (to_station_id)
        REFERENCES stations(id)
);

CREATE TABLE trains (
    id BIGSERIAL PRIMARY KEY,
    train_no VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    route_id BIGINT NOT NULL,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_train_route
        FOREIGN KEY (route_id)
        REFERENCES routes(id)
);